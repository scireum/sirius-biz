/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as part of an export for entities managed by a {@link BaseImportHandler}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Exportable {

    /**
     * Used to determine the column position in the created table (sorted ascending).
     *
     * @return the column position of this field
     */
    int priority() default 100;

    /**
     * Determines if this field should be contained in the default export.
     * <p>
     * If not, it still can be exported via a template file provided by the user.
     *
     * @return <tt>true</tt> to add this field to the default export, <tt>false</tt> otherwise
     */
    boolean autoExport() default true;

    /**
     * Determines if this field should be used as default representation if an entity reference is used in an export.
     *
     * @return <tt>true</tt> if this field value should be used to represent this entity in an export column
     * <tt>false</tt> otherwise. Note that multiple fields can wear this annotation but only the one with the lowest
     * {@link #priority()} will be used.
     */
    boolean defaultRepresentation() default false;

    /**
     * Controls the permissions required to be able to export the field.
     * <p>
     * If the current user hasn't all of the given permissions, this field will be skipped
     *
     * @return the list of permissions required to be able to export the annotated field
     */
    String[] permissions() default {};
}
