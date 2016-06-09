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
        if (silent) {
            return;
        }

        EntityDescriptor descriptor = owner.getDescriptor();
        for (Property p : descriptor.getProperties()) {
            if (p.getAnnotation(NoJournal.class) == null && descriptor.isChanged(owner, p)) {
                System.out.println(p);
            }
        }
    }

    @AfterDelete
    protected void onDelete() {
        if (!silent) {
            System.out.println(owner);
        }
    }

    public boolean isSilent() {
        return silent;
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
