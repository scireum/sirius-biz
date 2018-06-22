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

/**
 * Encapsulates the effective implementation of generating sequences of unique IDs.
 */
public interface SequenceStrategy extends Named {

    /**
     * Tries to generate the next id in the given sequence.
     *
     * @param sequence the sequence to generate the next id for
     * @return either the next id to use or <tt>null</tt> if a race condition or conflict occurred. This will
     * instruct the framework to perform a retry
     * @throws Exception in case of a severe error
     */
    Long tryGenerateId(String sequence) throws Exception;

    /**
     * Peeks at the next value in the sequence without using it.
     *
     * @param sequence the sequence to determine the next value for
     * @return the next value in the given sequence
     * @throws Exception in case of a severe error
     * @see Sequences#peekNextValue(String)
     */
    long peekNextValue(String sequence) throws Exception;

    /**
     * Specifies the next value to use for the given sequence.
     * <p>
     * Unless <tt>force</tt> is set to <tt>true</tt>, the value has to be higher than the current counter value to
     * prevent non unique numbers from being generated.
     *
     * @param sequence  the sequence to update
     * @param nextValue the next  value that will be returned when calling {@link #tryGenerateId(String)} for this
     *                  sequence.
     * @param force     if <tt>true</tt>, no sanity checks are performed and the sequence can be reset to <b>ANY</b>
     *                  value. This is rather dangerous, as it might lead to the generation of duplicate ids. If
     *                  set to <tt>false</tt>, the given <tt>nextValue</tt> has to be higher than the current sequence
     *                  value.
     * @throws Exception in case or a severe error
     */
    void setNextValue(String sequence, long nextValue, boolean force) throws Exception;

    /**
     * Enumerates the names of all known sequences.
     *
     * @param nameConsumer the consumer to be supplied with the names
     * @throws Exception in case or a severe error
     */
    void collectKnownSequences(Consumer<String> nameConsumer) throws Exception;
}
