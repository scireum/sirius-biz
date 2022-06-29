/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.kernel.di.std.Priorized;

import java.util.function.BiConsumer;

/**
 * Used to signal the usage of an open source technology.
 * <p>
 * This has to be {@link sirius.kernel.di.std.Register registered} and will then be picked up by the
 * {@link DashboardController} to be shown on the login page.
 */
public interface TechStackInfo extends Priorized {

    /**
     * Emits one or more technologies being used by the system.
     *
     * @param collector a collector being supplied with an image path and a link to the appropriate website
     */
    void fetchActiveTechnologies(BiConsumer<String, String> collector);
}
