/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.pagehelper

import sirius.biz.web.ElasticPageHelper
import sirius.db.es.Elastic
import sirius.kernel.BaseSpecification
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part
import sirius.web.http.WebContext

class ElasticPageHelperSpec extends BaseSpecification {

    @Part
    private static Elastic elastic

            def setupSpec() {
        ElasticPageHelperEntity entity1 = new ElasticPageHelperEntity()
        entity1.setBooleanField(true)
        entity1.setStringField("field-value-a")
        elastic.update(entity1)

        ElasticPageHelperEntity entity2 = new ElasticPageHelperEntity()
        entity2.setBooleanField(true)
        entity2.setStringField("field-value-b")
        elastic.update(entity2)

        ElasticPageHelperEntity entity3 = new ElasticPageHelperEntity()
        entity3.setBooleanField(true)
        entity3.setStringField("field-value-a")
        elastic.update(entity3)

        ElasticPageHelperEntity entity4 = new ElasticPageHelperEntity()
        entity4.setBooleanField(false)
        entity4.setStringField("field-value-b")
        elastic.update(entity4)

        ElasticPageHelperEntity entity5 = new ElasticPageHelperEntity()
        entity5.setBooleanField(false)
        entity5.setStringField("field-value-a")
        elastic.update(entity5)

        elastic.refresh(ElasticPageHelperEntity.class)
    }

    def "test boolean aggregation without value selected"() {
        given:
        ElasticPageHelper<ElasticPageHelperEntity> ph = ElasticPageHelper.withQuery(elastic.select(ElasticPageHelperEntity.class))
                WebContext wc = CallContext.getCurrent().get(WebContext.class)
                wc.queryString = [:]
        ph.withContext(wc)
        ph.addBooleanAggregation(ElasticPageHelperEntity.BOOLEAN_FIELD)
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
        facetItems.get(0).getKey() == "1"
        facetItems.get(0).getCount() == 3
        facetItems.get(0).isActive() == false
        facetItems.get(1).getKey() == "0"
        facetItems.get(1).getCount() == 2
        facetItems.get(1).isActive() == false
    }

    def "test boolean aggregation with value selected"() {
        given:
        ElasticPageHelper<ElasticPageHelperEntity> ph = ElasticPageHelper.withQuery(elastic.select(ElasticPageHelperEntity.class))
                WebContext wc = CallContext.getCurrent().get(WebContext.class)
                wc.queryString = ["booleanField": [1]]
        ph.withContext(wc)
        ph.addBooleanAggregation(ElasticPageHelperEntity.BOOLEAN_FIELD)
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
        facetItems.get(0).getKey() == "1"
        facetItems.get(0).getCount() == 3
        facetItems.get(0).isActive() == true
    }
}
