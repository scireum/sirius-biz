/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import java.util.zip.Deflater;

/**
 * Represents compression level supported by the {@link DeflateTransformer}.
 */
public enum CompressionLevel {

    /**
     * Compression is disabled.
     */
    OFF(Deflater.NO_COMPRESSION),

    /**
     * Fast compession ({@link Deflater#BEST_SPEED}).
     */
    FAST(Deflater.BEST_SPEED),

    /**
     * Represents the default compression ({@link Deflater#DEFAULT_COMPRESSION}).
     */
    DEFAULT(Deflater.DEFAULT_COMPRESSION),

    /**
     * Represents the best compression ({@link Deflater#BEST_COMPRESSION}).
     */
    BEST(Deflater.BEST_COMPRESSION);

    private final int deflateLevel;

    CompressionLevel(int deflateLevel) {
        this.deflateLevel = deflateLevel;
    }

    /**
     * Returns the effective compression level understood by {@link Deflater#Deflater(int)}.
     *
     * @return the compression level to use
     */
    public int getDeflateLevel() {
        return deflateLevel;
    }
}
