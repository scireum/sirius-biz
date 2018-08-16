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
        tenant2.getId() == NON_EXISTENT_TENANT_ID
        and:
        !oma.select(Tenant.class).eq(Tenant.ID, NON_EXISTENT_TENANT_ID).first().isPresent()
    }
}
