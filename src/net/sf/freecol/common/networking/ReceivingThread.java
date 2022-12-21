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
    private SocketConnection connection;

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
    public ReceivingThread(SocketConnection connection, String threadName) {
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
     * Listens to the InputStream and calls the message handler for
     * each message received.
     * 
     * @exception IOException on low level IO problems.
     * @exception SAXException if a problem occured during parsing.
     * @exception XMLStreamException if a problem occured during parsing.
     */
    private CompletableFuture<Void> listen() {
        final var conn = this.connection;
        CompletableFuture<String> start = conn.startListen().exceptionally((xse) -> {
            logger.log(Level.WARNING, threadName + ": listen fail", xse);
            return DisconnectMessage.TAG;
        });

        return start.thenAccept((String tag) -> {
            if (tag == null) return;
            final int replyId = conn.getReplyId();

            Message message;
            try {
                message = tag.equals(DisconnectMessage.TAG) ? TrivialMessage.disconnectMessage : conn.reader();
            } catch (Exception ex) {
                logger.log(Level.WARNING, threadName + ": read message fail", ex);
                return;
            }

            if (tag.equals(SocketConnection.REPLY_TAG)) {
                NetworkReplyObject nro = this.waitingThreads.remove(replyId);
                if (nro == null) {
                    logger.warning(threadName + ": did not find reply " + replyId);
                } else {
                    nro.setResponse(message);
                }
                return;
            }

            if (tag.equals(SocketConnection.QUESTION_TAG))
                message = ((QuestionMessage)message).getMessage();
            final String subTag = threadName + "-" + message.getType();

            Message reply;
            try {
                reply = conn.handle(message);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, subTag + ": handler fail", fce);
                return;
            }
    
            final String replyTag = (reply == null) ? "null" : reply.getType();
            if (tag.equals(SocketConnection.QUESTION_TAG))
                reply = new ReplyMessage(replyId, reply);

            conn.sendMessage(reply).thenAccept((v) -> {
                logger.log(Level.FINEST, subTag + " -> " + replyTag);
            }).exceptionally((ex) -> {
                logger.log(Level.WARNING, subTag + " -> " + replyTag + " failed", ex);
                return null;
            });
        }).whenComplete((v, e) -> {
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
