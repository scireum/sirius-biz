/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.elastic.AutoBatchLoop;
import sirius.biz.web.BizController;
import sirius.db.es.Elastic;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.Sirius;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * Provides a hook which records all changed fields into the system journal which can be embedded into other entities
 * or mixins.
 * <p>
 * To skip a field, a {@link NoJournal} annotation can be placed. To skip a record entirely, {@link #setSilent(boolean)}
 * can be called before the update or delete.
 */
public class JournalData extends Composite {

    @Transient
    private volatile boolean silent;

    @Transient
    private volatile boolean batchLog;

    @Transient
    private final BaseEntity<?> owner;

    @Part
    private static AutoBatchLoop autoBatchLoop;

    /**
     * Creates a new instance for the given entity.
     *
     * @param owner the entity which fields are to be recorded.
     */
    public JournalData(BaseEntity<?> owner) {
        this.owner = owner;
    }

    @AfterSave
    protected void onSave() {
        if (silent || !Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_JOURNAL)) {
            return;
        }

        try {
            String changes = buildChangeJournal();

            if (changes.length() > 0) {
                addJournalEntry(owner, changes, batchLog);
            }
        } catch (Exception e) {
            Exceptions.handle(e);
        }
    }

    /**
     * Enumerates all properties being journaled.
     * <p>
     * These are essentially all properties which do not wear a {@link NoJournal}.
     *
     * @return a stream of all journaled properties
     */
    public Stream<Property> fetchJournaledProperties() {
        return owner.getDescriptor()
                    .getProperties()
                    .stream()
                    .filter(p -> p.getAnnotation(NoJournal.class).isEmpty());
    }

    /**
     * Enumerates all journaled properties which are changed.
     *
     * @return a stream of all journaled properties which are changed
     */
    public Stream<Property> fetchJournaledAndChangedProperties() {
        return fetchJournaledProperties().filter(p -> p.getDescriptor().isChanged(owner, p));
    }

    /**
     * Reports all changed properties as a string.
     * <p>
     * This will output one line per changed property like {@code old_value -&gt; new_value}.
     *
     * @return a string which lists all changed properties
     */
    public String buildChangeJournal() {
        StringBuilder changes = new StringBuilder();
        fetchJournaledAndChangedProperties().forEach(p -> {
            changes.append(p.getName());
            changes.append(": ");
            changes.append(NLS.toUserString(owner.getPersistedValue(p), NLS.getDefaultLanguage()));
            changes.append(" -> ");
            changes.append(NLS.toUserString(p.getValue(owner), NLS.getDefaultLanguage()));
            changes.append("\n");
        });

        return changes.toString();
    }

    /**
     * Adds an entry to the journal of the given entity.
     *
     * @param entity  the entity to write a journal entry for
     * @param changes the entry to add to the journal
     */
    public static void addJournalEntry(BaseEntity<?> entity, String changes) {
        addJournalEntry(entity, changes, false);
    }

    /**
     * Adds an entry to the journal of the given entity.
     *
     * @param targetType        the type of the entity under which the entity will be written
     * @param targetId          the id of the entity under which the entity will be written
     * @param contentIdentifier the name identifying the entity, which can differ from the owner entity
     * @param changes           the entry to add to the journal
     */
    public static void addJournalEntry(String targetType, String targetId, String contentIdentifier, String changes) {
        addJournalEntry(targetType, targetId, contentIdentifier, changes, false);
    }

    private static void addJournalEntry(@Nonnull BaseEntity<?> entity, String changes, boolean batchLog) {
        if (entity.isNew() || entity.wasCreated()) {
            return;
        }
        String targetId = String.valueOf(entity.getId());
        String targetType = Mixing.getNameForType(entity.getClass());
        addJournalEntry(targetType, targetId, Strings.apply("%s-%s", targetType, targetId), changes, batchLog);
    }

    private static void addJournalEntry(String targetType,
                                        String targetId,
                                        String contentIdentifier,
                                        String changes,
                                        boolean batchLog) {
        if (!Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_PROTOCOLS)) {
            return;
        }

        try {
            JournalEntry entry = new JournalEntry();
            entry.setTod(LocalDateTime.now());
            entry.setChanges(changes);
            entry.setTargetId(targetId);
            entry.setContentIdentifier(contentIdentifier);
            entry.setTargetType(targetType);
            entry.setSubsystem(TaskContext.get().getSystemString());
            entry.setUserId(UserContext.getCurrentUser().getUserId());
            entry.setUsername(UserContext.getCurrentUser().getProtocolUsername());

            if (batchLog && autoBatchLoop.insertAsync(entry)) {
                return;
            }

            elastic.update(entry);
        } catch (Exception e) {
            Exceptions.handle(Elastic.LOG, e);
        }
    }

    @AfterDelete
    protected void onDelete() {
        if (!silent) {
            try {
                addJournalEntry(owner, Strings.apply("Entity '%s' has been deleted.", owner.toString()), batchLog);
            } catch (Exception e) {
                Exceptions.handle(e);
            }
        }
    }

    /**
     * Determines if the next change should be skipped (not recorded).
     *
     * @return <tt>true</tt> if the next change should be skipped, <tt>false</tt> otherwise
     */
    public boolean isSilent() {
        return silent;
    }

    /**
     * Sets the skip flag.
     * <p>
     * Calling this with <tt>true</tt>, will skip all changes performed on the referenced entity instance.
     *
     * @param silent <tt>true</tt> to skip the recording of all changes on the referenced entity instance,
     *               <tt>false</tt> to re-enable.
     */
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /**
     * Encourages the framework to log this journal entry via a batch update.
     * <p>
     * By default, each journal entry is created and persisted in a synchronous operation.
     * However, for fast operations, like import jobs, this provides quite an overhead. Therefore,
     * this method can be used to group batch insert all journal entries via the {@link AutoBatchLoop}.
     * <p>
     * Note however, that this optimization comes with the risk of loosing updates, if the
     * batch loop is heavily overloaded or contains malformed entities.
     */
    public void enableBatchLog() {
        this.batchLog = true;
    }

    /**
     * Determines if there are recordable changes in the referenced entity.
     *
     * @return <tt>true</tt> if at least one journaled field changed, <tt>false</tt> otherwise
     */
    public boolean hasJournaledChanges() {
        if (silent) {
            return false;
        }

        return fetchJournaledAndChangedProperties().findAny().isPresent();
    }

    /**
     * Returns the URI shown in <tt>tracing.html.pasta</tt>.
     *
     * @return the URI which permits access to the journal of the attached entity (owner).
     */
    public String getProtocolUri() {
        if (owner.isNew()) {
            return "";
        }

        String type = Mixing.getNameForType(owner.getClass());
        String id = String.valueOf(owner.getId());

        return BizController.signLink("/system/protocol/" + type + "/" + id);
    }
}
