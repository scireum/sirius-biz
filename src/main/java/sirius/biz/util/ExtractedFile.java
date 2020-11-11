/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.kernel.commons.Amount;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;

public interface ExtractedFile {

    InputStream openInputStream() throws IOException;

    void writeDataTo(OutputStream out) throws IOException;

    long size();

    LocalDateTime lastModified();

    String getFilePath();

    Amount getProgressInPercent();

}
