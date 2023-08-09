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
 * Marks fields which are automatically imported by subclasses of {@link BaseImportHandler}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoImport {

    /**
     * This enum describes whether a property is {@link #REQUIRED} or {@link #OPTIONAL}. In case of {@link #AUTO_DETECT} the framework
     * makes a reasonable guess.
     */
    enum RequiredStatus {
        REQUIRED, OPTIONAL, AUTO_DETECT
    }

    /**
     * Controls the permissions required to be able to import the field.
     * <p>
     * If the current user hasn't all of the given permissions, this field will be skipped
     *
     * @return the list of permissions required to be able to import the annotated field
     */
    String[] permissions() default {};

    /**
     * Hides the field in the job documentation
     *
     * @return whether the field is hidden in the job documentation
     */
    boolean hidden() default false;

    /**
     * Marks the field as required in the job documentation.
     * <p>
     * In most cases, the framework logic will work this out by itself. Use this flag if it doesn't.
     *
     * @return true if the field is required in the import
     */
    RequiredStatus value() default RequiredStatus.AUTO_DETECT;
}
