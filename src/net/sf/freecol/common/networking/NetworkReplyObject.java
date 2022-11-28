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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class for storing a network response.  If the response has not been
 * set when {@link #getResponse} has been called, this method will
 * block until {@link #setResponse} is called.
 */
public class NetworkReplyObject {

    /** A unique identifier for the message to wait for. */
    private final int networkReplyId;

    /** The response from the network. */
    private CompletableFuture<Object> response;


    /**
     * The constructor.
     *
     * @param networkReplyId The unique identifier for the network message
     *                       this object will store.
     */
    public NetworkReplyObject(int networkReplyId) {
        this.networkReplyId = networkReplyId;
        this.response = new CompletableFuture<Object>();
    }

    /**
     * Gets the unique identifier for the network message this
     * object will store.
     *
     * @return the unique identifier.
     */
    public int getNetworkReplyId() {
        return this.networkReplyId;
    }

    /**
     * Gets the response. If the response has not been set, this method
     * will block until {@link #setResponse} has been called.
     *
     * @param timeout A timeout in milliseconds, after which a
     *      {@code TimeoutException} gets thrown when waiting for
     *      a reply.
     * @return the response.
     * @throws TimeoutException when the timeout is reached.
     */
    public CompletableFuture<Object> getResponse(long timeout) {
        return this.response.orTimeout(timeout, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Sets the response and continues {@code getResponse()}.
     *
     * @param response The response.
     * @see #getResponse
     */
    public void setResponse(Object response) {
        this.response.complete(response);
    }

    /**
     * Interrupt the wait for response.
     */
    public void interrupt() {
        this.response.cancel(true);
    }
}
