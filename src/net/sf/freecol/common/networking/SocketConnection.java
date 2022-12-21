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

import static com.ea.async.Async.await;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.CharsetCompat;

public class SocketConnection extends Connection {
  
    private static final Logger logger = Logger.getLogger(Connection.class.getName());

    private static final int BUFFER_SIZE = 1 << 14;

    static final String NETWORK_REPLY_ID_TAG = "networkReplyId";
    static final String QUESTION_TAG = "question";
    static final String REPLY_TAG = "reply";

    private static final int TIMEOUT = 5000; // 5s

    /** The socket connected to the other end of the connection. */
    private AsynchronousSocketChannel socket = null;

    /** The wrapped version of the input side of the socket. */
    private ByteBuffer br;
    /** An XML stream wrapping of an input line. */
    private FreeColXMLReader xr;

    /** Main message writer. */
    private FreeColXMLWriter xw;

    /** The subthread to read the input. */
    private ReceivingThread receivingThread;

    /**
     * Creates a new {@code Connection} with the specified
     * {@code Socket} and {@link MessageHandler}.
     *
     * @param socket The socket to the client.
     * @param name The connection name.
     * @exception IOException if streams can not be derived from the socket.
     */
    public SocketConnection(AsynchronousSocketChannel socket, String name) throws IOException {
        super(name);

        this.socket = socket;
        this.br = (ByteBuffer)ByteBuffer.allocate(BUFFER_SIZE).flip();
        this.receivingThread = new ReceivingThread(this, name);
        
        this.xw = new FreeColXMLWriter(new Writer() {
            private CompletableFuture<Void> writeBytesAsync(final ByteBuffer buf) {
                final var result = new CompletableFuture<Void>();
                socket.write(buf, buf, new CompletionHandler<Integer,ByteBuffer>() {
                    @Override
                    public void completed(Integer len, ByteBuffer buf) {
                        if (buf.hasRemaining())
                            socket.write(buf, buf, this);
                        else
                            result.complete(null);
                    }
        
                    @Override
                    public void failed(Throwable exc, ByteBuffer buf) {
                        result.completeExceptionally(exc);
                    }
                });
                return result;
            }

            private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
            
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                final ByteBuffer buf = CharsetCompat.encode(StandardCharsets.UTF_8, CharBuffer.wrap(cbuf, off, len));
                synchronized (this) {
                    pending = pending.thenCompose((v) -> writeBytesAsync(buf));
                }
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
        final AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        final SocketAddress addr = new InetSocketAddress(host, port);
        final var result = new CompletableFuture<Connection>();
        socket.connect(addr, result, new CompletionHandler<Void,CompletableFuture<Connection>>() {
            @Override
            public void completed(Void v, CompletableFuture<Connection> result) {
                try {
                    result.complete(new SocketConnection(socket, name));
                } catch (IOException e) {
                    result.completeExceptionally(e);
                }
            }

            @Override
            public void failed(Throwable exc, CompletableFuture<Connection> result) {
                result.completeExceptionally(exc);
            }
        });
        return result.orTimeout(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    // ReceivingThread input support, work in progress

    private CompletableFuture<ByteBuffer> readBytesAsync(final ByteBuffer buf) {
        final var result = new CompletableFuture<ByteBuffer>();
        socket.read(buf, buf, new CompletionHandler<Integer,ByteBuffer>() {
            @Override
            public void completed(Integer len, ByteBuffer buf) {
                result.complete((len != -1) ? (ByteBuffer)buf.flip() : null);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                result.completeExceptionally(exc);
            }
        });
        return result;
    }

    private CompletableFuture<String> readLineAsync() {
        final var all = ByteBuffer.allocate(1 << 20);
        for (ByteBuffer buf = this.br; buf != null; buf = await(readBytesAsync((ByteBuffer)buf.clear()))) {
            while (buf.hasRemaining()) {
                final byte b = buf.get();
                if (b == END_OF_STREAM) {
                    final String line = CharsetCompat.decode(StandardCharsets.UTF_8, (ByteBuffer)all.flip()).toString();
                    return CompletableFuture.completedFuture(line);
                }
                all.put(b);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<String> startListen() {
        return readLineAsync().thenApply((line) -> {
            if (line == null) return DisconnectMessage.TAG;
            try {
                this.xr = new FreeColXMLReader(new StringReader(line));
            } catch (Exception ex) {
                return DisconnectMessage.TAG;
            }
            try {
                this.xr.nextTag();
            } catch (XMLStreamException e) {
                throw new CompletionException(e);
            }
            return this.xr.getLocalName();
        });
    }

    int getReplyId() {
        return (this.xr == null) ? -1
            : this.xr.getAttribute(NETWORK_REPLY_ID_TAG, -1);
    }

    void endListen() {
        this.xr = null;
    }

    /**
     * Read a message using the MessageHandler.
     *
     * @return The {@code Message} found, if any.
     * @exception FreeColException there is a problem creating the message.
     * @exception XMLStreamException if there is a problem reading the stream.
     */
    Message reader()
        throws FreeColException, XMLStreamException {
        if (this.xr == null) return null;

        MessageHandler mh = getMessageHandler();
        if (mh == null) { // FIXME: Temporary fast fail
            throw new FreeColException("No handler at " + xr.getLocalName())
                .preserveDebug();
        }
        return mh.read(this);
    }

    /**
     * Close and clear the socket.
     */
    private void closeSocket() {
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

    /**
     * Close and clear the output stream.
     */
    private void closeOutputStream() {
        if (this.xw != null) {
            try {
                this.socket.shutdownOutput();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error closing output", ioe);
            } finally {
                this.xw.close();
                this.xw = null;
            }
        }
    }

    /**
     * Close and clear the input stream.
     */
    private void closeInputStream() {
        if (this.br != null) {
            try {
                this.socket.shutdownInput();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error closing input", ioe);
            } finally {
                this.br = null;
            }
        }
    }

    /**
     * Start the recieving thread.
     */
    @Override
    public void startReceiving() {
        if (this.receivingThread != null) this.receivingThread.start();
    }

    /**
     * Is this connection alive?
     *
     * @return True if the connection is alive.
     */
    @Override
    public boolean isAlive() {
        return this.socket != null;
    }

    /**
     * Get the host address of this connection.
     *
     * @return The host address, or an empty string on error.
     */
    @Override
    public String getHostAddress() {
        try {
            return ((InetSocketAddress)this.socket.getRemoteAddress()).getHostString();
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
    int getPort() {
        try {
            return ((InetSocketAddress)this.socket.getRemoteAddress()).getPort();
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
    String getSocketName() {
        return (isAlive()) ? getHostAddress() + ":" + getPort() : "";
    }

    /**
     * Set the output write scope.
     *
     * @param ws The new write scope.
     */
    @Override
    public void setWriteScope(FreeColXMLWriter.WriteScope ws) {
        if (this.xw != null) this.xw.setWriteScope(ws);
    }

    @Override
    public FreeColXMLReader getFreeColXMLReader() {
        return this.xr;
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
    @Override
    public CompletableFuture<Void> sendMessage(Message message) {
        if (message == null) return CompletableFuture.completedFuture(null);
        if (this.xw == null) return CompletableFuture.completedFuture(null);
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
        if (this.receivingThread != null) {
            this.receivingThread.askToStop("connection closing");
            this.receivingThread = null;
        }

        // Close the socket before the input stream.  Socket closure will
        // terminate any existing I/O and release the locks.
        closeSocket();
        closeInputStream();
        closeOutputStream();
        
        logger.fine("Connection closed for " + getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        sb.append("[Connection ").append(getName()).append(" (")
            .append(getSocketName()).append(")]");
        return sb.toString();
    }
}
