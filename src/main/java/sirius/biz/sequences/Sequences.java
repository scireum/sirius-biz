/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences;

import sirius.biz.locks.Locks;
import sirius.db.jdbc.schema.Schema;
import sirius.db.mongo.Mongo;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a facility to generate unique consecutive numbers.
 * <p>
 * For each sequence name, a call to {@link #generateId(String)} will return a unique number. The initial or next
 * number being returned can be specified by {@link #setNextValue(String, long, boolean)}.
 * <p>
 * Note that these sequences are global and not tenant aware. Therefore care must be taken to generate unique names for
 * sequences. A viable option is to use {@link sirius.db.mixing.BaseEntity#getUniqueName()} of the entity which utilizes
 * this generator.
 */
@Register(classes = Sequences.class, framework = Sequences.FRAMEWORK_SEQUENCES)
public class Sequences {

    /**
     * Names the framework which must be enabled to activate the sequences feature.
     */
    public static final String FRAMEWORK_SEQUENCES = "biz.sequences";

    public static final Log LOG = Log.get("sequences");

    @Part
    private Locks locks;

    @Part
    private Schema schema;

    @Part
    private Mongo mongo;

    @Part(configPath = "sequences.strategy")
    private SequenceStrategy sequenceStrategy;

    /**
     * Returns the next value in the given sequence.
     * <p>
     * Note that this method doesn't use locks or transactions. It rather utilizes optimistic locking, which scales
     * extremely well. However, the algorithm used is not intended for extreme parallel usage. In such scenarios,
     * it will not content but rather give up after some tries and report an appropriate exception.
     *
     * @param sequence the name of the sequence which is counted up.
     * @return the next value (which has not yet been returned). If the sequence is unknown, <tt>1</tt> is returned.
     * @throws HandledException If the system was unable to generate a unique sequence number. This might happen in
     *                          extreme load conditions, as internally an optimistic locking algorithm is employed.
     */
    public long generateId(String sequence) {
        try {
            int retries = 2;
            while (retries-- > 0) {
                Long id = sequenceStrategy.tryGenerateId(sequence);
                if (id != null) {
                    return id;
                }

                // Block a short random amount of time to resolve conflicts with other waiting threads
                Wait.randomMillis(50, 100);
            }

            return generateInLock(sequence);
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage("Failed to generate an unique number for %s: %s (%s)", sequence)
                            .handle();
        }
    }

    private long generateInLock(String sequence) throws Exception {
        if (locks.tryLock("sequence-" + sequence, Duration.ofSeconds(5))) {
            try {
                Long id = sequenceStrategy.tryGenerateId(sequence);
                if (id == null) {
                    throw Exceptions.handle()
                                    .to(LOG)
                                    .withSystemErrorMessage(
                                            "Failed to generate an unique sequence number for %s while holding a lock.",
                                            sequence)
                                    .handle();
                }
                return id;
            } finally {
                locks.unlock("sequence-" + sequence);
            }
        } else {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage(
                                    "Failed to acquire a lock to generate a unique sequence number for %s.",
                                    sequence)
                            .handle();
        }
    }

    /**
     * Sets the initial or next value of the given sequence.
     * <p>
     * Unless <tt>force</tt> is set to <tt>true</tt>, the value has to be higher than the current counter value to
     * prevent non unique numbers from being generated.
     *
     * @param sequence  the sequence to update
     * @param nextValue the next  value that will be returned when calling {@link #generateId(String)} for this
     *                  sequence.
     * @param force     if <tt>true</tt>, no sanity checks are performed and the sequence can be reset to <b>ANY</b>
     *                  value. This is rather dangerous, as it might lead to the generation of duplicate ids. If
     *                  set to <tt>false</tt>, the given <tt>nextValue</tt> has to be higher than the current sequence
     *                  value.
     */
    public void setNextValue(String sequence, long nextValue, boolean force) {
        try {
            sequenceStrategy.setNextValue(sequence, nextValue, force);
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Failed to specify the next value for sequence %s due to an error: %s (%s)",
                                    sequence)
                            .handle();
        }
    }

    /**
     * Identifies the next value for the given sequence without using it.
     * <p>
     * Note that this method is only used for reporting and statistics and must never be called by production code,
     * as there is no guarantee that there isn't a parallel thread which currently acquires the returned value while
     * this method is running.
     *
     * @param sequence the sequence to peek the next value for
     * @return the next value for the sequence (unless already acquired by another thread)
     */
    public long peekNextValue(String sequence) {
        try {
            return sequenceStrategy.peekNextValue(sequence);
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Failed to peek the next value for sequence %s due to an error: %s (%s)",
                                    sequence)
                            .handle();
        }
    }

    /**
     * Enumerates all sequences known to the framework.
     * <p>
     * This must be only used by reporting systems. There is no guarantee that this list is complete.
     *
     * @return a list of known sequences.
     */
    public List<String> getKnownSequences() {
        try {
            List<String> result = new ArrayList<>();
            sequenceStrategy.collectKnownSequences(result::add);
            return result;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Failed to determine the list of known sequences due to an error: %s (%s)")
                            .handle();
        }
    }
}
