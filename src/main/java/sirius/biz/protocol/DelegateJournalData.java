/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Provides a hook which records custom messages into the system journal which can be embedded into other entities
 * or mixins.
 * <p>
 * To skip a record entirely, {@link #setSilent(boolean)} can be called before the update or delete.
 */
public class DelegateJournalData extends Composite {

    @Transient
    private volatile boolean silent;

    @Transient
    private final BaseEntity<?> owner;

    @Transient
    private final Supplier<String> targetId;

    @Transient
    private final Supplier<String> targetType;

    @Transient
    private Supplier<String> changeMessageSupplier;

    @Transient
    private Supplier<String> deleteMessageSupplier;

    /**
     * Creates a new instance for the given id and type suppliers.
     *
     * @param owner      the entity which triggered the change or delete event.
     * @param targetType the supplier which delivers the target entity type under which the journal message will be created
     * @param targetId   the supplier which delivers the target id under which the journal message will be created
     */
    public DelegateJournalData(BaseEntity<?> owner, Supplier<String> targetType, Supplier<String> targetId) {
        this.owner = owner;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    /**
     * Creates a new instance for the given entity reference.
     *
     * @param owner     the entity which triggered the change or delete event.
     * @param entityRef the entity reference under which the journal message will be created
     */
    public DelegateJournalData(BaseEntity<?> owner, @Nonnull BaseEntityRef<?, ?> entityRef) {
        this(owner, entityRef::getIdAsString, () -> Mixing.getNameForType(entityRef.getType()));
    }

    /**
     * Defines the supplier responsible to deliver the message to write in the protocol upon entity changes.
     * <p>
     * The supplier is called automatically as an {@link AfterSave} action. If the supplier is not set
     * or delivers empty results, no protocol message is written.
     * <p>
     * The {@link EntityDescriptor#getLabel() label} of the entity will be automatically
     * preppend to the generated message.
     *
     * @param changeMessageSupplier the supplier of the journal message
     * @return the object itself for fluent calls.
     */
    public DelegateJournalData withChangeMessageSupplier(Supplier<String> changeMessageSupplier) {
        this.changeMessageSupplier = changeMessageSupplier;
        return this;
    }

    /**
     * Defines the supplier responsible to deliver the message to write in the protocol upon entity deletes.
     * <p>
     * The supplier is called automatically as an {@link AfterDelete} action. If the supplier is not set
     * or delivers empty results, no protocol message is written.
     * <p>
     * The {@link EntityDescriptor#getLabel() label} of the entity will be automatically
     * preppend to the generated message.
     *
     * @param deleteMessageSupplier the supplier of the journal message
     * @return the object itself for fluent calls.
     */
    public DelegateJournalData withDeleteMessageSupplier(Supplier<String> deleteMessageSupplier) {
        this.deleteMessageSupplier = deleteMessageSupplier;
        return this;
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

    @AfterSave
    protected void onSave() {
        createJournalEntry(changeMessageSupplier);
    }

    @AfterDelete
    protected void onDelete() {
        createJournalEntry(deleteMessageSupplier);
    }

    private void createJournalEntry(Supplier<String> messageSupplier) {
        if (silent || !Sirius.isFrameworkEnabled(Protocols.FRAMEWORK_JOURNAL) || messageSupplier == null) {
            return;
        }

        if (owner.isNew() || owner.wasCreated()) {
            return;
        }

        try {
            String message = messageSupplier.get();
            if (Strings.isFilled(message)) {
                JournalData.addJournalEntry(targetType.get(),
                                            targetId.get(),
                                            Strings.apply("%s-%s",
                                                          Mixing.getNameForType(owner.getClass()),
                                                          String.valueOf(owner.getId())),
                                            Strings.apply("%s - %s", owner.getDescriptor().getLabel(), message));
            }
        } catch (Exception e) {
            Exceptions.handle(e);
        }
    }
}
