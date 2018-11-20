/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic


import sirius.kernel.BaseSpecification

import java.util.stream.Collectors

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

    def "splitting tokens works as intended"() {
        given:
        SearchableTestEntity e = new SearchableTestEntity()
        when:
        e.setTest(input)
        and:
        e.updateSearchField()
        then:
        Arrays.stream(e.getSearchField().split(" "))
              .filter({ t -> t.length() > 0 })
              .collect(Collectors.toList())
              .sort() == output
        where:
        input                        | output
        "max.mustermann@website.com" | ["com", "max", "max.mustermann", "max.mustermann@website.com", "mustermann", "website", "website.com"]
        "test-foobar"                | ["foobar", "test", "test-foobar"]
        "test123@bla-bar.foo"        | ["bar", "bla", "bla-bar.foo", "foo", "test", "test123", "test123@bla-bar.foo"]
    }

}
