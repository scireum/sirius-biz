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
import sirius.db.mixing.Mixing
import sirius.db.mongo.Mango
import sirius.db.mongo.Mongo
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Strings
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests the [CustomConfigs] class.
 */
@ExtendWith(SiriusExtension::class)
class CustomConfigsTest {

    private val createdEntities = mutableListOf<SQLEntity>()

    private val createdMongoEntities = mutableListOf<MongoCustomConfigTestEntity>()

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

    @Test
    fun `Stored invalid configs are reported`() {
        val tenant = createTenant()
        storeConfigWithoutValidation(tenant, INVALID_CONFIG)
        val user = createUser()
        storeConfigWithoutValidation(user, INVALID_CONFIG)

        val invalidConfigs = mutableListOf<CustomConfigs.InvalidCustomConfig>()
        mixing.descriptors.forEach { descriptor ->
            CustomConfigs.findConfigProperties(descriptor).forEach { property ->
                CustomConfigs.checkStoredConfigs(descriptor, property) { invalidConfigs.add(it) }
            }
        }

        val invalidTenantConfig = invalidConfigs.find { it.entity.uniqueName == tenant.uniqueName }
        assertNotNull(invalidTenantConfig)
        assertEquals("tenantData_configString", invalidTenantConfig.property.name)

        val invalidUserConfig = invalidConfigs.find { it.entity.uniqueName == user.uniqueName }
        assertNotNull(invalidUserConfig)
        assertEquals("userAccountData_permissions_configString", invalidUserConfig.property.name)
    }

    @Test
    fun `Saving an invalid config in a Mongo entity is rejected`() {
        val entity = MongoCustomConfigTestEntity()
        entity.permissions.configString = INVALID_CONFIG

        assertFailsWith<HandledException> { mango.update(entity) }
    }

    @Test
    fun `Stored invalid configs in Mongo entities are reported`() {
        val invalidEntity = createMongoEntity()
        storeConfigWithoutValidation(invalidEntity, INVALID_CONFIG)
        val validEntity = createMongoEntity()
        validEntity.permissions.configString = VALID_CONFIG
        mango.update(validEntity)

        val invalidConfigs = mutableListOf<CustomConfigs.InvalidCustomConfig>()
        val checkedConfigs = checkMongoConfigs { invalidConfigs.add(it) }

        assertEquals(2L, checkedConfigs)
        assertEquals(listOf(invalidEntity.id), invalidConfigs.map { it.entity.idAsString })
        assertEquals("permissions_configString", invalidConfigs.single().property.name)
    }

    @Test
    fun `Empty configs in Mongo entities are not checked`() {
        val entity = createMongoEntity()
        storeConfigWithoutValidation(entity, "")

        val invalidConfigs = mutableListOf<CustomConfigs.InvalidCustomConfig>()
        val checkedConfigs = checkMongoConfigs { invalidConfigs.add(it) }

        assertEquals(0L, checkedConfigs)
        assertEquals(emptyList(), invalidConfigs)
    }

    @AfterEach
    fun cleanup() {
        createdEntities.reversed().forEach { oma.forceDelete(it) }
        createdEntities.clear()
        createdMongoEntities.forEach { mango.forceDelete(it) }
        createdMongoEntities.clear()
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

    private fun createMongoEntity(): MongoCustomConfigTestEntity {
        val entity = MongoCustomConfigTestEntity()
        mango.update(entity)
        createdMongoEntities.add(entity)
        return entity
    }

    private fun storeConfigWithoutValidation(entity: MongoCustomConfigTestEntity, config: String) {
        mongo.update()
            .set(MongoCustomConfigTestEntity.PERMISSIONS.inner(PermissionData.CONFIG_STRING), config)
            .executeFor(entity)
    }

    private fun checkMongoConfigs(invalidConsumer: (CustomConfigs.InvalidCustomConfig) -> Unit): Long {
        val descriptor = mixing.getDescriptor(MongoCustomConfigTestEntity::class.java)
        val property = CustomConfigs.findConfigProperties(descriptor).toList().single()
        return CustomConfigs.checkStoredConfigs(descriptor, property, invalidConsumer)
    }

    companion object {
        /**
         * Rejected since com.typesafe:config 1.4.10, as the trailing semicolon is unquoted text after a list.
         */
        private const val INVALID_CONFIG = "x = [\"a\", \"b\"];"

        private const val VALID_CONFIG = "x = [\"a\", \"b\"]"

        @Part
        private lateinit var oma: OMA

        @Part
        private lateinit var mixing: Mixing

        @Part
        private lateinit var mango: Mango

        @Part
        private lateinit var mongo: Mongo

        @BeforeAll
        @JvmStatic
        fun setup() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }
}
