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
 * Created by aha on 22.03.16.
 */
public class JournalData extends Composite {

    @Transient
    private volatile boolean silent;

    @Transient
    private Entity owner;

    public JournalData(Entity owner) {
        this.owner = owner;
    }

    @AfterSave
    protected void onSave() {
        if (silent || !Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_PROTOCOLS)) {
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
                JournalEntry entry = new JournalEntry();
                entry.setTod(LocalDateTime.now());
                entry.setChanges(changes.toString());
                entry.setTargetId(owner.getId());
                entry.setTargetName(owner.toString());
                entry.setTargetType(descriptor.getType().getName());
                entry.setSubsystem(TaskContext.get().getSystemString());
                entry.setUserId(UserContext.getCurrentUser().getUserId());
                entry.setUsername(UserContext.getCurrentUser().getUserName());
                oma.update(entry);
            }
        } catch (Throwable e) {
            Exceptions.handle(e);
        }
    }

    @AfterDelete
    protected void onDelete() {
        if (!silent && Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_PROTOCOLS)) {
            try {
                JournalEntry entry = new JournalEntry();
                entry.setTod(LocalDateTime.now());
                entry.setChanges("Entity has been deleted.");
                entry.setTargetId(owner.getId());
                entry.setTargetName(owner.toString());
                entry.setTargetType(owner.getDescriptor().getType().getName());
                entry.setSubsystem(TaskContext.get().getSystemString());
                entry.setUserId(UserContext.getCurrentUser().getUserId());
                entry.setUsername(UserContext.getCurrentUser().getUserName());
                oma.update(entry);
            } catch (Throwable e) {
                Exceptions.handle(e);
            }
        }
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

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
