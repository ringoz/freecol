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

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.Message;


/**
 * A dummy connection, used for AI players.
 */
public final class DummyConnection extends Connection {

    private static final Logger logger = Logger.getLogger(DummyConnection.class.getName());

    /** The other connection, to which outgoing requests are forwarded .*/
    private DummyConnection otherConnection;


    /**
     * Sets up a dummy connection using the specified message handler.
     *
     * @param name A name for this connection.
     */
    public DummyConnection(String name) {
        super(name);
    }


    /**
     * Gets the {@code DummyConnection} this object is connected to.
     *
     * @return The {@code DummyConnection} .
     */
    public DummyConnection getOtherConnection() {
        return this.otherConnection;
    }

    /**
     * Sets the other connection for this dummy connection.
     *
     * @param dc The {@code DummyConnection} to connect to.
     */
    public void setOtherConnection(DummyConnection dc) {
        this.otherConnection = dc;
    }


    // Override Connection

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAlive() {
        return this.otherConnection != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        this.otherConnection = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> sendMessage(Message message) {
        try {
            return CompletableFuture.completedFuture(sendMessageSync(message));
        } catch (FreeColException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Void sendMessageSync(Message message) throws FreeColException {
        Message reply = askMessageSync(message, Connection.DEFAULT_REPLY_TIMEOUT);
        assert reply == null;
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Message> askMessage(Message message, long timeout) {
        try {
            return CompletableFuture.completedFuture(askMessageSync(message, timeout));
        } catch (FreeColException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Message askMessageSync(Message message, long timeout) throws FreeColException {
        DummyConnection other = getOtherConnection();
        if (other == null) return null;
        if (message == null) return null;
        logMessage(message, true);
        Message reply = other.handle(message);
        logMessage(reply, false);
        return reply;
    }


    // Override Object

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[DummyConnection " + getName() + "]";
    }
}
