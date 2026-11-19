/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.model

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.tenants.TenantData
import sirius.biz.tenants.TenantsHelper
import sirius.biz.tenants.UserAccountData
import sirius.biz.tenants.jdbc.SQLTenant
import sirius.biz.tenants.jdbc.SQLUserAccount
import sirius.db.jdbc.OMA
import sirius.db.jdbc.SQLEntity
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Strings
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests the [CustomConfigs] class.
 */
@ExtendWith(SiriusExtension::class)
class CustomConfigsTest {

    private val createdEntities = mutableListOf<SQLEntity>()

    @Test
    fun `Saving an invalid tenant config is rejected`() {
        val tenant = createTenant()
        tenant.tenantData.configString = INVALID_CONFIG

        assertFailsWith<HandledException> { oma.update(tenant) }
    }

    @Test
    fun `Saving an invalid user config is rejected`() {
        val user = createUser()
        user.userAccountData.permissions.configString = INVALID_CONFIG

        assertFailsWith<HandledException> { oma.update(user) }
    }

    @Test
    fun `Saving a valid config works`() {
        val tenant = createTenant()
        tenant.tenantData.configString = VALID_CONFIG
        oma.update(tenant)

        val user = createUser()
        user.userAccountData.permissions.configString = VALID_CONFIG
        oma.update(user)

        assertEquals(VALID_CONFIG, oma.refreshOrFail(tenant).tenantData.configString)
        assertEquals(VALID_CONFIG, oma.refreshOrFail(user).userAccountData.permissions.configString)
    }

    @Test
    fun `An already stored invalid config does not block saving other changes`() {
        val tenant = createTenant()
        storeConfigWithoutValidation(tenant, INVALID_CONFIG)
        val user = createUser()
        storeConfigWithoutValidation(user, INVALID_CONFIG)

        val reloadedTenant = oma.refreshOrFail(tenant)
        reloadedTenant.tenantData.fullName = "Changed"
        oma.update(reloadedTenant)

        val reloadedUser = oma.refreshOrFail(user)
        reloadedUser.userAccountData.login.isAccountLocked = true
        oma.update(reloadedUser)
    }

    @AfterEach
    fun cleanup() {
        createdEntities.reversed().forEach { oma.forceDelete(it) }
        createdEntities.clear()
    }

    private fun createTenant(): SQLTenant {
        val tenant = SQLTenant()
        tenant.tenantData.name = "CustomConfigsTest-" + Strings.generateCode(8)
        oma.update(tenant)
        createdEntities.add(tenant)
        return tenant
    }

    private fun createUser(): SQLUserAccount {
        val user = SQLUserAccount()
        user.tenant.setValue(TenantsHelper.getTestTenant())
        user.userAccountData.login.username = "custom-configs-" + Strings.generateCode(8).lowercase()
        user.userAccountData.email = user.userAccountData.login.username + "@test.test"
        oma.update(user)
        createdEntities.add(user)
        return user
    }

    private fun storeConfigWithoutValidation(tenant: SQLTenant, config: String) {
        oma.updateStatement(SQLTenant::class.java)
            .set(SQLTenant.TENANT_DATA.inner(TenantData.CONFIG_STRING), config)
            .where(SQLTenant.ID, tenant.id)
            .executeUpdate()
    }

    private fun storeConfigWithoutValidation(user: SQLUserAccount, config: String) {
        oma.updateStatement(SQLUserAccount::class.java)
            .set(
                SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERMISSIONS)
                    .inner(PermissionData.CONFIG_STRING), config
            )
            .where(SQLUserAccount.ID, user.id)
            .executeUpdate()
    }

    companion object {
        /**
         * Rejected since com.typesafe:config 1.4.10, as the trailing semicolon is unquoted text after a list.
         */
        private const val INVALID_CONFIG = "x = [\"a\", \"b\"];"

        private const val VALID_CONFIG = "x = [\"a\", \"b\"]"

        @Part
        private lateinit var oma: OMA

        @BeforeAll
        @JvmStatic
        fun setup() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }
}
