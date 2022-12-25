package net.sf.freecol.common.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PromiseCompat {
    public static final <T> CompletableFuture<T> create(final BiConsumer<Consumer<? super T>, Consumer<Throwable>> action) {
        final var result = new CompletableFuture<T>();
        action.accept((val) -> result.complete(val), (err) -> result.completeExceptionally(err));
        return result;
    }
}
