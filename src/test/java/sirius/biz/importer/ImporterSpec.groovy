package sirius.biz.importer

import sirius.biz.jdbc.tenants.Tenant
import sirius.biz.jdbc.tenants.TenantsHelper
import sirius.db.jdbc.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Context
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException

import java.time.Duration

class ImporterSpec extends BaseSpecification {

    @Part
    private static OMA oma

    private Importer importer

    private static final long NON_EXISTENT_TENANT_ID = 100000L

    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
    }

    def setup() {
        importer = new Importer()
    }

    def "find existent tenant with importer"() {
        when:
        Tenant tenant = TenantsHelper.getTestTenant()
        and:
        Context context = Context.create().set(Tenant.ID.getName(), tenant.getId())
        then:
        Tenant tenant1 = importer.findOrFail(Tenant.class, context)
        and:
        tenant.getId() == tenant1.getId()
        and:
        tenant.getName() == tenant1.getName()
    }

    def "fail on find non existent tenant with importer"() {
        when:
        Context context = Context.create().set(Tenant.ID.getName(), NON_EXISTENT_TENANT_ID)
        and:
        importer.findOrFail(Tenant.class, context)
        then:
        thrown HandledException.class
    }

    def "find or load tenant with importer"() {
        when:
        Tenant tenant = TenantsHelper.getTestTenant()
        String newTenantName = "Test1234"
        and:
        Context context = Context.create().set(Tenant.NAME.getName(), newTenantName)
        and:
        Tenant tenant1 = importer.findOrLoad(Tenant.class, context.set(Tenant.ID.getName(), tenant.getId()))
        Tenant tenant2 = importer.findOrLoad(Tenant.class, context.set(Tenant.ID.getName(), NON_EXISTENT_TENANT_ID))
        then:
        tenant1.getName() == tenant.getName()
        tenant1.getId() == tenant.getId()
        and:
        tenant2.getName() == newTenantName
        tenant2.isNew()
        and:
        !oma.select(Tenant.class).eq(Tenant.ID, NON_EXISTENT_TENANT_ID).first().isPresent()
    }

    def "findOrLoadAndCreate tenant with importer"() {
        when:
        String newTenantName = "Importer_Test123456471232"
        and:
        Context context = Context.create().set(Tenant.NAME.getName(), newTenantName)
        and:
        Tenant tenant = importer.findOrLoadAndCreate(Tenant.class, context)
        then:
        tenant.getName() == newTenantName
        !tenant.isNew()
        and:
        oma.select(Tenant.class).eq(Tenant.ID, tenant.getId()).first().isPresent()
    }

    def "load parent tenant entity ref with importer"() {
        when:
        String newTenantName = "Importer_Test_Load"
        and:
        Tenant tenant = TenantsHelper.getTestTenant()
        and:
        Context context = Context.create().
                set(Tenant.NAME.getName(), newTenantName).
                set(Tenant.PARENT.getName(), tenant)
        and:
        Tenant newTenant = importer.load(Tenant.class, context)
        then:
        newTenant.getParent().getId() == tenant.getId()
    }

    def "createOrUpdateNow creating tenant"() {
        given:
        String newTenantName = "Importer_createOrUpdateNow_Test"
        and:
        Context context = Context.create().set(Tenant.NAME.getName(), newTenantName)
        when:
        !oma.select(Tenant.class).eq(Tenant.NAME, newTenantName).first().isPresent()
        and:
        Tenant tenant = importer.load(Tenant.class, context)
        importer.createOrUpdateNow(tenant)
        then:
        !tenant.isNew()
        and:
        oma.select(Tenant.class).eq(Tenant.NAME, newTenantName).first().isPresent()
    }

    def "createOrUpdateNow updating tenant"() {
        given:
        String newTenantName = "Importer_createOrUpdateNow_Test2"
        and:
        Context context = Context.create().set(Tenant.NAME.getName(), newTenantName)
        and:
        Tenant tenant = importer.load(Tenant.class, context)
        tenant = importer.createOrUpdateNow(tenant)
        when:
        context = Context.create().set(Tenant.NAME.getName(), newTenantName + "new").set(Tenant.ID.getName(), tenant.getId())
        tenant = importer.tryFind(Tenant.class, context).orElse(null)
        and:
        tenant = importer.load(Tenant.class, context, tenant)
        importer.createOrUpdateNow(tenant)
        then:
        !oma.select(Tenant.class).eq(Tenant.NAME, newTenantName).first().isPresent()
        and:
        oma.select(Tenant.class).eq(Tenant.NAME, newTenantName + "new").first().isPresent()
        and:
        oma.select(Tenant.class).eq(Tenant.NAME, newTenantName + "new").queryFirst().getId() == tenant.getId()
    }

    def "createOrUpdate in batch inserting tenants"() {
        given:
        String basicTenantName = "Importer_batchInsert"
        and:
        long tenantCount = oma.select(Tenant.class).count()
        when:
        for (int i = 0; i < 200; i++) {
            Context context = Context.create().set(Tenant.NAME.getName(), basicTenantName + i)
            and:
            Tenant tenant = importer.load(Tenant.class, context)
            importer.createOrUpdateInBatch(tenant)
        }
        and:
        importer.close()
        then:
        tenantCount + 200 == oma.select(Tenant.class).count()
    }

    def "createOrUpdate in batch updating tenants"() {
        given:
        String basicTenantName = "Importer_batchUpdate"
        and:
        long tenantCount = oma.select(Tenant.class).count()
        and:
        for (int i = 0; i < 200; i++) {
            Context context = Context.create().set(Tenant.NAME.getName(), basicTenantName + i)
            Tenant tenant = importer.load(Tenant.class, context)
            importer.createOrUpdateInBatch(tenant)
        }
        importer.close()
        when:
        oma.select(Tenant.class).where(OMA.FILTERS.like(Tenant.NAME).contains(basicTenantName).build()).iterateAll { tenant ->
            tenant.setName(tenant.getName() + "AFTERUPDATE")
            importer.createOrUpdateInBatch(tenant)
        }
        importer.close()
        then:
        oma.select(Tenant.class).where(OMA.FILTERS.like(Tenant.NAME).contains("AFTERUPDATE").build()).count() == 200
    }
}
