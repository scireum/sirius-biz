/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import sirius.kernel.BaseSpecification
import sirius.web.security.UserContext
import sirius.web.security.UserInfo

/**
 * Created by aha on 30.11.15.
 */
class TenantsSpec extends BaseSpecification {

    def "installTestTenant works"() {
        when:
        TenantsHelper.installTestTenant();
        then:
        UserContext.get().getUser().hasPermission(UserInfo.PERMISSION_LOGGED_IN)
    }
}
