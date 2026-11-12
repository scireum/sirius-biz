/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as not being relevant for the {@linkplain EntityDescriptor#isChanged(BaseEntity, Property) isChanged}
 * checks during imports.
 * <p>
 * If during an update, only fields marked as {@link IgnoreInImportChangedCheck} are changed, the entity won't be
 * updated.
 *
 * @see BaseImportHandler#isChanged(BaseEntity)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IgnoreInImportChangedCheck {
}
