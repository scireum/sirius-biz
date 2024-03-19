/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences.mongo;

import com.mongodb.MongoWriteException;
import com.mongodb.client.result.UpdateResult;
import sirius.biz.sequences.SequenceStrategy;
import sirius.biz.sequences.Sequences;
import sirius.db.KeyGenerator;
import sirius.db.mongo.Doc;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.QueryBuilder;
import sirius.db.mongo.Updater;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Uses a collection in MongoDB to generate and maintain sequences.
 *
 * @see Sequences
 */
@Register(framework = Sequences.FRAMEWORK_SEQUENCES)
public class MongoSequenceStrategy implements SequenceStrategy {

    /**
     * Contains the name of this strategy.
     */
    public static final String TYPE = "mongo";

    @Part
    private Mongo mongo;

    @Part
    private KeyGenerator keyGen;

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public Long tryGenerateId(String sequence) throws Exception {
        Doc counter = mongo.find()
                           .where(MongoSequenceCounter.NAME, sequence)
                           .singleIn(MongoSequenceCounter.class)
                           .orElse(null);

        if (counter == null) {
            return createSequence(sequence);
        }

        long result = counter.get(MongoSequenceCounter.NEXT_VALUE).asLong(-1);

        long numRowsChanged = mongo.update()
                                   .where(MongoSequenceCounter.NAME, sequence)
                                   .where(MongoSequenceCounter.NEXT_VALUE, result)
                                   .set(MongoSequenceCounter.NEXT_VALUE, result + 1)
                                   .executeForOne(MongoSequenceCounter.class)
                                   .getModifiedCount();

        if (numRowsChanged == 1) {
            // Nobody else changed the counter, so we can savely return the determined value...
            return result;
        }

        return null;
    }

    private Long createSequence(String sequence) {
        try {
            mongo.insert()
                 .set(MongoSequenceCounter.ID, keyGen.generateId())
                 .set(MongoSequenceCounter.NAME, sequence)
                 .set(MongoSequenceCounter.NEXT_VALUE, 2)
                 .into(MongoSequenceCounter.class);

            return 1L;
        } catch (MongoWriteException exception) {
            if (exception.getError().getCode() == 11000) {
                // This only happens if another thread / server inserted the entity already...
                Exceptions.ignore(exception);
                return null;
            } else {
                throw Exceptions.handle()
                                .to(Mongo.LOG)
                                .error(exception)
                                .withSystemErrorMessage("Failed to create the sequence: %s - %s (%s)", sequence)
                                .handle();
            }
        }
    }

    @Override
    public long peekNextValue(String sequence) {
        return mongo.find()
                    .where(MongoSequenceCounter.NAME, sequence)
                    .singleIn(MongoSequenceCounter.class)
                    .map(doc -> doc.get(MongoSequenceCounter.NEXT_VALUE).asLong(-1))
                    .orElse(1L);
    }

    @Override
    public void setNextValue(String sequence, long nextValue, boolean force) {
        if (mongo.find().where(MongoSequenceCounter.NAME, sequence).countIn(MongoSequenceCounter.class) > 0) {
            updateCounterValue(sequence, nextValue, force);
        } else {
            createSequenceWithValue(sequence, nextValue);
        }
    }

    private void createSequenceWithValue(String sequence, long nextValue) {
        mongo.insert()
             .set(MongoSequenceCounter.ID, keyGen.generateId())
             .set(MongoSequenceCounter.NAME, sequence)
             .set(MongoSequenceCounter.NEXT_VALUE, nextValue)
             .into(MongoSequenceCounter.class);
    }

    private void updateCounterValue(String sequence, long nextValue, boolean force) {
        Updater updater = mongo.update()
                               .where(MongoSequenceCounter.NAME, sequence)
                               .set(MongoSequenceCounter.NEXT_VALUE, nextValue);

        if (!force) {
            updater.where(QueryBuilder.FILTERS.lt(MongoSequenceCounter.NEXT_VALUE, nextValue));
        }

        UpdateResult updateResult = updater.executeForOne(MongoSequenceCounter.class);

        if (updateResult.getMatchedCount() != 1) {
            throw Exceptions.handle()
                            .to(Sequences.LOG)
                            .withSystemErrorMessage("Failed to specify the next value for sequence %s", sequence)
                            .handle();
        }
    }

    @Override
    public void collectKnownSequences(Consumer<String> nameConsumer) throws Exception {
        mongo.find()
             .orderByAsc(MongoSequenceCounter.NAME)
             .allIn(MongoSequenceCounter.class, doc -> nameConsumer.accept(doc.getString(MongoSequenceCounter.NAME)));
    }
}
