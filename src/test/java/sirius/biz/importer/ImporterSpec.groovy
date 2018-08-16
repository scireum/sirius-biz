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

    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
    }

    def "find existent tenant with importer"() {
        when:
        Importer importer = new Importer()
        and:
        Tenant tenant = TenantsHelper.getTestTenant()
        and:
        Context context = Context.create().set(Tenant.ID.getName(), tenant.getId())
        then:
        importer.findOrFail(Tenant.class, context)
    }

    def "fail on find non existent tenant with importer"() {
        when:
        Importer importer = new Importer()
        and:
        Context context = Context.create().set(Tenant.ID.getName(), 100000L)
        and:
        importer.findOrFail(Tenant.class, context)
        then:
        thrown HandledException.class
    }
}
