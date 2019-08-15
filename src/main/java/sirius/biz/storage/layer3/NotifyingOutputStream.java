/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.async.Future;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Wraps a given {@link OutputStream} and invokes a callback once the stream was fully read.
 */
public class NotifyingOutputStream extends OutputStream {

    private OutputStream delegate;
    private Future closeFuture;

    /**
     * Creates a new wrapper for the given stream and callback.
     *
     * @param delegate    the stream to wrap
     * @param closeFuture the future which will be notified once processing has been finished
     */
    public NotifyingOutputStream(OutputStream delegate, Future closeFuture) {
        this.delegate = delegate;
        this.closeFuture = closeFuture;
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();
        } catch (IOException e) {
            if (!closeFuture.isCompleted()) {
                closeFuture.fail(e);
            }
            throw e;
        }

        if (!closeFuture.isCompleted()) {
            closeFuture.success();
        }
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }
}
