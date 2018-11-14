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

public interface ProcessOutputType extends Named {

    void render(WebContext ctx, Process process, ProcessOutput output);

    String getIcon();
}
