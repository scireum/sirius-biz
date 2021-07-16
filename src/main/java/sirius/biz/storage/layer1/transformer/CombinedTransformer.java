/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Optional;

/**
 * Combines and applies two {@link ByteBlockTransformer transformers} (one after another).
 */
public class CombinedTransformer implements ByteBlockTransformer {

    private final ByteBlockTransformer first;
    private final ByteBlockTransformer second;

    /**
     * Creates a new instance which applies the <tt>first</tt> transformer and then the <tt>second</tt>.
     * @param first the first transformer to apply
     * @param second the second transformer to apply
     */
    public CombinedTransformer(@Nonnull ByteBlockTransformer first,@Nonnull  ByteBlockTransformer second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Optional<ByteBuf> apply(ByteBuf input) throws IOException {
        return first.apply(input).flatMap(this::forwardToSecond);
    }

    @Override
    public Optional<ByteBuf> complete() throws IOException {
        ByteBuf firstResult = first.complete().flatMap(this::forwardToSecond).orElse(null);
        ByteBuf secondResult = second.complete().orElse(null);

        if (firstResult != null && secondResult != null) {
            ByteBuf combinedResult = Unpooled.buffer(firstResult.readableBytes() + secondResult.readableBytes());
            combinedResult.writeBytes(firstResult);
            combinedResult.writeBytes(secondResult);
            firstResult.release();
            secondResult.release();

            return Optional.of(combinedResult);
        } else if (firstResult != null) {
            return Optional.of(firstResult);
        } else if (secondResult != null) {
            return Optional.of(secondResult);
        } else {
            return Optional.empty();
        }
    }

    protected Optional<ByteBuf> forwardToSecond(ByteBuf intermediateBuffer) {
        try {
            return second.apply(intermediateBuffer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
