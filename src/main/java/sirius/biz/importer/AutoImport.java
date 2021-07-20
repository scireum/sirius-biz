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
}
