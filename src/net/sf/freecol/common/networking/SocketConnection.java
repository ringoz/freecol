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

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.PromiseCompat;

public class SocketConnection extends Connection {
  
    private static final Logger logger = Logger.getLogger(Connection.class.getName());

    /** Maximum number of retries before closing the connection. */
    private static final int MAXIMUM_RETRIES = 5;

    static final String NETWORK_REPLY_ID_TAG = "networkReplyId";
    static final String QUESTION_TAG = "question";
    static final String REPLY_TAG = "reply";

    private static final int TIMEOUT = 5000; // 5s

    /** The socket connected to the other end of the connection. */
    private final SocketIO io;

    /** Main message writer. */
    private final FreeColXMLWriter xw;

    /** A map of network ids to the corresponding waiting thread. */
    private final Map<Integer, Consumer<Object>> waitingThreads
        = Collections.synchronizedMap(new HashMap<Integer, Consumer<Object>>());

    /** A counter for reply ids. */
    private int nextNetworkReplyId = 1;

    /**
     * Creates a new {@code Connection} with the specified
     * {@code Socket} and {@link MessageHandler}.
     *
     * @param socket The socket to the client.
     * @param name The connection name.
     * @exception IOException if streams can not be derived from the socket.
     */
    public SocketConnection(SocketIO io, String name) throws IOException {
        super(name);

        this.io = io;
        this.xw = new FreeColXMLWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                if (cbuf[off + len - 1] != '\n')
                    throw new IOException();
                io.writeLineAsync(String.valueOf(cbuf, off, len));
            }

            @Override
            public void flush() throws IOException {
            }

            @Override
            public void close() throws IOException {
            }
        }, FreeColXMLWriter.WriteScope.toSave());
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
    public static CompletableFuture<Connection> open(String host, int port, String name) throws IOException {
        return SocketIO.connect(host, port).thenApply((io) -> {
            try {
                return (Connection)new SocketConnection(io, name);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });//.orTimeout(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    /**
     * Ask this thread to stop work.
     *
     * @param reason A brief description of why the thread should stop.
     */
    private void askToStop(String reason) {
        final var nros = this.waitingThreads.values();
        for (var nro : nros) nro.accept(null);

        logger.info(this.getName() + ": stopped receiving thread: " + reason);
    }

    /**
     * Listens to the InputStream and calls the message handler for
     * each message received.
     * 
     * @exception IOException on low level IO problems.
     * @exception SAXException if a problem occured during parsing.
     * @exception XMLStreamException if a problem occured during parsing.
     */
    private CompletableFuture<Void> listen() {
        return this.io.readLineAsync().thenAccept((line) -> {
            if (line == null) return;

            String tag; Message message; int replyId = -1;
            try (final FreeColXMLReader xr = new FreeColXMLReader(new StringReader(line))) {
                xr.nextTag();
                tag = xr.getLocalName();
                replyId = xr.getAttribute(NETWORK_REPLY_ID_TAG, -1);
                message = this.getMessageHandler().read(this, xr);
            } catch (Exception e) {
                logger.log(Level.WARNING, this.getName() + ": read message fail", e);
                tag = DisconnectMessage.TAG;
                message = TrivialMessage.disconnectMessage;
            }

            if (tag.equals(SocketConnection.REPLY_TAG)) {
                final var nro = this.waitingThreads.remove(replyId);
                if (nro == null) {
                    logger.warning(this.getName() + ": did not find reply " + replyId);
                } else {
                    nro.accept(message);
                }
                return;
            }

            if (tag.equals(SocketConnection.QUESTION_TAG))
                message = ((QuestionMessage)message).getMessage();
            final String subTag = this.getName() + "-" + message.getType();

            Message reply;
            try {
                reply = this.handle(message);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, subTag + ": handler fail", fce);
                return;
            }
    
            final String replyTag = (reply == null) ? "null" : reply.getType();
            if (tag.equals(SocketConnection.QUESTION_TAG))
                reply = new ReplyMessage(replyId, reply);

            this.sendMessage(reply).thenAccept((v) -> {
                logger.log(Level.FINEST, subTag + " -> " + replyTag);
            }).exceptionally((ex) -> {
                logger.log(Level.WARNING, subTag + " -> " + replyTag + " failed", ex);
                return null;
            });
        });
    }

    private int timesFailed = 0;

    /**
     * Start the recieving thread.
     */
    @Override
    public void startReceiving() {
        listen().whenComplete((v, ex) -> {
            if (ex == null) {
                timesFailed = 0;
            }
            else {
                logger.log(Level.WARNING, this.getName() + ": fail", ex);
                if (++timesFailed > MAXIMUM_RETRIES) {
                    askToStop("disconnect");
                    sendDisconnect();
                }
            }
            this.startReceiving();
        });
    }

    /**
     * Is this connection alive?
     *
     * @return True if the connection is alive.
     */
    @Override
    public boolean isAlive() {
        return this.io.isOpen();
    }

    /**
     * Get the host address of this connection.
     *
     * @return The host address, or an empty string on error.
     */
    @Override
    public String getHostAddress() {
        try {
            return this.io.getRemoteAddress().toString();
        }
        catch (Throwable t) {
            return "";
        }
    }

    /**
     * Set the output write scope.
     *
     * @param ws The new write scope.
     */
    @Override
    public void setWriteScope(FreeColXMLWriter.WriteScope ws) {
        this.xw.setWriteScope(ws);
    }

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
    @Override
    public CompletableFuture<Message> askMessage(Message message, long timeout) {
        if (message == null) return CompletableFuture.completedFuture(null);
        final String tag = message.getType();

        final int replyId = this.nextNetworkReplyId++;
        final var nro = PromiseCompat.create((resolve, reject) -> {
            this.waitingThreads.put(replyId, resolve);
        });

        final QuestionMessage qm = new QuestionMessage(replyId, message);
        return sendMessage(qm).thenCompose((v) -> nro/*.orTimeout(timeout, TimeUnit.MILLISECONDS)*/).thenApply((response) -> {
            if (response == null && this.io == null) {
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
    @Override
    public CompletableFuture<Void> sendMessage(Message message) {
        if (message == null) return CompletableFuture.completedFuture(null);
        try {
            message.toXML(this.xw);
            this.xw.writeCharacters(END_OF_STREAM_ARRAY, 0,
                                    END_OF_STREAM_ARRAY.length);
            this.xw.flush();
            logMessage(message, true);
        } catch (XMLStreamException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        askToStop("connection closing");

        // Close the socket before the input stream.  Socket closure will
        // terminate any existing I/O and release the locks.
        try {
            this.io.close();
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Error closing socket", ioe);
        }
        
        logger.fine("Connection closed for " + getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        sb.append("[Connection ").append(getName()).append(" (")
            .append(getHostAddress()).append(")]");
        return sb.toString();
    }
}
