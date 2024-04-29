/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import sirius.biz.process.Process;
import sirius.kernel.di.std.Named;
import sirius.web.http.WebContext;

/**
 * Defines the way a {@link ProcessOutput} is rendered in the UI.
 */
public interface ProcessOutputType extends Named {

    /**
     * Renders the given output for the given process.
     *
     * @param webContext     the request to respond to
     * @param process the process which contains the output
     * @param output  the output to render
     */
    void render(WebContext webContext, Process process, ProcessOutput output);

    /**
     * Defines the icon to be used in menus and lists when enumerating outputs of this type.
     *
     * @return the icon to be used for this type
     */
    String getIcon();
}
