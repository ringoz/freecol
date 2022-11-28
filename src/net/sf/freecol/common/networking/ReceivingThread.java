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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import org.xml.sax.SAXException;

import net.sf.freecol.common.FreeColException;

abstract class ThreadWork implements Runnable {
    private final String name;
    private CompletableFuture<Void> work;

    ThreadWork(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ThreadWork[" + getName() + "]";
    }

    public String getName() {
        return this.name;
    }

    public void start() {
        this.work = CompletableFuture.runAsync(this);
    }

    public void interrupt() {
        this.work.cancel(true);
    }

    public boolean isInterrupted() {
        return this.work.isCancelled();
    }    
};

/**
 * The thread that checks for incoming messages.
 */
final class ReceivingThread extends ThreadWork {

    private static final Logger logger = Logger.getLogger(ReceivingThread.class.getName());

    /** A class to handle questions. */
    private static class QuestionThread extends ThreadWork {

        /** The connection to communicate with. */
        private final Connection conn;

        /** The message to handle. */
        private final Message query;

        /** The reply identifier to use when sending a reply. */
        private final int replyId;


        /**
         * Build a new thread to respond to a question message.
         *
         * @param name The thread name.
         * @param conn The {@code Connection} to use for I/O.
         * @param query The {@code Message} to handle.
         * @param replyId The network reply identifier 
         */
        public QuestionThread(String name, Connection conn, Message query,
            int replyId) {
            super(name);

            this.conn = conn;
            this.query = query;
            this.replyId = replyId;
        }
            
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            Message reply;
            try {
                reply = this.conn.handle(this.query);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, getName() + ": handler fail", fce);
                return;
            }

            final String replyTag = (reply == null) ? "null"
                : reply.getType();
            this.conn.sendMessage(new ReplyMessage(this.replyId, reply)).thenAccept((v) -> {
                logger.log(Level.FINEST, getName() + " -> " + replyTag);
            }).exceptionally((ex) -> {
                logger.log(Level.WARNING, getName() + " -> " + replyTag + " failed", ex);
                return null;
            });
        }
    };

    private static class UpdateThread extends ThreadWork {

        /** The connection to use for I/O. */
        private final Connection conn;

        /** The message to handle. */
        private final Message message;


        /**
         * Build a new thread to handle an update.
         *
         * @param name The thread name.
         * @param conn The {@code Connection} to use to send messsages.
         * @param message The {@code Message} to handle.
         */
        public UpdateThread(String name, Connection conn, Message message) {
            super(name);

            this.conn = conn;
            this.message = message;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            Message reply;
            try {
                reply = this.conn.handle(this.message);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, getName() + ": handler fail", fce);
                return;
            }

            final String outTag = (reply == null) ? "null" : reply.getType();
            this.conn.sendMessage(reply).thenAccept((v) -> {
                logger.log(Level.FINEST, getName() + " -> " + outTag);
            }).exceptionally((ex) -> {
                logger.log(Level.WARNING, getName() + " -> " + outTag + " failed", ex);
                return null;
            });
        }
    };

    /** Maximum number of retries before closing the connection. */
    private static final int MAXIMUM_RETRIES = 5;

    /** A map of network ids to the corresponding waiting thread. */
    private final Map<Integer, NetworkReplyObject> waitingThreads
        = Collections.synchronizedMap(new HashMap<Integer, NetworkReplyObject>());

    /** The connection to receive on. */
    private final Connection connection;

    /** A counter for reply ids. */
    private int nextNetworkReplyId;


    /**
     * The constructor to use.
     * 
     * @param connection The {@code Connection} this
     *     {@code ReceivingThread} belongs to.
     * @param threadName The base name for the thread.
     */
    public ReceivingThread(Connection connection, String threadName) {
        super("ReceivingThread-" + threadName);

        this.connection = connection;
        this.nextNetworkReplyId = 1;
    }

    /**
     * Gets the next network reply identifier that will be used when
     * identifing a network message.
     * 
     * @return The next available network reply identifier.
     */
    public synchronized int getNextNetworkReplyId() {
        return nextNetworkReplyId++;
    }

    /**
     * Creates and registers a new {@code NetworkReplyObject} with the
     * specified object identifier.
     * 
     * @param networkReplyId The identifier of the message the calling
     *     thread should wait for.
     * @return The {@code NetworkReplyObject} containing the network
     *     message.
     */
    public NetworkReplyObject waitForNetworkReply(int networkReplyId) {
        NetworkReplyObject nro = new NetworkReplyObject(networkReplyId);
        this.waitingThreads.put(networkReplyId, nro);
        return nro;
    }

    /**
     * Stop this thread.
     *
     * @return True if the thread was previously running and is now stopped.
     */
    private boolean stopThread() {
        if (isInterrupted()) return false; interrupt();
        // Explicit extraction from waitingThreads before iterating
        Collection<NetworkReplyObject> nros;
        synchronized (this.waitingThreads) {
            nros = this.waitingThreads.values();
        }
        for (NetworkReplyObject o : nros) o.interrupt();
        return true;
    }
        
    /**
     * Ask this thread to stop work.
     *
     * @param reason A brief description of why the thread should stop.
     */
    public void askToStop(String reason) {
        if (stopThread()) {
            logger.info(getName() + ": stopped receiving thread: " + reason);
        }
    }

    /**
     * Disconnects this thread.
     */
    private void disconnect() {
        askToStop("disconnect");
        this.connection.sendDisconnect();
    }

    /**
     * Create a thread to handle an incoming question message.
     *
     * @param qm The {@code QuestionMessage} to handle.
     * @param replyId The network reply.
     * @return A new {@code Thread} to do the work, or null if none required.
     */
    private ThreadWork messageQuestion(final QuestionMessage qm,
                                   final int replyId) {
        final Message query = qm.getMessage();
        return (query == null) ? null
            : new QuestionThread(getName() + "-question-" + replyId + "-"
                                     + query.getType(),
                                 this.connection, query, replyId);
    }

    /**
     * Create a thread to handle an incoming ordinary message.
     *
     * @param message The {@code Message} to handle.
     * @return A new {@code Thread} to do the work, or null if none required.
     */
    private ThreadWork messageUpdate(final Message message) {
        if (message == null) return null;
        final String inTag = message.getType();

        return new UpdateThread(getName() + "-update-" + inTag,
                                this.connection, message);
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
        CompletableFuture<String> start = this.connection.startListen().exceptionally((xse) -> {
            logger.log(Level.WARNING, getName() + ": listen fail", xse);
            return DisconnectMessage.TAG;
        });

        return start.thenAccept((String tag) -> {
            if (tag == null) return;
            int replyId = -1;

            // Read the message, optionally create a thread to handle it
            ThreadWork t = null;
            switch (tag) {
            case DisconnectMessage.TAG:
                // Do not actually read the message, it might be a fake one
                // due to end-of-stream.
                askToStop("listen-disconnect");
                t = messageUpdate(TrivialMessage.disconnectMessage);
                break;
    
            case Connection.REPLY_TAG:
                // A reply.  Always respond, even when failing, so as to
                // unblock the waiting thread.
    
                replyId = this.connection.getReplyId();
                Message rm;
                try {
                    rm = this.connection.reader();
                } catch (Exception ex) {
                    rm = null;
                    logger.log(Level.WARNING, getName() + ": reply fail", ex);
                }
                NetworkReplyObject nro = this.waitingThreads.remove(replyId);
                if (nro == null) {
                    logger.warning(getName() + ": did not find reply " + replyId);
                } else {
                    nro.setResponse(rm);
                }
                break;
    
            case Connection.QUESTION_TAG:
                // A question.  Build a thread to handle it and send a reply.
    
                replyId = this.connection.getReplyId();
                Message m = null;
                try {
                    m = this.connection.reader();
                    assert m instanceof QuestionMessage;
                    t = messageQuestion((QuestionMessage)m, replyId);
                } catch (FreeColException fce) {
                    logger.log(Level.WARNING, "No reader for " + replyId, fce);
                    t = null;
                } catch (XMLStreamException e) {
                    throw new CompletionException(e);
                }
                break;
                
            default:
                // An ordinary update message.
                // Build a thread to handle it and possibly respond.
    
                try {
                    t = messageUpdate(this.connection.reader());
                } catch (Exception ex) {
                    logger.log(Level.FINEST, getName() + ": fail", ex);
                    askToStop("listen-update-fail");
                }
                break;
            }
    
            // Start the thread
            if (t != null) t.start();
    
            this.connection.endListen(); // Clean up
        });
    }


    // Override Thread
    int timesFailed = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        // Receive messages from the network in a loop.
        listen().whenComplete((v, ex) -> {
            if (ex == null) {
                timesFailed = 0;
            }
            else {
                logger.log(Level.WARNING, getName() + ": fail", ex);
                if (++timesFailed > MAXIMUM_RETRIES) {
                    disconnect();
                }
            }
            this.run();
        });
    }
}
