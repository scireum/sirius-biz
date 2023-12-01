/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.pagehelper

import sirius.biz.web.MongoPageHelper
import sirius.db.mongo.Mango
import sirius.kernel.BaseSpecification
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part
import sirius.web.http.WebContext

class MongoPageHelperSpec extends BaseSpecification {

    @Part
    private static Mango mango

            def setupSpec() {
        MongoPageHelperEntity entity1 = new MongoPageHelperEntity()
        entity1.setBooleanField(true)
        entity1.setStringField("field-value-a")
        mango.update(entity1)

        MongoPageHelperEntity entity2 = new MongoPageHelperEntity()
        entity2.setBooleanField(true)
        entity2.setStringField("field-value-b")
        mango.update(entity2)

        MongoPageHelperEntity entity3 = new MongoPageHelperEntity()
        entity3.setBooleanField(true)
        entity3.setStringField("field-value-a")
        mango.update(entity3)

        MongoPageHelperEntity entity4 = new MongoPageHelperEntity()
        entity4.setBooleanField(false)
        entity4.setStringField("field-value-b")
        mango.update(entity4)

        MongoPageHelperEntity entity5 = new MongoPageHelperEntity()
        entity5.setBooleanField(false)
        entity5.setStringField("field-value-a")
        mango.update(entity5)
    }

    def "test boolean aggregation without value selected"() {
        given:
        MongoPageHelper<MongoPageHelperEntity> ph = MongoPageHelper.withQuery(mango.select(MongoPageHelperEntity.class))
                WebContext wc = CallContext.getCurrent().get(WebContext.class)
                wc.queryString = [:]
        ph.withContext(wc)
        ph.addBooleanAggregation(MongoPageHelperEntity.BOOLEAN_FIELD)
        when:
        def page = ph.asPage()
        then:
        page.hasFacets() == true
        page.getFacets().size() == 1
        and:
        def facet = page.getFacets().get(0)
        facet.getTitle() == "Bool Feld"
        facet.getName() == "booleanField"
        and:
        def facetItems = facet.getAllItems()
        facetItems.size() == 2
        facetItems.get(0).getKey() == "true"
        facetItems.get(0).getCount() == 3
        facetItems.get(0).isActive() == false
        facetItems.get(1).getKey() == "false"
        facetItems.get(1).getCount() == 2
        facetItems.get(1).isActive() == false
    }

    def "test boolean aggregation with value selected"() {
        given:
        MongoPageHelper<MongoPageHelperEntity> ph = MongoPageHelper.withQuery(mango.select(MongoPageHelperEntity.class))
                WebContext wc = CallContext.getCurrent().get(WebContext.class)
                wc.queryString = ["booleanField": [true]]
        ph.withContext(wc)
        ph.addBooleanAggregation(MongoPageHelperEntity.BOOLEAN_FIELD)
        when:
        def page = ph.asPage()
        then:
        page.hasFacets() == true
        page.getFacets().size() == 1
        and:
        def facet = page.getFacets().get(0)
        facet.getTitle() == "Bool Feld"
        facet.getName() == "booleanField"
        and:
        def facetItems = facet.getAllItems()
        facetItems.size() == 1
        facetItems.get(0).getKey() == "true"
        facetItems.get(0).getCount() == 3
        facetItems.get(0).isActive() == true
    }

    def "test term aggregation without value selected"() {
        given:
        MongoPageHelper<MongoPageHelperEntity> ph = MongoPageHelper.withQuery(mango.select(MongoPageHelperEntity.class))
                WebContext wc = CallContext.getCurrent().get(WebContext.class)
                wc.queryString = [:]
        ph.withContext(wc)
        ph.addTermAggregation(MongoPageHelperEntity.STRING_FIELD)
        when:
        def page = ph.asPage()
        then:
        page.hasFacets() == true
        page.getFacets().size() == 1
        and:
        def facet = page.getFacets().get(0)
        facet.getTitle() == "String Feld"
        facet.getName() == "stringField"
        and:
        def facetItems = facet.getAllItems()
        facetItems.size() == 2
        facetItems.get(0).getKey() == "field-value-a"
        facetItems.get(0).getCount() == 3
        facetItems.get(0).isActive() == false
        facetItems.get(1).getKey() == "field-value-b"
        facetItems.get(1).getCount() == 2
        facetItems.get(1).isActive() == false
    }

    def "test term aggregation with value selected"() {
        given:
        MongoPageHelper<MongoPageHelperEntity> ph = MongoPageHelper.withQuery(mango.select(MongoPageHelperEntity.class))
                WebContext wc = CallContext.getCurrent().get(WebContext.class)
                wc.queryString = ["stringField": ["field-value-a"]]
        ph.withContext(wc)
        ph.addTermAggregation(MongoPageHelperEntity.STRING_FIELD)
        when:
        def page = ph.asPage()
        then:
        page.hasFacets() == true
        page.getFacets().size() == 1
        and:
        def facet = page.getFacets().get(0)
        facet.getTitle() == "String Feld"
        facet.getName() == "stringField"
        and:
        def facetItems = facet.getAllItems()
        facetItems.size() == 1
        facetItems.get(0).getKey() == "field-value-a"
        facetItems.get(0).getCount() == 3
        facetItems.get(0).isActive() == true
    }
}
