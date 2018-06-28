/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.memory;

public class BitBuddy {

    public static final int LONG_BYTES = 8;
    public static final int INT_BYTES = 4;

    public static long wrapLong(long value) {
        if (isWrappedLong(value)) {
            throw new IllegalArgumentException("Value is too large or already wrapped!");
        }

        return 0x8000000000000000L | value;
    }

    public static boolean isWrappedLong(long value) {
        return (0x8000000000000000L & value) != 0;
    }

    public static long unwrapLong(long value) {
        return 0x7FFFFFFFFFFFFFFFL & value;
    }

    public static int wrap(int value) {
        if (isWrapped(value)) {
            throw new IllegalArgumentException("Value is too large or already wrapped!");
        }

        return 0x80000000 | value;
    }

    public static boolean isWrapped(int value) {
        return (0x80000000 & value) != 0;
    }

    public static int unwrap(int value) {
        return 0x7FFFFFFF & value;
    }
}
