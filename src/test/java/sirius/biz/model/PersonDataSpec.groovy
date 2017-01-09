/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model

import sirius.biz.tenants.Tenants
import sirius.biz.tenants.TenantsHelper
import sirius.db.mixing.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.web.mails.Mails

import java.time.Duration

class PersonDataSpec extends BaseSpecification {

    @Part
    private static OMA oma

    @Part
    private static Mails mail

    @Part
    private static Tenants tenants

    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
    }

    def "ContactData test @beforeSave"() {
        given:
        TenantsHelper.installTestTenant()
        when:
        PersonData p1 = new PersonData()
        p1.setSalutation("Herr")
        p1.setFirstname("<Firstname1>")
        p1.setLastname("<Lastname1>")
        p1.setTitle("<Title1>")
        then:
        p1.getAddressableName() == "Herr <Title1> <Lastname1>"
        and:
        p1.getSalutation() == "Herr"
        and:
        p1.getTitle() == "<Title1>"
    }

}
