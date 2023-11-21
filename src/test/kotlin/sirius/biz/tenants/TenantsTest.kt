/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import sirius.db.jdbc.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.web.security.UserContext
import sirius.web.security.UserInfo

import java.time.Duration

class TenantsSpec extends BaseSpecification {

    @Part
    private static OMA oma

            def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
    }

    def "installTestTenant works"() {
        when:
        TenantsHelper.installTestTenant()
        then:
        UserContext.get().getUser().hasPermission(UserInfo.PERMISSION_LOGGED_IN)
    }
}
