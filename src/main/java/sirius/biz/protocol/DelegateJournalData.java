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
import sirius.db.mixing.Property;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Provides a hook which records custom messages into the system journal which can be embedded into other entities
 * or mixins.
 * <p>
 * To skip a record entirely, {@link #setSilent(boolean)} can be called before the update or delete.
 */
public class DelegateJournalData extends Composite {

    @Part
    private static Mixing mixing;

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

    @Transient
    private Supplier<String> ownerKeySupplier;

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
        this(owner, () -> Mixing.getNameForType(entityRef.getType()), entityRef::getIdAsString);
    }

    /**
     * Defines the supplier responsible to deliver the message to write in the protocol upon entity changes.
     * <p>
     * The supplier is called automatically as an {@link AfterSave} action. If the supplier is not set
     * or delivers empty results, no protocol message is written.
     * <p>
     * The {@link EntityDescriptor#getLabel() label} of the entity will be automatically
     * prepend to the generated message.
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
     * prepend to the generated message.
     *
     * @param deleteMessageSupplier the supplier of the journal message
     * @return the object itself for fluent calls.
     */
    public DelegateJournalData withDeleteMessageSupplier(Supplier<String> deleteMessageSupplier) {
        this.deleteMessageSupplier = deleteMessageSupplier;
        return this;
    }

    /**
     * Defines the supplier responsible to deliver an identification text for the entity.
     * <p>
     * This is usually made of key-fields which uniquely identify the entity (without its parent).
     *
     * @param ownerKeySupplier the supplier of the key
     * @return the object itself for fluent calls
     */
    public DelegateJournalData withKeySupplier(Supplier<String> ownerKeySupplier) {
        this.ownerKeySupplier = ownerKeySupplier;
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

    /**
     * Adds an entry to the journal of the given entity.
     *
     * @param targetType             the type of the entity under which the journal entity will be written
     * @param targetId               the id of the entity under which the journal entity will be written
     * @param contentIdentifierClass the type of the actual entity which was changed
     * @param contentIdentifierId    the id of the actual entity which was changed
     * @param changes                the entry to add to the journal, automatically appended by the class's label
     */
    public static void createJournalEntry(String targetType,
                                          String targetId,
                                          Class<?> contentIdentifierClass,
                                          String contentIdentifierId,
                                          String changes) {
        JournalData.addJournalEntry(targetType,
                                    targetId,
                                    Strings.apply("%s-%s",
                                                  Mixing.getNameForType(contentIdentifierClass),
                                                  contentIdentifierId),
                                    Strings.apply("%s - %s",
                                                  mixing.getDescriptor(contentIdentifierClass).getLabel(),
                                                  changes));
    }

    @AfterSave
    protected void onSave() {
        if (changeMessageSupplier != null) {
            createJournalEntry(changeMessageSupplier);
        } else {
            createJournalEntry(this::buildChangeJournal);
        }
    }

    @AfterDelete
    protected void onDelete() {
        if (deleteMessageSupplier != null) {
            createJournalEntry(deleteMessageSupplier);
        } else {
            createJournalEntry(this::buildDeleteJournal);
        }
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
                createJournalEntry(targetType.get(), targetId.get(), owner.getClass(), owner.getIdAsString(), message);
            }
        } catch (Exception e) {
            Exceptions.handle(e);
        }
    }

    private Stream<Property> fetchJournaledProperties() {
        return owner.getDescriptor()
                    .getProperties()
                    .stream()
                    .filter(property -> !property.getAnnotation(NoJournal.class).isPresent());
    }

    private String buildChangeJournal() {
        if (ownerKeySupplier == null) {
            return null;
        }

        Monoflop changesFound = Monoflop.create();
        StringBuilder changes = new StringBuilder(ownerKeySupplier.get());
        changes.append("\n");
        fetchJournaledProperties().filter(property -> property.getDescriptor().isChanged(owner, property))
                                  .forEach(property -> {
                                      changesFound.toggle();

                                      changes.append("- ");
                                      changes.append(property.getName());
                                      changes.append(": ");
                                      changes.append(NLS.toUserString(owner.getPersistedValue(property),
                                                                      NLS.getDefaultLanguage()));
                                      changes.append(" -> ");
                                      changes.append(NLS.toUserString(property.getValue(owner),
                                                                      NLS.getDefaultLanguage()));
                                      changes.append("\n");
                                  });

        if (changesFound.isToggled()) {
            return changes.toString();
        } else {
            return null;
        }
    }

    private String buildDeleteJournal() {
        if (ownerKeySupplier == null) {
            return null;
        }

        StringBuilder changes = new StringBuilder("Deleted: " + ownerKeySupplier.get());
        changes.append("\n");
        fetchJournaledProperties().forEach(property -> {
            changes.append("- ");
            changes.append(property.getName());
            changes.append(": ");
            changes.append(NLS.toUserString(owner.getPersistedValue(property), NLS.getDefaultLanguage()));
            changes.append("\n");
        });

        return changes.toString();
    }
}
