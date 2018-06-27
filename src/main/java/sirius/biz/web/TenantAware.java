/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.health.Exceptions;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Base class which marks subclasses as aware of their tenant they belong to.
 */
public interface TenantAware {

    String getTenantAsString();

    /**
     * Asserts that the given object has the same tenant as this object.
     *
     * @param fieldLabel the field in which the referenced object would be stored - used to generate an appropriate
     *                   error message
     * @param other      the object to check
     */
    default void assertSameTenant(Supplier<String> fieldLabel, TenantAware other) {
        if (other != null && (!Objects.equals(other.getTenantAsString(), getTenantAsString()))) {
            throw Exceptions.createHandled()
                            .withNLSKey("TenantAware.invalidTenant")
                            .set("field", fieldLabel.get())
                            .handle();
        }
    }

    //TODO
    void setCurrentTenant();
}
