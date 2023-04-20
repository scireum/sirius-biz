/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualFileTest {

    @Test
    void parseVirtualFilePathByQueryParameter() {
        String searchedParameterName = "unique-id";
        String parameterValue = "uniqueIdentifier";

        URI uri = URI.create("https://something.also.not.proving.content-disposition/cdn?"
                             + searchedParameterName
                             + "="
                             + parameterValue
                             + "&another-id=not-relevant");
        String path = VirtualFile.parsePathFromUrl(uri, ignored -> false, name -> name.equals(searchedParameterName));

        assertEquals(path, parameterValue);
    }
}
