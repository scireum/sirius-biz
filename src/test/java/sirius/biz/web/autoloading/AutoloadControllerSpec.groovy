/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.autoloading

import sirius.db.mongo.Mango
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.web.http.TestRequest

class AutoloadControllerSpec extends BaseSpecification {

    @Part
    private static Mango mango

    def "test creation with autoload"() {
        when:
        def response = TestRequest.SAFEPOST("/auto-load-controller/new")
                                  .withParameter("stringField", "string1")
                                  .withParameter("intField", 42)
                                  .withParameter("listField", ["listItem1", "listItem2"])
                                  .execute()
        then:
        def id = response.getContentAsJson().path("id").asText(null)
        id != null
        and:
        AutoLoadEntity entity = mango.find(AutoLoadEntity.class, id).get()
        entity.getStringField() == "string1"
        entity.getIntField() == 42
        entity.getListField().size() == 2
        entity.getListField().data().get(0) == "listItem1"
        entity.getListField().data().get(1) == "listItem2"
    }

    def "test update with autoload"() {
        given:
        AutoLoadEntity entity = new AutoLoadEntity()
        entity.setStringField("string-not-autoloaded")
        entity.setIntField(1337)
        entity.getListField().modify().add("starterListItem")
        mango.update(entity)
        when:
        def response = TestRequest.SAFEPOST("/auto-load-controller/" + entity.getId())
                                  .withParameter("stringField", "string-autoloaded")
                                  .withParameter("intField", 1)
                                  .withParameter("listField", ["listItem3", "listItem4"])
                                  .execute()
        then:
        def id = response.getContentAsJson().path("id").asText(null)
        id != null
        and:
        AutoLoadEntity entity1 = mango.find(AutoLoadEntity.class, id).get()
        entity1.getStringField() == "string-autoloaded"
        entity1.getIntField() == 1
        entity1.getListField().size() == 2
        entity1.getListField().data().get(0) == "listItem3"
        entity1.getListField().data().get(1) == "listItem4"
    }
}
