/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.biz.protocol.NoJournal;
import sirius.biz.protocol.TraceData;
import sirius.biz.protocol.Traced;
import sirius.biz.sequences.Sequences;
import sirius.db.mixing.Mapping;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;

/**
 * Provides a base class for entities managed by a {@link sirius.biz.web.BizController}.
 * <p>
 * Provides built in {@link TraceData}
 */
public abstract class MongoBizEntity extends PrefixSearchableEntity implements Traced {

    @Part
    @Nullable
    protected static Sequences sequences;

    /**
     * Contains tracing data which records which user created and last edited the entity
     */
    private final TraceData trace = new TraceData();

    @Override
    public TraceData getTrace() {
        return trace;
    }

    /**
     * Checks whether any {@link Mapping} (except {@link NoJournal no journal data}) of the current {@link MongoBizEntity} changed.
     *
     * @return <tt>true</tt> if at least one column was changed, <tt>false</tt> otherwise.
     */
    public boolean isAnyColumnChangedExceptNoJournal() {
        return getDescriptor().getProperties()
                              .stream()
                              .anyMatch(property -> getDescriptor().isChanged(this, property)
                                                    && property.getAnnotation(NoJournal.class).isEmpty());
    }

    /**
     * Generates an id to use in {@link #ID} when creating a new entity.
     * <p>
     * By default this uses the same method as {@link MongoEntity#generateId()} to create the id,
     * but can be changed to use the {@link Sequences} framework to create sequential ids by annotating {@link SequenceId}.
     *
     * @return the generated id
     */
    @Override
    protected String generateId() {
        if (getClass().isAnnotationPresent(SequenceId.class)) {
            return generateSequenceId();
        }
        return super.generateId();
    }

    private String generateSequenceId() {
        if (sequences == null) {
            throw Exceptions.handle()
                            .to(Mongo.LOG)
                            .withSystemErrorMessage(
                                    "Can not generate sequential id for entity of type %s, as the framework %s is not active!",
                                    getTypeName(),
                                    Sequences.FRAMEWORK_SEQUENCES)
                            .handle();
        }
        return String.valueOf(sequences.generateId(getTypeName()));
    }
}
