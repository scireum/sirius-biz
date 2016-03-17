/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model

import sirius.biz.tenants.Tenants
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.mixing.OMA

/**
 * Created by gerhardhaufler on 04.03.16.
 */
class PersonDataSpec extends BaseSpecification{

    @Part
    private static OMA oma;

    @Part
    private static Tenants tenants;


    def "ContactData test @beforeSave"() {
        given:

        TenantsHelper.installTestTenant();
        when:
        PersonData p1 = new PersonData();
        p1.setSalutation("Herr")
        p1.setFirstname("<Firstname1>");
        p1.setLastname("<Lastname1>");
        p1.setTitle("<Title1>");
        PersonData p2 = new PersonData();
        p2.setSalutation("Frau")
        p2.setFirstname("<Firstname2>");
        p2.setLastname("<Lastname2>");

        then:
        p1.getLastname() == "<Lastname1>";
        and:
        p1.getFirstname() == "<Firstname1>";
        and:
        p1.getAddressableName() == "Herr <Title1> <Lastname1>";
        and:
        p1.getGeehrt() == "geehrter";
        and:
        p1.getLetterSalutation() == "Sehr geehrter Herr <Title1> <Lastname1>,";
        and:
        p1.getName() == "Herr <Title1> <Firstname1> <Lastname1>";
        and:
        p1.getNameInvers() == "<Lastname1>, <Title1>, <Firstname1>, Herr";
        and:
        p1.getSalutation() == "Herr";
        and:
        p1.getTitle() == "<Title1>";
        and:
        p2.getLastname() == "<Lastname2>";
        and:
        p2.getFirstname() == "<Firstname2>";
        and:
        p2.getAddressableName() == "Frau <Lastname2>";
        and:
        p2.getGeehrt() == "geehrte";
        and:
        p2.getLetterSalutation() == "Sehr geehrte Frau <Lastname2>,";
        and:
        p2.getName() == "Frau <Firstname2> <Lastname2>";
        and:
        p2.getNameInvers() == "<Lastname2>, <Firstname2>, Frau";
        and:
        p2.getSalutation() == "Frau";
        and:
        p2.getTitle() == null;
    }

}
