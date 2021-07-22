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
import java.util.zip.Deflater;

/**
 * Compresses the given input data using a {@link Deflater}.
 */
public class DeflateTransformer implements ByteBlockTransformer {

    private final Deflater deflater;

    /**
     * This is a shared buffer which is only used to transfer data from the deflater into the effective output buffer.
     * Being oly used within a method call, we can keep a reusable instance around instead of re-creating one
     * each time.
     */
    private final byte[] deflateBuffer = new byte[TransformingInputStream.DEFAULT_BUFFER_SIZE];

    /**
     * Creates a new trasformer using the given compression level.
     *
     * @param level the compression level to use
     */
    public DeflateTransformer(CompressionLevel level) {
        this.deflater = new Deflater(level.getDeflateLevel());
    }

    @Override
    public Optional<ByteBuf> apply(ByteBuf input) throws IOException {
        if (input.hasArray()) {
            deflater.setInput(input.array(), input.arrayOffset(), input.readableBytes());
        } else {
            byte[] localBuffer = new byte[input.readableBytes()];
            input.readBytes(localBuffer);
            deflater.setInput(localBuffer);
        }

        if (deflater.needsInput()) {
            return Optional.empty();
        }

        ByteBuf outputBuffer = Unpooled.buffer(TransformingInputStream.DEFAULT_BUFFER_SIZE);
        while (!deflater.needsInput()) {
            int outputLength = deflater.deflate(deflateBuffer);
            outputBuffer.writeBytes(deflateBuffer, 0, outputLength);
        }

        return Optional.of(outputBuffer);
    }

    @Override
    public Optional<ByteBuf> complete() throws IOException {
        deflater.finish();
        if (deflater.finished()) {
            return Optional.empty();
        }

        ByteBuf outputBuffer = Unpooled.buffer(TransformingInputStream.DEFAULT_BUFFER_SIZE);
        while (!deflater.finished()) {
            int outputLength = deflater.deflate(deflateBuffer);
            outputBuffer.writeBytes(deflateBuffer, 0, outputLength);
        }
        return Optional.of(outputBuffer);
    }
}
