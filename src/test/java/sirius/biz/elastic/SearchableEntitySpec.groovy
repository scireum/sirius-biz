/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic


import sirius.kernel.BaseSpecification

class SearchableEntitySpec extends BaseSpecification {

    def "tokenizing works"() {
        given:

        SearchableTestEntity e = new SearchableTestEntity()
        when:
        e.setTest("This is a test")
        e.setUnsearchableTest("Secret Content")
        e.setSearchableContent(
                "email:test@test.local 12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
        and:
        e.updateSearchField()
        and:
        def tokens = new HashSet<>(Arrays.asList(e.getSearchField().split(" ")))
        then:
        tokens.contains("this")
        tokens.contains("test")
        tokens.contains("email")
        !tokens.contains("secret")
        !tokens.contains("content")
        tokens.contains("test")
        tokens.contains("local")
        !tokens.contains(
                "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
    }

}
