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

package net.sf.freecol.server.networking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.Message;
import net.sf.freecol.common.networking.MessageHandler;
import static net.sf.freecol.common.util.CollectionUtils.*;
import net.sf.freecol.server.FreeColServer;


/**
 * The networking server in which new clients can connect and methods
 * like {@code sendToAll} are kept.
 *
 * <br><br>
 *
 * When a new client connects to the server a new {@link Connection}
 * is made, with {@link net.sf.freecol.server.control.UserConnectionHandler}
 * as the control object.
 *
 * @see net.sf.freecol.common.networking
 */
public final class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());

    /** Backlog for socket. */
    private static final int BACKLOG_DEFAULT = 10;

    /** The public "well-known" socket to which clients may connect. */
    private final AsynchronousServerSocketChannel serverSocket;

    /** A map of Connection objects, keyed by their Socket. */
    private final HashMap<AsynchronousSocketChannel, Connection> connections = new HashMap<>();

    /** The owner of this {@code Server}. */
    private final FreeColServer freeColServer;

    /** The name of the host for the public socket. */
    private final String host;

    /** The TCP port that is beeing used for the public socket. */
    private final int port;

    /** For information about this variable see the run method. */
    private final Object shutdownLock = new Object();


    /**
     * Creates a new network server. Use {@link #run server.start()} to start
     * listening for new connections.
     *
     * @param freeColServer The owner of this {@code Server}.
     * @param host The name of the host for the public socket.
     * @param port The TCP port to use for the public socket.
     * @throws IOException if the public socket cannot be created.
     */
    public Server(FreeColServer freeColServer, String host, int port)
        throws IOException {

        this.freeColServer = freeColServer;
        this.host = host;
        this.port = port;
        this.serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(host, port), BACKLOG_DEFAULT);
        this.serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    }


    /**
     * Gets the host that is being used for the public socket.
     *
     * @return The name of the host.
     */
    public String getHost() {
        return this.host;
    }

    /**
     * Gets the TCP port that is being used for the public socket.
     *
     * @return The TCP port.
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Gets a {@code Connection} identified by a {@code Socket}.
     *
     * @param socket The {@code Socket} that identifies the
     *               {@code Connection}
     * @return The {@code Connection}.
     */
    public Connection getConnection(AsynchronousSocketChannel socket) {
        return this.connections.get(socket);
    }

    /**
     * Adds a (usually Dummy)Connection into the hashmap.
     *
     * @param connection The connection to add.
     */
    public void addDummyConnection(Connection connection) {
        if (!this.serverSocket.isOpen()) return;
        try {
            this.connections.put(AsynchronousSocketChannel.open(), connection);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a Connection into the hashmap.
     *
     * @param connection The connection to add.
     */
    public void addConnection(Connection connection) {
        if (!this.serverSocket.isOpen()) return;
        this.connections.put(connection.getSocket(), connection);
    }

    /**
     * Removes the given connection.
     *
     * @param connection The connection that should be removed.
     */
    public void removeConnection(Connection connection) {
        this.connections.remove(connection.getSocket());
    }

    /**
     * Sets the specified {@code MessageHandler} to all connections.
     *
     * @param mh The {@code MessageHandler} to use.
     */
    public void setMessageHandlerToAllConnections(MessageHandler mh) {
        for (Connection c : this.connections.values()) {
            c.setMessageHandler(mh);
        }
    }

    /**
     * Sends a network message to all connections with an optional exception.
     *
     * @param message The {@code Message} to send.
     * @param exceptConnection An optional {@code Connection} not
     *     to send to.
     */
    public void sendToAll(Message message, Connection exceptConnection) {
        for (Connection conn : transform(connections.values(),
                                         c -> c != exceptConnection)) {

            if (conn.isAlive()) {
                try {
                    conn.sendMessage(message);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Unable to send to: " + conn, ex);
                }
            } else {
                logger.log(Level.INFO, "Reap dead connection: " + conn);
                removeConnection(conn);
            }
        }
    }

    /**
     * Set the logging state for all connections.
     *
     * @param log If true, enable logging.
     */
    public void setCommsLogging(boolean log) {
        for (Connection conn : connections.values()) {
            conn.setCommsLogging(log);
        }
    }

    /**
     * Sends a network message to all connections.
     *
     * @param message The {@code Message} to send.
     */
    public void sendToAll(Message message) {
        sendToAll(message, null);
    }
    
    /**
     * Start the thread processing.  Contains the loop that is waiting
     * for new connections to the public socket.  When a new client
     * connects to the server a new {@link Connection} is made, with
     * {@link net.sf.freecol.server.control.UserConnectionHandler} as
     * the control object.
     */
    public void start() {
        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            public void completed(AsynchronousSocketChannel sock, Void att) {
                serverSocket.accept(null, this); // accept the next connection
                synchronized (shutdownLock) {
                    if (!serverSocket.isOpen()) return;
                    try {
                        freeColServer.addNewUserConnection(sock);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Connection failed: ", ex);
                    }
                }
            }

            public void failed(Throwable ex, Void att) {
                if (!serverSocket.isOpen()) return;
                logger.log(Level.WARNING, "Connection failed: ", ex);
            }
        });
    }

    /**
     * Shuts down the server thread.
     */
    public void shutdown() {
        try {
            this.serverSocket.close();
            logger.fine("Closed server socket.");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not close the server socket!", e);
        }

        synchronized (this.shutdownLock) {
            // See run() above.
            logger.fine("Wait for Server.run to complete.");
        }

        for (Connection c : transform(this.connections.values(),
                                      Connection::isAlive)) c.disconnect();
        this.connections.clear();

        this.freeColServer.removeFromMetaServer();
        logger.fine("Server shutdown.");
    }
}
