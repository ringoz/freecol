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

import java.awt.EventQueue;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColXMLWriter;


/**
 * A network connection.
 * Responsible for both sending and receiving network messages.
 */
public abstract class Connection implements Closeable {

    private static final Logger logger = Logger.getLogger(Connection.class.getName());

    /**
     * The default timeout when waiting for a reply from the server. Defined
     * in milliseconds.
     * 
     * Setting 30s for now since there was no timeout earlier when waiting
     * for å reply.
     */
    public static final long DEFAULT_REPLY_TIMEOUT = 30000;
    
    static final char END_OF_STREAM = '\n';
    static final char[] END_OF_STREAM_ARRAY = { END_OF_STREAM };
    static final String SEND_SUFFIX = "-send";
    static final String REPLY_SUFFIX = "-reply";
    
    /** The name of the connection. */
    private String name;

    /** The FreeColXMLWriter to write logging messages to. */
    private FreeColXMLWriter lw;

    /** The message handler to process incoming messages with. */
    private MessageHandler messageHandler = null;


    /**
     * Trivial constructor.
     *
     * @param name The name of the connection.
     */
    protected Connection(String name) {
        this.name = name;

        // Make a (pretty printing) transformer, but only make the log
        // writer in COMMS-debug mode.
        setCommsLogging(FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.COMMS));
    }

    /**
     * Sets up a new socket with specified host and port
     *
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @param name The name for the connection.
     * @exception IOException if the socket creation is problematic.
     */
    public static CompletableFuture<Connection> open(String host, int port, String name) throws IOException {
        return SocketConnection.open(host, port, name);
    }

    /**
     * Start the recieving thread.
     */
    public void startReceiving() {
    }
        
    /**
     * Is this connection alive?
     *
     * @return True if the connection is alive.
     */
    public boolean isAlive() {
        return false;
    }

    /**
     * Get the host address of this connection.
     *
     * @return The host address, or an empty string on error.
     */
    public String getHostAddress() {
        return "";
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
    public abstract CompletableFuture<Message> askMessage(Message message, long timeout);
    
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
    public abstract CompletableFuture<Void> sendMessage(Message message);

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
       
        final boolean wasDispatchThread = EventQueue.isDispatchThread();
        return askMessage(message, DEFAULT_REPLY_TIMEOUT).thenAcceptAsync((Message response) -> {
            try {
                if (response != null) {
                    Message reply = handle(response);
                    assert reply == null;
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, (command) -> {
            if (!wasDispatchThread || EventQueue.isDispatchThread())
                command.run();
            else
                EventQueue.invokeLater(command);
        });
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
        return sendMessage(message);
    }
    

    // Implement Closeable

    /**
     * Close this connection.
     */
    abstract public void close();


    // Override Object

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[Connection " + getName() + "]";
    }
}
