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

    def "searching works"() {
        when:
        PrefixSearchableTestEntity e = new PrefixSearchableTestEntity()
        e.setTest("Some Test")
        mango.update(e)
        and:
        print mango.select(PrefixSearchableTestEntity.class).
                      where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "som")).
                      explain()
        then:
        mango.select(PrefixSearchableTestEntity.class).
                where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "som")).first().isPresent()
        and:
        mango.select(PrefixSearchableTestEntity.class).
                where(QueryBuilder.FILTERS.prefix(PrefixSearchableEntity.SEARCH_PREFIXES, "Test")).first().isPresent()
    }

}
