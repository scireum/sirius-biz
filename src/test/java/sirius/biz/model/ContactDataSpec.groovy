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
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.mixing.OMA

/**
 * Created by gerhardhaufler on 04.03.16.
 */
class ContactDataSpec extends BaseSpecification {

    @Part
    private static OMA oma;

    @Part
    private static Tenants tenants;


    def "ContactData normalizeNumbers1 @beforeSave"() {
        given:
        TenantsHelper.installTestTenant();
        when:
        ContactData c = new ContactData();
        c.setEmail("  abc@defg.de  ");
        c.setPhone("  01234 / 123456");
        c.setFax(" +49 (01234) 12345-67");
        c.setMobile("  0151 99 123456")

        c.onSave()


        then:
        c.getEmail() == "abc@defg.de";
        and:
        c.getPhone() == "00491234123456";
        and:
        c.getFax() == "004912341234567";
        and:
        c.getMobile() == "004915199123456";
    }

    def "ContactData normalizeNumbers2 @beforeSave"() {
        given:
        TenantsHelper.installTestTenant();
        when:

        ContactData c1 = new ContactData();
        c1.setEmail("  defg.ikl@abc.com  ");
        c1.setPhone("  +0049 (0)7151 / 12345-678");

        c1.onSave()


        then:
        c1.getEmail() == "defg.ikl@abc.com";
        and:
        c1.getPhone() ==  "0049715112345678";

    }
}
