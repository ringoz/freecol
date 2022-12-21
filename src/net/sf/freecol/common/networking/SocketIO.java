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
import java.io.Writer;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import net.sf.freecol.common.util.CharsetCompat;

public class SocketIO extends Writer {
    private static final int BUFFER_SIZE = 1 << 14;

    private final AsynchronousSocketChannel socket;
    private final ByteBuffer readBuf;
    private CompletableFuture<Void> pendingWrite = CompletableFuture.completedFuture(null);

    public SocketIO(AsynchronousSocketChannel socket) {
        this.socket = socket;
        this.readBuf = (ByteBuffer)ByteBuffer.allocate(BUFFER_SIZE).flip();
    }

    public SocketAddress getRemoteAddress() throws IOException {
        return this.socket.getRemoteAddress();
    }

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

    public CompletableFuture<String> readLineAsync() {
        final var all = ByteBuffer.allocate(1 << 20);
        for (ByteBuffer buf = this.readBuf; buf != null; buf = await(readBytesAsync((ByteBuffer)buf.clear()))) {
            while (buf.hasRemaining()) {
                final byte b = buf.get();
                if (b == '\n') {
                    final String line = CharsetCompat.decode(StandardCharsets.UTF_8, (ByteBuffer)all.flip()).toString();
                    return CompletableFuture.completedFuture(line);
                }
                all.put(b);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

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

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        final ByteBuffer buf = CharsetCompat.encode(StandardCharsets.UTF_8, CharBuffer.wrap(cbuf, off, len));
        synchronized (this) {
            pendingWrite = pendingWrite.thenCompose((v) -> writeBytesAsync(buf));
        }
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }
}
