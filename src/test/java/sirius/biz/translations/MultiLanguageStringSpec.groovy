/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import sirius.kernel.BaseSpecification

class MultiLanguageStringSpec extends BaseSpecification {

    def "adding a text with null as language key throws IllegalArgumentException"() {
        given:
        MultiLanguageString mls = new MultiLanguageString()
        when:
        mls.addText(null, "null key")
        then:
        thrown(IllegalArgumentException)
    }

    def "adding a text with an empty String as language key throws IllegalArgumentException"() {
        given:
        MultiLanguageString mls = new MultiLanguageString()
        when:
        mls.addText("", "empty key")
        then:
        thrown(IllegalArgumentException)
    }

    def "adding a value set with null as language key throws IllegalArgumentException"() {
        given:
        MultiLanguageString mls = new MultiLanguageString()
        when:
        mls.addText("en", "some text")
        mls.addText("de", "irgendein text")
        Map<String, String> map = new HashMap<>()
        map.put("en", "some text")
        map.put(null, "null key")
        mls.setData(map)
        then:
        thrown(IllegalArgumentException)
    }

    def "adding a value set with an empty String as language key throws IllegalArgumentException"() {
        given:
        MultiLanguageString mls = new MultiLanguageString()
        when:
        mls.addText("en", "some text")
        mls.addText("de", "irgendein text")
        Map<String, String> map = new HashMap<>()
        map.put("en", "some text")
        map.put("", "empty key")
        mls.setData(map)
        then:
        thrown(IllegalArgumentException)
    }

}
