/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer

import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.tenants.Tenant
import sirius.biz.tenants.TenantData
import sirius.biz.tenants.TenantsHelper
import sirius.biz.tenants.jdbc.SQLTenant
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val i1 = 200

/**
 * Tests the [Importer] class.
 */
@ExtendWith(SiriusExtension::class)
class ImporterTest {

    @BeforeEach
    fun setup() {
        importer = Importer("ImporterTest")
    }

    @AfterEach
    fun cleanup() {
        importer.close()
    }

    @Test
    fun `find existent tenant with importer`() {
        val tenant = TenantsHelper.getTestTenant()
        val context = ImportContext.create().set(SQLTenant.ID, tenant.id)
        val tenant1 = importer.findOrFail(SQLTenant::class.java, context)
        assertEquals(tenant.id, tenant1.id)
        assertEquals(tenant.tenantData.name, tenant1.tenantData.name)
    }

    @Test
    fun `fail on find non existent tenant with importer`() {
        val context = ImportContext.create().set(SQLTenant.ID, NON_EXISTENT_TENANT_ID)
        assertThrows<HandledException> {
            importer.findOrFail(SQLTenant::class.java, context)
        }
    }

    @Test
    fun `find and load tenant with importer`() {
        val tenant = TenantsHelper.getTestTenant()
        val newTenantName = "Test1234"
        val context = ImportContext.create().set(
                SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName
        )
        val tenant1 = importer.findAndLoad(SQLTenant::class.java, context.set(SQLTenant.ID, tenant.id))
        val tenant2 = importer.findAndLoad(SQLTenant::class.java, context.set(SQLTenant.ID, NON_EXISTENT_TENANT_ID))
        assertEquals(tenant.id, tenant1.id)
        assertEquals(newTenantName, tenant1.tenantData.name)
        assertEquals(newTenantName, tenant2.tenantData.name)
        assertTrue { tenant2.isNew }
        assertFalse { oma.select(SQLTenant::class.java).eq(SQLTenant.ID, NON_EXISTENT_TENANT_ID).first().isPresent }
    }

    @Test
    fun `findOrLoadAndCreate tenant with importer`() {
        val newTenantName = "Importer_Test123456471232"
        val context = ImportContext.create().set(
                SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName
        )
        val tenant = importer.findOrLoadAndCreate(SQLTenant::class.java, context)
        assertEquals(newTenantName, tenant.tenantData.name)
        assertFalse { tenant.isNew }
        assertTrue { oma.select(SQLTenant::class.java).eq(SQLTenant.ID, tenant.id).first().isPresent }
    }

    @Test
    fun `load parent tenant entity ref with importer`() {
        val newTenantName = "Importer_Test_Load"
        val tenant = TenantsHelper.getTestTenant()
        val context = ImportContext.create().set(
                SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName
        ).set(Tenant.PARENT, tenant)
        val newTenant = importer.load(SQLTenant::class.java, context)
        assertEquals(tenant.id, newTenant.parent.id)
    }

    @Test
    fun `createOrUpdateNow creating tenant`() {
        val newTenantName = "Importer_createOrUpdateNow_Test"
        val context = ImportContext.create().set(
                SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName
        )
        assertFalse {
            oma.select(
                    SQLTenant::class.java
            ).eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName).first().isPresent
        }
        val tenant = importer.load(SQLTenant::class.java, context)
        importer.createOrUpdateNow(tenant)
        assertFalse { tenant.isNew }
        assertTrue {
            oma.select(SQLTenant::class.java).eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName)
                    .first().isPresent
        }
    }

    @Test
    fun `createOrUpdateNow updating tenant`() {
        val newTenantName = "Importer_createOrUpdateNow_Test2"
        var context = ImportContext.create().set(
                SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName
        )
        var tenant = importer.load(SQLTenant::class.java, context)
        tenant = importer.createOrUpdateNow(tenant)
        context = ImportContext.create().set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName + "new")
                .set(SQLTenant.ID, tenant.getId())
        tenant = importer.tryFind(SQLTenant::class.java, context).orElse(null)
        tenant = importer.load(SQLTenant::class.java, context, tenant)
        importer.createOrUpdateNow(tenant)
        assertFalse {
            oma.select(
                    SQLTenant::class.java
            ).eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName).first().isPresent
        }
        assertTrue {
            oma.select(SQLTenant::class.java).eq(
                    SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName + "new"
            ).first().isPresent
        }
        assertEquals(
                tenant.id, oma.select(SQLTenant::class.java).eq(
                SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName + "new"
        ).queryFirst().id
        )
    }

    @Test
    fun `createOrUpdate in batch inserting tenants`() {
        val basicTenantName = "Importer_batchInsert"
        val tenantCountBefore = oma.select(SQLTenant::class.java).count()
        val tenantsToCreate = 200

        for (index in 0 until tenantsToCreate) {
            val context = ImportContext.create().set(
                    SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + index
            )
            val tenant = importer.load(SQLTenant::class.java, context)
            importer.createOrUpdateInBatch(tenant)
        }
        importer.getContext().getBatchContext().tryCommit()

        assertEquals(tenantCountBefore + tenantsToCreate, oma.select(SQLTenant::class.java).count())
    }

    @Test
    fun `createOrUpdate in batch updating tenants`() {
        val basicTenantName = "Importer_batchUpdate"
        val tenantsToUpdate: Long = 200

        for (index in 0 until tenantsToUpdate) {
            val context = ImportContext.create().set(
                    SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + index
            )
            val tenant = importer.load(SQLTenant::class.java, context)
            importer.createOrUpdateInBatch(tenant)
        }
        importer.getContext().getBatchContext().tryCommit()

        oma.select(SQLTenant::class.java).where(
                OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).contains(basicTenantName).build()
        ).iterateAll { tenant ->
            tenant.tenantData.name += "AFTERUPDATE"
            importer.createOrUpdateInBatch(tenant)
        }
        importer.getContext().getBatchContext().tryCommit()

        assertEquals(
                tenantsToUpdate, oma.select(SQLTenant::class.java).where(
                OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).contains("AFTERUPDATE").build()
        ).count()
        )
    }

    @Test
    fun `deleteNow is deleting tenants as expected`() {
        val basicTenantName = "Importer_delete"
        val tenantsToDelete: Long = 10

        // Create the tenants that should be deleted
        for (i in 0 until tenantsToDelete) {
            val context = ImportContext.create().set(
                    SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + i
            )
            val tenant = importer.load(SQLTenant::class.java, context)
            importer.createOrUpdateInBatch(tenant)
        }
        importer.getContext().getBatchContext().tryCommit()

        val tenantNameConstraint =
                OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).startsWith(basicTenantName).build()
        // Check that all tenants were created properly
        assertEquals(tenantsToDelete, oma.select(SQLTenant::class.java).where(tenantNameConstraint).count())

        // Delete the tenants via the importer
        oma.select(SQLTenant::class.java).where(tenantNameConstraint).iterateAll { entity ->
            importer.deleteNow(entity)
        }

        // Check that no tenants remain
        assertEquals(0, oma.select(SQLTenant::class.java).where(tenantNameConstraint).count())
    }

    @Test
    fun `deleteInBatch is deleting tenants as expected`() {
        val basicTenantName = "Importer_batchDelete"
        val tenantsToDelete: Long = 200

        // Create the tenants that should be deleted
        for (i in 0 until tenantsToDelete) {
            val context = ImportContext.create().set(
                    SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + i
            )
            val tenant = importer.load(SQLTenant::class.java, context)
            importer.createOrUpdateInBatch(tenant)
        }
        importer.getContext().getBatchContext().tryCommit()

        val tenantNameConstraint =
                OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).startsWith(basicTenantName).build()
        // Check that all tenants were created properly
        assertEquals(200, oma.select(SQLTenant::class.java).where(tenantNameConstraint).count())

        // Delete the tenants via the importer
        oma.select(SQLTenant::class.java).where(
                tenantNameConstraint
        ).iterateAll { entity ->
            importer.deleteInBatch(entity)
        }
        importer.getContext().getBatchContext().tryCommit()

        // Check that no tenants remain
        assertEquals(0, oma.select(SQLTenant::class.java).where(tenantNameConstraint).count())
    }

    companion object {
        private const val NON_EXISTENT_TENANT_ID = 100000L

        @Part
        @JvmStatic
        private lateinit var oma: OMA

        private lateinit var importer: Importer

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }
}
