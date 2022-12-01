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

/**
 * The thread that checks for incoming messages.
 */
final class ReceivingThread {

    private static final Logger logger = Logger.getLogger(ReceivingThread.class.getName());

    /** Maximum number of retries before closing the connection. */
    private static final int MAXIMUM_RETRIES = 5;

    /** A map of network ids to the corresponding waiting thread. */
    private final Map<Integer, NetworkReplyObject> waitingThreads
        = Collections.synchronizedMap(new HashMap<Integer, NetworkReplyObject>());

    /** The connection to receive on. */
    private Connection connection;

    /** A counter for reply ids. */
    private int nextNetworkReplyId;

    private final String threadName;

    /**
     * The constructor to use.
     * 
     * @param connection The {@code Connection} this
     *     {@code ReceivingThread} belongs to.
     * @param threadName The base name for the thread.
     */
    public ReceivingThread(Connection connection, String threadName) {
        this.threadName = "ReceivingThread-" + threadName;
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
        //if (isInterrupted()) return false; interrupt();
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
            logger.info(threadName + ": stopped receiving thread: " + reason);
        }
    }

    /**
     * Disconnects this thread.
     */
    private void disconnect() {
        askToStop("disconnect");
        if (this.connection != null) {
            this.connection.sendDisconnect();
            this.connection = null;
        }
    }

    /**
     * Create a thread to handle an incoming question message.
     *
     * @param qm The {@code QuestionMessage} to handle.
     * @param replyId The network reply.
     * @return A new {@code Thread} to do the work, or null if none required.
     */
    private CompletableFuture<Void> messageQuestion(final Connection conn, final QuestionMessage qm, final int replyId) {
        final Message query = qm.getMessage();
        if (query == null) return null;

        final String task = threadName + "-question-" + replyId + "-" + query.getType();
        return CompletableFuture.runAsync(() -> {
            Message reply;
            try {
                reply = conn.handle(query);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, task + ": handler fail", fce);
                return;
            }

            final String replyTag = (reply == null) ? "null"
                : reply.getType();
            conn.sendMessage(new ReplyMessage(replyId, reply)).thenAccept((v) -> {
                logger.log(Level.FINEST, task + " -> " + replyTag);
            }).exceptionally((ex) -> {
                logger.log(Level.WARNING, task + " -> " + replyTag + " failed", ex);
                return null;
            });
        });
    }

    /**
     * Create a thread to handle an incoming ordinary message.
     *
     * @param message The {@code Message} to handle.
     * @return A new {@code Thread} to do the work, or null if none required.
     */
    private CompletableFuture<Void> messageUpdate(final Connection conn, final Message message) {
        if (message == null) return null;

        final String task = threadName + "-update-" + message.getType();
        return CompletableFuture.runAsync(() -> {
            Message reply;
            try {
                reply = conn.handle(message);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, task + ": handler fail", fce);
                return;
            }

            final String outTag = (reply == null) ? "null" : reply.getType();
            conn.sendMessage(reply).thenAccept((v) -> {
                logger.log(Level.FINEST, task + " -> " + outTag);
            }).exceptionally((ex) -> {
                logger.log(Level.WARNING, task + " -> " + outTag + " failed", ex);
                return null;
            });
        });
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
        final Connection conn = this.connection;
        CompletableFuture<String> start = conn.startListen().exceptionally((xse) -> {
            logger.log(Level.WARNING, threadName + ": listen fail", xse);
            return DisconnectMessage.TAG;
        });

        return start.thenAccept((String tag) -> {
            if (tag == null) return;
            int replyId = -1;

            // Read the message, optionally create a thread to handle it
            switch (tag) {
            case DisconnectMessage.TAG:
                // Do not actually read the message, it might be a fake one
                // due to end-of-stream.
                askToStop("listen-disconnect");
                messageUpdate(conn, TrivialMessage.disconnectMessage);
                break;
    
            case Connection.REPLY_TAG:
                // A reply.  Always respond, even when failing, so as to
                // unblock the waiting thread.
    
                replyId = conn.getReplyId();
                Message rm;
                try {
                    rm = conn.reader();
                } catch (Exception ex) {
                    rm = null;
                    logger.log(Level.WARNING, threadName + ": reply fail", ex);
                }
                NetworkReplyObject nro = this.waitingThreads.remove(replyId);
                if (nro == null) {
                    logger.warning(threadName + ": did not find reply " + replyId);
                } else {
                    nro.setResponse(rm);
                }
                break;
    
            case Connection.QUESTION_TAG:
                // A question.  Build a thread to handle it and send a reply.
    
                replyId = conn.getReplyId();
                Message m = null;
                try {
                    m = conn.reader();
                    assert m instanceof QuestionMessage;
                    messageQuestion(conn, (QuestionMessage)m, replyId);
                } catch (FreeColException fce) {
                    logger.log(Level.WARNING, "No reader for " + replyId, fce);
                } catch (XMLStreamException e) {
                    throw new CompletionException(e);
                }
                break;
                
            default:
                // An ordinary update message.
                // Build a thread to handle it and possibly respond.
    
                try {
                    messageUpdate(conn, conn.reader());
                } catch (Exception ex) {
                    logger.log(Level.FINEST, threadName + ": fail", ex);
                    askToStop("listen-update-fail");
                }
                break;
            }
    
            conn.endListen(); // Clean up
        });
    }


    // Override Thread
    int timesFailed = 0;

    // Receive messages from the network in a loop.
    public void start() {
        if (this.connection == null) return;
        listen().whenComplete((v, ex) -> {
            if (ex == null) {
                timesFailed = 0;
            }
            else {
                logger.log(Level.WARNING, threadName + ": fail", ex);
                if (++timesFailed > MAXIMUM_RETRIES) {
                    disconnect();
                }
            }
            this.start();
        });
    }
}
