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
import java.io.InputStream;

/**
 * Wraps a {@link InputStream} annd applies a {@link ByteBlockTransformer} while shovelling data.
 */
public class TransformingInputStream extends InputStream {

    /**
     * Defines the default size of byte blocks being processed by this stream.
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private InputStream sourceStream;
    private final ByteBlockTransformer transformer;
    private ByteBuf buffer = Unpooled.EMPTY_BUFFER;

    /**
     * Creates a new instance which applies the given transformer on the given stream.
     *
     * @param sourceStream the origin which provides the input data
     * @param transformer  the transformer to apply
     */
    public TransformingInputStream(InputStream sourceStream, ByteBlockTransformer transformer) {
        this.sourceStream = sourceStream;
        this.transformer = transformer;
    }

    @Override
    public int read() throws IOException {
        byte[] singleByteBuffer = new byte[1];
        int bytesRead = read(singleByteBuffer, 0, 1);
        return bytesRead == 1 ? singleByteBuffer[0] : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (sourceStream == null && !buffer.isReadable()) {
            buffer.release();
            return -1;
        }

        while (sourceStream != null && !buffer.isReadable()) {
            buffer.release();

            byte[] readBuffer = new byte[DEFAULT_BUFFER_SIZE];
            int sourceBytesRead = sourceStream.read(readBuffer);
            if (sourceBytesRead < 0) {
                sourceStream.close();
                sourceStream = null;
                buffer = transformer.complete().orElse(Unpooled.EMPTY_BUFFER);
                if (!buffer.isReadable()) {
                    buffer.release();
                    return -1;
                }
            } else if (sourceBytesRead > 0) {
                ByteBuf input = Unpooled.wrappedBuffer(readBuffer, 0, sourceBytesRead);
                buffer = transformer.apply(input).orElse(Unpooled.EMPTY_BUFFER);
                input.release();
            }
        }

        int bytesToRead = Math.min(len, buffer.readableBytes());
        buffer.readBytes(b, off, bytesToRead);
        return bytesToRead;
    }
}
