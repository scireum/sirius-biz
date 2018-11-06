/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster

import com.google.common.collect.ImmutableList
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Wait
import sirius.web.controller.Message
import spock.lang.Shared

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class RedisUserMessageCacheTest extends BaseSpecification {

    @Shared
    def cache = new RedisUserMessageCache<>()

    @Shared
    def messages = ImmutableList.of(Message.error("Test error message"))

    def setupSpec() {
        //Mock TTL to 1 Second
        Field field = RedisUserMessageCache.class.getDeclaredField("DEFAULT_TTL")
        field.setAccessible(true)

        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.setAccessible(true)
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)

        field.set(null, 1)
    }

    def "test put value and then get value works, but second get is null"() {
        given:
        String key = "key" + System.currentTimeMillis()
        when:
        cache.put(key, messages)
        then:
        List<Message> result = cache.getAndRemove(key)
        result.size() == 1
        result.get(0).getMessage() == "Test error message"
        result.get(0).getType() == Message.ERROR
        and:
        cache.getAndRemove(key) == null
    }

    def "get after the expire time returns null"() {
        given:
        String key = "key" + System.currentTimeMillis()
        when:
        cache.put(key, messages)
        Wait.seconds(1)
        then:
        cache.getAndRemove(key) == null
    }

}
