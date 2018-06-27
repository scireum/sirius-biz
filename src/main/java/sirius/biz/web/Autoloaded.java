/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.BaseEntity;
import sirius.web.http.WebContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fields marked with this annotation are auto filled from the given request, when calling {@link
 * BizController#load(WebContext, BaseEntity)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Autoloaded {

    /**
     * Controls the permissions required to be able to fill the field.
     * <p>
     * If the current user hasn't all of the given permissions, the value in the context will not be applied.
     *
     * @return the list of permissions required to be able to fill the annotated field
     */
    String[] permissions() default {};
}
