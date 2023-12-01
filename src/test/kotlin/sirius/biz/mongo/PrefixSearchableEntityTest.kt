/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo

import sirius.db.mongo.Mango
import sirius.db.mongo.QueryBuilder
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class PrefixSearchableEntitySpec extends BaseSpecification {

    @Part
    private static Mango mango

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

    def "tokenizing string map works"() {
        given:

        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        when:
        e.getMap().put("color", "Indigo blue");
        e.getMap().put("shape", "Triangle");
        and:
        e.updateSearchField()
        and:
        def tokens = e.getSearchPrefixes().data()
        then:
        tokens.contains("color")
        tokens.contains("blue")
        tokens.contains("indigo")
        tokens.contains("indigo blue")
        tokens.contains("shape")
        tokens.contains("triangle")
    }

    def "tokenizing multi language works"() {
        given:
        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        when:
        e.getMultiLanguageText().addText("de", "Schmetterling")
        e.getMultiLanguageText().addText("en", "Nice butterfly")
        and:
        e.updateSearchField()
        and:
        def tokens = e.getSearchPrefixes().data()
        then:
        tokens.contains("schmetterling")
        tokens.contains("nice")
        tokens.contains("butterfly")
        tokens.contains("nice butterfly")
        !tokens.contains("de")
        !tokens.contains("en")
    }

    def "tokenizing string list works"() {
        given:
        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        when:
        e.getList().add("Grumpy Cat").add("Dog")
        and:
        e.updateSearchField()
        and:
        def tokens = e.getSearchPrefixes().data()
        then:
        tokens.contains("dog")
        tokens.contains("grumpy")
        tokens.contains("cat")
        tokens.contains("grumpy cat")
    }

    def "splitting with emails works"() {
        given:
        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        when:
        e.setTest(input)
        and:
        e.updateSearchField()
        and:
        List<String> tokens = e.getSearchPrefixes().modify().sort()
        output.sort()
        then:
        tokens == output
        where:
        input      | output
        "a.b@c.de" | ["a", "b", "c", "de", "a.b", "c.de", "a.b@c.de"]
        "a24@x.de" | ["a24", "a", "x", "de", "x.de", "a24@x.de"]
    }

    def "searching works"() {
        when:
        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        e.setTest("Some Test")
        mango.update(e)
        then:
        mango.select(PrefixSearchableTestEntity.class).
        where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "som")).first().isPresent()
                and:
                mango.select(PrefixSearchableTestEntity.class).
        where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "Test")).first().isPresent()
    }

}
