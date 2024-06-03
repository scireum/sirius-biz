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

import java.io.IOException;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Uncompresses data which has previously been compressed by a {@link DeflateTransformer}.
 */
public class InflateTransformer implements ByteBlockTransformer {

    private final Inflater inflater = new Inflater();

    /**
     * This is a shared buffer which is only used to transfer data from the inflater into the effective output buffer.
     * Being only used within a method call, we can keep a reusable instance around instead of re-creating one
     * each time.
     */
    private final byte[] inflateBuffer = new byte[TransformingInputStream.DEFAULT_BUFFER_SIZE];

    @Override
    public Optional<ByteBuf> apply(ByteBuf input) throws IOException {
        if (input.hasArray()) {
            inflater.setInput(input.array(), input.arrayOffset(), input.readableBytes());
        } else {
            byte[] localBuffer = new byte[input.readableBytes()];
            input.readBytes(localBuffer);
            inflater.setInput(localBuffer);
        }

        if (inflater.needsInput()) {
            return Optional.empty();
        }

        ByteBuf outputBuffer = Unpooled.buffer(TransformingInputStream.DEFAULT_BUFFER_SIZE);
        while (!inflater.needsInput()) {
            try {
                int outputLength = inflater.inflate(inflateBuffer);
                outputBuffer.writeBytes(inflateBuffer, 0, outputLength);
            } catch (DataFormatException exception) {
                throw new IOException(exception);
            }
        }

        return Optional.of(outputBuffer);
    }

    @Override
    public Optional<ByteBuf> complete() throws IOException {
        if (inflater.finished()) {
            return Optional.empty();
        }

        ByteBuf outputBuffer = Unpooled.buffer(TransformingInputStream.DEFAULT_BUFFER_SIZE);
        while (!inflater.finished()) {
            if (inflater.needsInput()) {
                throw new IllegalStateException("Inflater needs input in complete!");
            }
            try {
                int outputLength = inflater.inflate(inflateBuffer);
                outputBuffer.writeBytes(inflateBuffer, 0, outputLength);
            } catch (DataFormatException exception) {
                throw new IOException(exception);
            }
        }

        return Optional.of(outputBuffer);
    }
}
