/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class CodeListsSpec extends BaseSpecification {

    @Part
    private static CodeLists cl;

    def "get entries of a code list works"() {
        cl.getEntries("test");
    }

}
