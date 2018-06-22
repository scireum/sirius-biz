/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences;

import sirius.kernel.di.std.Named;

import java.util.function.Consumer;

public interface SequenceStrategy extends Named {

    Long tryGenerateId(String sequence) throws Exception;

    long peekNextValue(String sequence) throws Exception;

    void setNextValue(String sequence, long nextValue, boolean force) throws Exception;

    void collectKnownSequences(Consumer<String> nameConsumer) throws Exception;
}
