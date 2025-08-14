/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.importer.Importer;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.scripting.ScriptableEvents;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

/**
 * Provides a base class for batch jobs which utilize an {@link Importer} to import data.
 */
public abstract class ImportJob extends BatchJob {

    /**
     * Contains the importer which can be used to import data
     */
    protected final Importer importer;

    @Part
    private static ScriptableEvents scriptableEvents;

    /**
     * Creates a new job for the given process context.
     *
     * @param process the process context in which the job is executed
     */
    protected ImportJob(ProcessContext process) {
        super(process);
        this.importer = new Importer(process.getTitle());
    }

    @Override
    protected void initializeEventDispatchers(boolean enabled) {
        super.initializeEventDispatchers(enabled);
        if (enabled) {
            importer.getContext().withScriptableEventHandler(eventHandler);
            eventHandler.handleEvent(new ImportJobStartedEvent(this, process));
        }
    }

    /**
     * Properly creates or maintains a reference to an entity with {@link BaseEntityRef#hasWriteOnceSemantics()} write-once semantic.
     * <p>
     * For new entities (owner), the given reference is initialized with the given target. For existing entities
     * it is verified, that the given reference points to the given target.
     * <p>
     * This method can also maintain references without a {@link BaseEntityRef#hasWriteOnceSemantics write-once semantic},
     * but this might indicate an inconsistent or invalid usage pattern and one should strongly consider using a reference
     * with {@link BaseEntityRef#hasWriteOnceSemantics write-once semantics}.
     *
     * @param owner  the entity which contains the reference
     * @param ref    the reference which is either to be filled or verified that it points to <tt>target</tt>
     * @param target the target the reference must point to
     * @param <E>    the generic type the parent being referenced
     * @param <I>    the type of the id column of E
     * @throws sirius.kernel.health.HandledException if the entities do no match
     * @see BaseEntityRef#hasWriteOnceSemantics
     */
    protected <I extends Serializable, E extends BaseEntity<I>> void setOrVerify(BaseEntity<?> owner,
                                                                                 BaseEntityRef<I, E> ref,
                                                                                 E target) {
        if (!Objects.equals(ref.getId(), target.getId())) {
            if (owner.isNew()) {
                ref.setValue(target);
            } else {
                throw Exceptions.createHandled()
                                .withNLSKey("ImportJob.invalidReference")
                                .set("owner", owner.getUniqueName())
                                .set("target", target.getUniqueName())
                                .set("actual", ref.getUniqueObjectName())
                                .handle();
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (importer.getContext().hasBatchContext()) {
                process.log(ProcessLog.info()
                                      .withMessage(importer.getContext().getBatchContext().toString())
                                      .asSystemMessage());
            }
            this.importer.close();
        } catch (IOException exception) {
            process.handle(exception);
        }
        super.close();
    }

    public Importer getImporter() {
        return importer;
    }
}
