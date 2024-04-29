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

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.util.Optional;

/**
 * Provides a transformer which applies the given cipher on all data being processed.
 */
public class CipherTransformer implements ByteBlockTransformer {

    private final Cipher cipher;

    /**
     * Creates a new transformer for the given cipher.
     *
     * @param cipher the cipher to use
     */
    public CipherTransformer(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public Optional<ByteBuf> apply(ByteBuf input) throws IOException {
        if (!input.isReadable()) {
            return Optional.empty();
        }

        byte[] buffer = applyCipher(input);
        if (buffer.length > 0) {
            return Optional.of(Unpooled.wrappedBuffer(buffer));
        } else {
            return Optional.empty();
        }
    }

    protected byte[] applyCipher(ByteBuf input) {
        if (input.hasArray()) {
            return cipher.update(input.array(), input.arrayOffset(), input.readableBytes());
        } else {
            byte[] localBuffer = new byte[input.readableBytes()];
            input.readBytes(localBuffer);
            return cipher.update(localBuffer);
        }
    }

    @Override
    public Optional<ByteBuf> complete() throws IOException {
        try {
            byte[] buffer = cipher.doFinal();
            if (buffer.length > 0) {
                return Optional.of(Unpooled.wrappedBuffer(buffer));
            } else {
                return Optional.empty();
            }
        } catch (IllegalBlockSizeException | BadPaddingException exception) {
            throw new IOException("Invalid cipher data", exception);
        }
    }
}
