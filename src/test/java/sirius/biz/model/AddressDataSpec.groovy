/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model

import sirius.biz.tenants.Tenants
import sirius.kernel.di.std.Part
import sirius.mixing.OMA

/**
 * Created by gerhardhaufler on 04.03.16.
 */
class AddressDataSpec {

    @Part
    private static OMA oma;

    @Part
    private static Tenants tenants;


    def "AddressData can be persist"() {
        given:
        TenantsHelper.installTestTenant();
        when: AddressData c = new AddressData();
        c.setCountry("GB");
        c.setStreet("Millerstreet 10")
        c.setZip("99999");
        c.setCity("London");

        c.onSave()


        then:
        c.getCity() == "London";
        and:
        c.getCountry() == "GB";
        and:
        c.getStreet() == "Millerstreet 10"

        and:
        c.getZip()
    }

}
