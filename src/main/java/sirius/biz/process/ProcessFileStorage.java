/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.web.http.WebContext;

import java.io.File;

public interface ProcessFileStorage {

    ProcessFile upload(Process process, String filename, File data);

    File download(Process process, ProcessFile file);

    void serve(WebContext request, Process process, ProcessFile file);

    void delete(Process process, ProcessFile file);
}
