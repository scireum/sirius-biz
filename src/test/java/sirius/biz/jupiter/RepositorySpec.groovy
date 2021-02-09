/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

/**
 * Provides a simple test for the communication with the Jupiter repository.
 */
class RepositorySpec extends BaseSpecification {

    @Part
    private static Jupiter jupiter

    def "list works"() {
        when:
        def list = jupiter.getDefault().repository().list()
        then:
        list.size() == 2
    }

}
