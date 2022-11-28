/**
 *  Copyright (C) 2002-2022   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.networking;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;


/**
 * A network connection.
 * Responsible for both sending and receiving network messages.
 */
public class Connection implements Closeable {

    private static final Logger logger = Logger.getLogger(Connection.class.getName());

    /**
     * The default timeout when waiting for a reply from the server. Defined
     * in milliseconds.
     * 
     * Setting 30s for now since there was no timeout earlier when waiting
     * for å reply.
     */
    public static final long DEFAULT_REPLY_TIMEOUT = 30000;
    
    public static final char END_OF_STREAM = '\n';
    private static final char[] END_OF_STREAM_ARRAY = { END_OF_STREAM };
    public static final int BUFFER_SIZE = 1 << 14;
    
    public static final String NETWORK_REPLY_ID_TAG = "networkReplyId";
    public static final String QUESTION_TAG = "question";
    public static final String REPLY_TAG = "reply";
    public static final String SEND_SUFFIX = "-send";
    public static final String REPLY_SUFFIX = "-reply";

    private static final int TIMEOUT = 5000; // 5s

    /** The name of the connection. */
    private String name;

    /** A lock for access to the socket. */
    private final Object socketLock = new Object();
    /** The socket connected to the other end of the connection. */
    private AsynchronousSocketChannel socket = null;

    /** A lock for the input side. */
    private final Object inputLock = new Object();
    /** The wrapped version of the input side of the socket. */
    private BufferedReader br;
    /** An XML stream wrapping of an input line. */
    private FreeColXMLReader xr;

    /** A lock for the output side. */
    private final Object outputLock = new Object();
    /** Main message writer. */
    private FreeColXMLWriter xw;

    /** The FreeColXMLWriter to write logging messages to. */
    private FreeColXMLWriter lw;

    /** The subthread to read the input. */
    private ReceivingThread receivingThread;

    /** The message handler to process incoming messages with. */
    private MessageHandler messageHandler = null;
    

    /**
     * Trivial constructor.
     *
     * @param name The name of the connection.
     */
    protected Connection(String name) {
        this.name = name;

        setSocket(null);
        this.br = null;
        this.xr = null;
        this.receivingThread = null;
        this.messageHandler = null;
        this.xw = null;

        // Make a (pretty printing) transformer, but only make the log
        // writer in COMMS-debug mode.
        setCommsLogging(FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.COMMS));
    }

    /**
     * Creates a new {@code Connection} with the specified
     * {@code Socket} and {@link MessageHandler}.
     *
     * @param socket The socket to the client.
     * @param name The connection name.
     * @exception IOException if streams can not be derived from the socket.
     */
    public Connection(AsynchronousSocketChannel socket, String name) throws IOException {
        this(name);

        setSocket(socket);
        this.br = new BufferedReader(new InputStreamReader(Channels.newInputStream(socket), StandardCharsets.UTF_8));
        this.receivingThread = new ReceivingThread(this, name);
        this.xw = new FreeColXMLWriter(Channels.newOutputStream(socket),
            FreeColXMLWriter.WriteScope.toSave(), false);
    }

    /**
     * Sets up a new socket with specified host and port and uses
     * {@link #Connection(Socket, String)}.
     *
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @param name The name for the connection.
     * @exception IOException if the socket creation is problematic.
     */
    public Connection(String host, int port, String name) throws IOException {
        this(createSocket(host, port), name);
    }


    /**
     * Creates a socket to communication with a given host, port pair.
     *
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @return A new socket.
     * @exception IOException on failure to create/connect the socket.
     */
    private static AsynchronousSocketChannel createSocket(String host, int port)
        throws IOException {
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        SocketAddress addr = new InetSocketAddress(host, port);
        socket.connect(addr);
        return socket;
    }

    /**
     * Start the recieving thread.
     */
    public void startReceiving() {
        if (this.receivingThread != null) this.receivingThread.start();
    }
        
    /**
     * Get the socket.
     *
     * @return The current {@code Socket}.
     */
    public AsynchronousSocketChannel getSocket() {
        synchronized (this.socketLock) {
            return this.socket;
        }
    }

    /**
     * Set the socket.
     *
     * @param socket The new {@code Socket}.
     */
    private void setSocket(AsynchronousSocketChannel socket) {
        synchronized (this.socketLock) {
            this.socket = socket;
        }
    }

    /**
     * Close and clear the socket.
     */
    private void closeSocket() {
        synchronized (this.socketLock) {
            if (this.socket != null) {
                try {
                    this.socket.close();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "Error closing socket", ioe);
                } finally {
                    this.socket = null;
                }
            }
        }
    }

    /**
     * Close and clear the output stream.
     */
    private void closeOutputStream() {
        synchronized (this.outputLock) {
            if (this.xw != null) {
                this.xw.close();
                this.xw = null;
            }
        }
    }

    /**
     * Close and clear the input stream.
     */
    private void closeInputStream() {
        synchronized (this.inputLock) {
            if (this.br != null) {
                try {
                    this.br.close();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "Error closing buffered input",
                        ioe);
                } finally {
                    this.br = null;
                }
            }
        }
    }

    /**
     * Is this connection alive?
     *
     * @return True if the connection is alive.
     */
    public boolean isAlive() {
        return getSocket() != null;
    }

    /**
     * Get the host address of this connection.
     *
     * @return The host address, or an empty string on error.
     */
    public String getHostAddress() {
        try {
            return ((InetSocketAddress)getSocket().getRemoteAddress()).getHostString();
        }
        catch (Throwable t) {
            return "";
        }
    }

    /**
     * Get the port for this connection.
     *
     * @return The port number, or negative on error.
     */
    public int getPort() {
        try {
            return ((InetSocketAddress)getSocket().getRemoteAddress()).getPort();
        }
        catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Get the printable description of the socket.
     *
     * @return *host-address*:*port-number* or an empty string on error.
     */
    public String getSocketName() {
        return (isAlive()) ? getHostAddress() + ":" + getPort() : "";
    }
    
    /**
     * Gets the message handler for this connection.
     *
     * @return The {@code MessageHandler} for this Connection.
     */
    public synchronized MessageHandler getMessageHandler() {
        return this.messageHandler;
    }

    /**
     * Sets the message handler for this connection.
     *
     * @param messageHandler The new {@code MessageHandler} for this
     *     Connection.
     * @return This {@code Connection}.
     */
    public synchronized Connection setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        return this;
    }

    /**
     * Set the output write scope.
     *
     * @param ws The new write scope.
     */
    public void setWriteScope(FreeColXMLWriter.WriteScope ws) {
        synchronized (this.outputLock) {
            if (this.xw != null) this.xw.setWriteScope(ws);
        }
    }

    /**
     * Gets the connection name.
     *
     * @return The connection name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the logging state of this connection.
     *
     * @param log If true, enable logging.
     * @return This {@code Connection}.
     */
    public final Connection setCommsLogging(boolean log) {
        FreeColXMLWriter lw = null;
        if (log) {
            try {
                lw = new FreeColXMLWriter(FreeColDirectories.getLogCommsWriter(),
                    FreeColXMLWriter.WriteScope.toSave(), true);
            } catch (FreeColException|IOException ex) {
                lw = null; // Just do not log
                logger.log(Level.WARNING, "Comms logs disabled", ex);
            }
        }
        this.lw = lw;
        return this;
    }

    /**
     * Signal that this connection is disconnecting.
     */
    public CompletableFuture<Void> sendDisconnect() {
        return send(TrivialMessage.disconnectMessage).exceptionally((ex) -> {
            logger.log(Level.WARNING, "Failed to send disconnect", ex);
            return null;
        });
    }

    /**
     * Send a reconnect message.
     */
    public CompletableFuture<Void> sendReconnect() {
        return send(TrivialMessage.reconnectMessage).exceptionally((ex) -> {
            logger.log(Level.WARNING, "Failed to send reconnect", ex);
            return null;
        });
    }

    /**
     * Disconnect this connection.
     */
    public CompletableFuture<Void> disconnect() {
        return sendDisconnect().thenAccept((v) -> {
            close();
        });
    }


    // ReceivingThread input support, work in progress

    public FreeColXMLReader getFreeColXMLReader() {
        return this.xr;
    }

    public String startListen() throws XMLStreamException {
        String line;
        try {
            line = this.br.readLine();
        } catch (IOException ioe) {
            line = null;
        }
        if (line == null) return DisconnectMessage.TAG;
        try {
            this.xr = new FreeColXMLReader(new StringReader(line));
        } catch (Exception ex) {
            return DisconnectMessage.TAG;
        }
        this.xr.nextTag();
        return this.xr.getLocalName();
    }

    public int getReplyId() {
        return (this.xr == null) ? -1
            : this.xr.getAttribute(NETWORK_REPLY_ID_TAG, -1);
    }

    public void endListen() {
        this.xr = null;
    }


    // Low level Message routines.  Overridden in DummyConnection
    
    /**
     * Send a message, and return the response.  Log both.
     *
     * @param message The {@code Message} to send.
     * @param timeout A timeout in milliseconds, after which a
     *      {@code TimeoutException} gets thrown when waiting
     *      for a reply.
     * @return The response.
     * @exception FreeColException on extreme confusion.
     * @exception IOException on failure to send.
     * @exception XMLStreamException on stream write error.
     * @exception TimeoutException when the timeout is reached.
     */
    public CompletableFuture<Message> askMessage(Message message, long timeout) {
        if (message == null) return null;
        final String tag = message.getType();

        // Build the question message and establish an NRO for it.
        // *Then* send the message.
        final int replyId = this.receivingThread.getNextNetworkReplyId();
        QuestionMessage qm = new QuestionMessage(replyId, message);
        NetworkReplyObject nro = this.receivingThread.waitForNetworkReply(replyId);

        return sendMessage(qm).thenCompose((v) -> nro.getResponse(timeout)).thenApply((response) -> {
            if (response == null && !this.socket.isOpen()) {
                return null;
            } else if (!(response instanceof ReplyMessage)) {
                throw new CompletionException(new FreeColException("Bad response to " + replyId + "/" + tag
                        + ": " + response));
            }
            ReplyMessage reply = (ReplyMessage) response;
            logMessage(reply, false);
            return reply.getMessage();
        });
    }
    
    /**
     * Send a message, do not consider a response.
     *
     * Public as this is called from ReceivingThread.
     *
     * @param message The {@code Message} to send.
     * @exception FreeColException on extreme confusion.
     * @exception IOException on failure to send.
     * @exception XMLStreamException on stream problem.
     */
    public CompletableFuture<Void> sendMessage(Message message) {
        if (message == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            synchronized (this.outputLock) {
                if (this.xw == null) return;
                try {
                    message.toXML(this.xw);
                    this.xw.writeCharacters(END_OF_STREAM_ARRAY, 0,
                                            END_OF_STREAM_ARRAY.length);
                    this.xw.flush();
                    logMessage(message, true);
                } catch (XMLStreamException e) {
                    throw new CompletionException(e);
                }
            }
        });
    }

    /**
     * Log a message.
     *
     * @param message The {@code Message} to log.
     * @param send True if this is a send, false if a reply.
     */
    protected final void logMessage(Message message, boolean send) {
        if (this.lw == null || message == null) return;
        // Catch *all* failures.  Logging must not crash the game.
        // Only XMLStreamException is visible, but other odd parse
        // errors have been sighted.
        try {
            synchronized (this.lw) {
                this.lw.writeComment(this.name
                    + ((send) ? SEND_SUFFIX : REPLY_SUFFIX));
                message.toXML(this.lw);
                this.lw.writeCharacters(END_OF_STREAM_ARRAY, 0,
                                        END_OF_STREAM_ARRAY.length);
                this.lw.flush();
            }
        } catch (Exception ex) {}
    }
    

    // Quasi-MessageHandler behaviour

    /**
     * Handle a message using the MessageHandler.
     *
     * @param message The {@code Message} to handle.
     * @return The result of the handler.
     * @exception FreeColException if the message is malformed.
     */
    public Message handle(Message message) throws FreeColException {
        if (message == null) return null;
        final MessageHandler mh = getMessageHandler();
        return (mh == null) ? null : mh.handle(this, message);
    }

    /**
     * Read a message using the MessageHandler.
     *
     * @return The {@code Message} found, if any.
     * @exception FreeColException there is a problem creating the message.
     * @exception XMLStreamException if there is a problem reading the stream.
     */
    public Message reader()
        throws FreeColException, XMLStreamException {
        if (this.xr == null) return null;

        MessageHandler mh = getMessageHandler();
        if (mh == null) { // FIXME: Temporary fast fail
            throw new FreeColException("No handler at " + xr.getLocalName())
                .preserveDebug();
        }
        return mh.read(this);
    }


    // Client entry points

    /**
     * Client request.
     *
     * @param message A {@code Message} to process.
     * @exception FreeColException on handler failure.
     * @exception IOException if there is a problem sending messages.
     * @exception XMLStreamException if there is a message format problem.
     */
    public CompletableFuture<Void> request(Message message) {
        if (message == null) return CompletableFuture.completedFuture(null);
        return askMessage(message, DEFAULT_REPLY_TIMEOUT).thenAcceptAsync((Message response) -> {
            try {
                if (response != null) {
                    Message reply = handle(response);
                    assert reply == null;
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, java.awt.EventQueue::invokeLater);
    }
        
    /**
     * Client send.
     *
     * @param message A {@code Message} to send.
     * @exception FreeColException on extreme confusion.
     * @exception IOException on write error.
     * @exception XMLStreamException if there is a message format problem.
     */
    public CompletableFuture<Void> send(Message message) {
        if (message == null) return CompletableFuture.completedFuture(null);
        try {
            sendMessage(message);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    

    // Implement Closeable

    /**
     * Close this connection.
     */
    public void close() {
        if (this.receivingThread != null) {
            this.receivingThread.askToStop("connection closing");
            this.receivingThread.interrupt();
            this.receivingThread = null;
        }

        // Close the socket before the input stream.  Socket closure will
        // terminate any existing I/O and release the locks.
        closeSocket();
        closeInputStream();
        closeOutputStream();
        
        logger.fine("Connection closed for " + this.name);
    }


    // Override Object

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        sb.append("[Connection ").append(this.name).append(" (")
            .append(getSocketName()).append(")]");
        return sb.toString();
    }
}
