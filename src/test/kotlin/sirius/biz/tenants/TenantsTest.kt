/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.codelists.jdbc.SQLCodeList
import sirius.biz.codelists.jdbc.SQLCodeListEntry
import sirius.biz.tenants.PhoneNumberValidator
import sirius.biz.tenants.TenantsHelper
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.web.security.UserContext
import sirius.web.security.UserInfo
import java.time.Duration

/**
 * Tests the [SQLTenants].
 */
@ExtendWith(SiriusExtension::class)
class TenantsTest {

    companion object {
        @Part
        private lateinit var oma: OMA

        @BeforeAll
        @JvmStatic
        fun setup() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }

    @Test
    fun `installTestTenant works`() {
        TenantsHelper.installTestTenant()
        kotlin.test.assertTrue { UserContext.get().getUser().hasPermission(UserInfo.PERMISSION_LOGGED_IN) }
    }
}
