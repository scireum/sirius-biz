/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences.jdbc;

import sirius.biz.sequences.SequenceStrategy;
import sirius.biz.sequences.Sequences;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Uses a table in a JDBC database to generate and maintain sequences.
 *
 * @see Sequences
 */
@Register(framework = Sequences.FRAMEWORK_SEQUENCES)
public class SQLSequenceStrategy implements SequenceStrategy {

    /**
     * Contains the name of this strategy.
     */
    public static final String TYPE = "sql";

    @Part
    private OMA oma;

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public Long tryGenerateId(String sequence) throws Exception {
        awaitReadiness();

        // Select the current value which will be returned if all goes well....
        SequenceCounter result = oma.select(SequenceCounter.class).eq(SequenceCounter.NAME, sequence).queryFirst();
        if (result == null) {
            return createSequence(sequence);
        }

        int numRowsChanged = oma.updateStatement(SequenceCounter.class)
                                .inc(SequenceCounter.NEXT_VALUE)
                                .where(SequenceCounter.NAME, sequence)
                                .where(SequenceCounter.NEXT_VALUE, result.getNextValue())
                                .executeUpdate();
        if (numRowsChanged == 1) {
            // Nobody else changed the counter, so we can savely return the determined value...
            return result.getNextValue();
        }

        return null;
    }

    private void awaitReadiness() {
        if (!oma.isReady()) {
            oma.getReadyFuture().await(Duration.ofSeconds(30));
        }
    }

    private Long createSequence(String sequence) {
        try {
            // Try to create a new record, as no counter is yet present...
            SequenceCounter result = new SequenceCounter();
            result.setName(sequence);
            result.setNextValue(2);
            oma.update(result);
            return 1L;
        } catch (HandledException exception) {
            // This only happens if another thread / server inserted the entity already...
            Exceptions.ignore(exception);
            return null;
        }
    }

    @Override
    public long peekNextValue(String sequence) {
        awaitReadiness();

        return oma.select(SequenceCounter.class)
                  .eq(SequenceCounter.NAME, sequence)
                  .first()
                  .map(SequenceCounter::getNextValue)
                  .orElse(1L);
    }

    @Override
    public void setNextValue(String sequence, long nextValue, boolean force) throws Exception {
        awaitReadiness();

        // Select the current value which will be returned if all goes well....
        if (oma.select(SequenceCounter.class).eq(SequenceCounter.NAME, sequence).exists()) {
            updateCounterValue(sequence, nextValue, force);
        } else {
            createSequenceWithValue(sequence, nextValue);
        }
    }

    private void createSequenceWithValue(String sequence, long nextValue) {
        SequenceCounter counter = new SequenceCounter();
        counter.setName(sequence);
        counter.setNextValue(nextValue);
        oma.update(counter);
    }

    private void updateCounterValue(String sequence, long nextValue, boolean force) throws SQLException {
        String sql = "UPDATE sequencecounter SET nextValue = ${value} WHERE name = ${name}";
        if (!force) {
            sql += "  AND nextValue <= ${value}";
        }

        int updatedRows = oma.getDatabase(Mixing.DEFAULT_REALM)
                             .createQuery(sql)
                             .set("name", sequence)
                             .set("value", nextValue)
                             .executeUpdate();
        if (updatedRows != 1) {
            throw Exceptions.handle()
                            .to(Sequences.LOG)
                            .withSystemErrorMessage("Failed to specify the next value for sequence %s", sequence)
                            .handle();
        }
    }

    @Override
    public void collectKnownSequences(Consumer<String> nameConsumer) throws Exception {
        oma.select(SequenceCounter.class)
           .fields(SequenceCounter.NAME)
           .orderAsc(SequenceCounter.NAME)
           .iterateAll(counter -> nameConsumer.accept(counter.getName()));
    }
}
