/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo


import sirius.kernel.BaseSpecification

class PrefixSearchableEntitySpec extends BaseSpecification {

    def "tokenizing works"() {
        given:

        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        when:
        e.setTest("This is a test")
        e.setUnsearchableTest("Secret Content")
        and:
        e.updateSearchField()
        and:
        def tokens = e.getSearchPrefixes().data()
        then:
        tokens.contains("this")
        tokens.contains("is")
        tokens.contains("a")
        tokens.contains("test")
        !tokens.contains("secret")
        !tokens.contains("content")
    }

}
