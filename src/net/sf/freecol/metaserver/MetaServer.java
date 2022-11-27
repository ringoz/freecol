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

package net.sf.freecol.metaserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.networking.Connection;


/**
 * The entry point and main controller object for the meta server.
 * 
 * When a new client connects to the meta server a new {@link Connection} is
 * made, with {@link MetaServerHandler} as the control object.
 * 
 * @see net.sf.freecol.common.networking
 */
public final class MetaServer {

    private static final Logger logger = Logger.getLogger(MetaServer.class.getName());

    /** The public "well-known" socket to which clients may connect. */
    private final AsynchronousServerSocketChannel serverSocket;

    /** A map of Connection objects, keyed by the Socket they relate to. */
    private final Map<AsynchronousSocketChannel, Connection> connections = new HashMap<>();

    /** The TCP port that is beeing used for the public socket. */
    private final int port;

    private final MetaServerHandler metaServerHandler;


    /**
     * Creates a new network server. Use {@link #run metaServer.start()} to
     * start listening for new connections.
     * 
     * @param port The TCP port to use for the public socket.
     * @throws IOException if the public socket cannot be created.
     */
    public MetaServer(int port) throws IOException {
        this.port = port;
        final MetaRegister mr = new MetaRegister();
        this.metaServerHandler = new MetaServerHandler(this, mr);
        this.serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
    }

    /**
     * Gets the control object that handles the network requests.
     * 
     * @return The {@code MetaServerHandler}.
     */
    public MetaServerHandler getMetaServerHandler() {
        return this.metaServerHandler;
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
     * Shuts down the server thread.
     */
    public void shutdown() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not close the server socket!", e);
        }

        Connection c;
        while ((c = this.connections.remove(0)) != null) c.disconnect();
        logger.info("Metaserver shutdown.");
    }

    /**
     * Gets a {@code Connection} identified by a {@code Socket}.
     * 
     * @param socket The {@code Socket} that identifies the
     *            {@code Connection}
     * @return The {@code Connection}.
     */
    public Connection getConnection(AsynchronousSocketChannel socket) {
        return this.connections.get(socket);
    }

    /**
     * Removes the given connection.
     * 
     * @param connection The connection that should be removed.
     */
    public void removeConnection(Connection connection) {
        this.connections.remove(connection.getSocket());
    }


    // Override Thread

    /**
     * {@inheritDoc}
     */
    public void start() {
        // Starts the thread's processing. Contains the loop that is
        // waiting for new connections to the public socket. When a
        // new client connects to the server a new {@link Connection}
        // is made, with {@link MetaServerHandler} as the input handler.
        while (this.serverSocket.isOpen()) {
            AsynchronousSocketChannel clientSocket = null;
            try {
                clientSocket = serverSocket.accept().get();
                logger.info("Client connection from: "
                    + clientSocket.getRemoteAddress().toString());
                Connection connection = new Connection(clientSocket,
                    FreeCol.METASERVER_THREAD)
                    .setMessageHandler(getMetaServerHandler());
                this.connections.put(clientSocket, connection);
                connection.startReceiving();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Meta-run", e);
            }
        }
    }

    // Main entry point

    /**
     * Create and start a new {@code MetaServer}.
     * 
     * @param args The command-line options.
     */
    public static void main(String[] args) {
        int port = -1;
        try {
            port = Integer.parseInt(args[0]);
        } catch (ArrayIndexOutOfBoundsException|NumberFormatException e) {
            System.out.println("Usage: " + MetaServer.class.getName()
                + " PORT_NUMBER");
            System.exit(1);
        }

        MetaServer metaServer = null;
        try {
            metaServer = new MetaServer(port);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not create MetaServer!", e);
            System.exit(1);
        }
        metaServer.start();
    }
}
