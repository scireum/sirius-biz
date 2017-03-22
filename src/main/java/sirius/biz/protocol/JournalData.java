/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Entity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.Schema;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.Sirius;
import sirius.kernel.async.TaskContext;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import java.time.LocalDateTime;

/**
 * Provides a hook which records all changed fields into the system journal which can be embedded into other entities
 * or
 * mixins.
 * <p>
 * To skip a field, a {@link NoJournal} annotation can be placed. To skip a record entirely, {@link
 * #setSilent(boolean)}
 * can be called before the update or delete.
 */
public class JournalData extends Composite {

    @Transient
    private volatile boolean silent;

    @Transient
    private Entity owner;

    /**
     * Creates a new instance for the given entity.
     *
     * @param owner the entity which fields are to be recorded.
     */
    public JournalData(Entity owner) {
        this.owner = owner;
    }

    @AfterSave
    protected void onSave() {
        if (silent) {
            return;
        }

        try {
            StringBuilder changes = new StringBuilder();
            EntityDescriptor descriptor = owner.getDescriptor();
            for (Property p : descriptor.getProperties()) {
                if (p.getAnnotation(NoJournal.class) == null && descriptor.isChanged(owner, p)) {
                    changes.append(p.getName());
                    changes.append(": ");
                    changes.append(NLS.toUserString(p.getValue(owner), NLS.getDefaultLanguage()));
                    changes.append("\n");
                }
            }

            if (changes.length() > 0) {
                addJournalEntry(owner, changes.toString());
            }
        } catch (Exception e) {
            Exceptions.handle(e);
        }
    }

    /**
     * Adds an entry to the journal of the given entity.
     *
     * @param entity  the entity to write a journal entry for
     * @param changes the entry to add to the journal
     */
    public static void addJournalEntry(Entity entity, String changes) {
        if (!Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_PROTOCOLS)) {
            return;
        }
        JournalEntry entry = new JournalEntry();
        entry.setTod(LocalDateTime.now());
        entry.setChanges(changes);
        entry.setTargetId(entity.getId());
        entry.setTargetName(entity.toString());
        entry.setTargetType(Schema.getNameForType(entity.getClass()));
        entry.setSubsystem(TaskContext.get().getSystemString());
        entry.setUserId(UserContext.getCurrentUser().getUserId());
        entry.setUsername(UserContext.getCurrentUser().getUserName());
        oma.update(entry);
    }

    @AfterDelete
    protected void onDelete() {
        if (!silent) {
            try {
                addJournalEntry(owner, "Entity has been deleted.");
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
     * Determines if there are recordable changes in the referenced entity.
     *
     * @return <tt>true</tt> if at least one journaled field changed, <tt>false</tt> otherwise
     */
    public boolean hasJournaledChanges() {
        if (silent) {
            return false;
        }

        EntityDescriptor descriptor = owner.getDescriptor();
        for (Property p : descriptor.getProperties()) {
            if (p.getAnnotation(NoJournal.class) == null && descriptor.isChanged(owner, p)) {
                return true;
            }
        }

        return false;
    }
}
