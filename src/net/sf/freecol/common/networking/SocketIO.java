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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import jsinterop.annotations.JsAsync;
import net.sf.freecol.common.util.CharsetCompat;
import net.sf.freecol.common.util.PromiseCompat;

public class SocketIO implements Channel {
    public static class Server implements Channel {
        private final AsynchronousServerSocketChannel serverSocket;

        public Server(String host, int port) throws IOException {
            if (host == null) host = "0.0.0.0";
            this.serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(host, port));
            this.serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }

        public CompletableFuture<SocketIO> accept() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new SocketIO(this.serverSocket.accept().get());
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
        }

        @Override
        public boolean isOpen() {
            return this.serverSocket.isOpen();
        }

        @Override
        public void close() throws IOException {
            this.serverSocket.close();
        }
    }

    private final AsynchronousSocketChannel socket;
    private final ByteBuffer readBuf = (ByteBuffer)ByteBuffer.allocate(1 << 14).flip();
    private CompletableFuture<Void> pendingWrite = CompletableFuture.completedFuture(null);

    private SocketIO(AsynchronousSocketChannel socket) {
        this.socket = socket;
    }

    static CompletableFuture<SocketIO> connect(String host, int port) throws IOException {
        final var socket = AsynchronousSocketChannel.open();
        final var addr = new InetSocketAddress(host, port);
        return PromiseCompat.create((resolve, reject) -> {
            socket.connect(addr, socket, new CompletionHandler<Void,AsynchronousSocketChannel>() {
                @Override
                public void completed(Void v, AsynchronousSocketChannel socket) {
                    resolve.accept(new SocketIO(socket));
                }
    
                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel socket) {
                    reject.accept(exc);
                }
            });
        });
    }

    public SocketAddress getRemoteAddress() throws IOException {
        return this.socket.getRemoteAddress();
    }

    private CompletableFuture<ByteBuffer> readBytesAsync(final ByteBuffer buf) {
        return PromiseCompat.create((resolve, reject) -> {
            socket.read(buf, buf, new CompletionHandler<Integer,ByteBuffer>() {
                @Override
                public void completed(Integer len, ByteBuffer buf) {
                    resolve.accept((len != -1) ? (ByteBuffer)buf.flip() : null);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buf) {
                    reject.accept(exc);
                }
            });
        });
    }

    @JsAsync
    public CompletableFuture<CharBuffer> readLineAsync() {
        final var all = ByteBuffer.allocate(1 << 20);
        for (ByteBuffer buf = this.readBuf; buf != null; buf = await(readBytesAsync((ByteBuffer)buf.clear()))) {
            while (buf.hasRemaining()) {
                final byte b = buf.get();
                if (b == '\n') {
                    final CharBuffer cbuf = CharsetCompat.decode(StandardCharsets.UTF_8, (ByteBuffer)all.flip());
                    return CompletableFuture.completedFuture(cbuf);
                }
                all.put(b);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> writeBytesAsync(final ByteBuffer buf) {
        return PromiseCompat.create((resolve, reject) -> {
            socket.write(buf, buf, new CompletionHandler<Integer,ByteBuffer>() {
                @Override
                public void completed(Integer len, ByteBuffer buf) {
                    if (buf.hasRemaining())
                        socket.write(buf, buf, this);
                    else
                        resolve.accept(null);
                }
    
                @Override
                public void failed(Throwable exc, ByteBuffer buf) {
                    reject.accept(exc);
                }
            });
        });
    }

    public CompletableFuture<Void> writeLineAsync(final CharBuffer cbuf) throws IOException {
        final ByteBuffer buf = CharsetCompat.encode(StandardCharsets.UTF_8, cbuf);
        synchronized (this) {
            return pendingWrite = pendingWrite.thenCompose((v) -> writeBytesAsync(buf));
        }
    }

    @Override
    public boolean isOpen() {
        return this.socket.isOpen();
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }
}
