/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Optional;

/**
 * Transforms a given {@link ByteBuf} into another.
 * <p>
 * This can e.g. be used to encrypt or ZIP a chunk of data while transferring it.
 */
public interface ByteBlockTransformer {

    /**
     * Transforms the given input buffer.
     *
     * @param input the buffer to transform
     * @return either a result buffer or an empty optional if the transformer needs more input data to generate an
     * output
     * @throws IOException in case of an error while performing the transformation
     */
    Optional<ByteBuf> apply(@Nonnull ByteBuf input) throws IOException;

    /**
     * Notifies the transformer that all input has been processed and permits to generate a last block of output.
     *
     * @return either a result buffer or an empty optional if the transformer doesn't produce any additional output
     * @throws IOException in case of an error while completing the transformation
     */
    Optional<ByteBuf> complete() throws IOException;
}
