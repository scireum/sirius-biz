/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.commons.Hasher;

import javax.annotation.Nullable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Used to wrap an {@link InputStream} and calculate a hash while reading.
 */
public class HashingInputStream extends FilterInputStream {

    private final Hasher hasher;

    /**
     * Creates a new instance which wraps the given input stream and uses the given hasher to calculate the hash.
     *
     * @param inputStream the input stream to wrap
     * @param hasher      the hasher to use for calculating the hash
     */
    public HashingInputStream(InputStream inputStream, @Nullable Hasher hasher) {
        super(inputStream);
        this.hasher = hasher;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (hasher != null && b != -1) {
            hasher.hashBytes(new byte[]{(byte) b});
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int bytesRead = super.read(buffer, offset, length);
        if (hasher != null && bytesRead != -1) {
            hasher.hashBytes(buffer, offset, bytesRead);
        }
        return bytesRead;
    }
}
