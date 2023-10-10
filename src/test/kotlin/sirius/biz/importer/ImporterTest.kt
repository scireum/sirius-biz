/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer

class ImporterTest extends BaseSpecification {

    @Part
    private static OMA oma

            private Importer importer

    private static final long NON_EXISTENT_TENANT_ID = 100000L

    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
    }

    def setup() {
        importer = new Importer("ImporterSpec")
    }

    def cleanup() {
        importer.close()
    }

    def "find existent tenant with importer"() {
        when:
        SQLTenant tenant = TenantsHelper.getTestTenant()
        and:
        ImportContext context = ImportContext.create().set(SQLTenant.ID, tenant.getId())
        then:
        SQLTenant tenant1 = importer.findOrFail(SQLTenant.class, context)
        and:
        tenant.getId() == tenant1.getId()
        and:
        tenant.getTenantData().getName() == tenant1.getTenantData().getName()
    }

    def "fail on find non existent tenant with importer"() {
        when:
        ImportContext context = ImportContext.create().set(SQLTenant.ID, NON_EXISTENT_TENANT_ID)
        and:
        importer.findOrFail(SQLTenant.class, context)
        then:
        thrown HandledException.class
    }

    def "find and load tenant with importer"() {
        when:
        SQLTenant tenant = TenantsHelper.getTestTenant()
        String newTenantName = "Test1234"
        and:
        ImportContext context = ImportContext.create().set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName)
        and:
        Tenant tenant1 = importer.findAndLoad(SQLTenant.class, context.set(SQLTenant.ID, tenant.getId()))
        Tenant tenant2 = importer.findAndLoad(SQLTenant.class, context.set(SQLTenant.ID, NON_EXISTENT_TENANT_ID))
        then:
        tenant1.getTenantData().getName() == newTenantName
        tenant1.getId() == tenant.getId()
        and:
        tenant2.getTenantData().getName() == newTenantName
        tenant2.isNew()
        and:
        !oma.select(SQLTenant.class).eq(SQLTenant.ID, NON_EXISTENT_TENANT_ID).first().isPresent()
    }

    def "findOrLoadAndCreate tenant with importer"() {
        when:
        String newTenantName = "Importer_Test123456471232"
        and:
        ImportContext context = ImportContext.create().set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName)
        and:
        SQLTenant tenant = importer.findOrLoadAndCreate(SQLTenant.class, context)
        then:
        tenant.getTenantData().getName() == newTenantName
        !tenant.isNew()
        and:
        oma.select(SQLTenant.class).eq(SQLTenant.ID, tenant.getId()).first().isPresent()
    }

    def "load parent tenant entity ref with importer"() {
        when:
        String newTenantName = "Importer_Test_Load"
        and:
        SQLTenant tenant = TenantsHelper.getTestTenant()
        and:
        ImportContext context = ImportContext.create().
        set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName).
        set(Tenant.PARENT, tenant)
        and:
        SQLTenant newTenant = importer.load(SQLTenant.class, context)
        then:
        newTenant.getParent().getId() == tenant.getId()
    }

    def "createOrUpdateNow creating tenant"() {
        given:
        String newTenantName = "Importer_createOrUpdateNow_Test"
        and:
        ImportContext context = ImportContext.create().set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName)
        when:
        !oma.select(SQLTenant.class).
        eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName).
        first().
        isPresent()
                and:
                SQLTenant tenant = importer.load(SQLTenant.class, context)
                importer.createOrUpdateNow(tenant)
                then:
                !tenant.isNew()
                        and:
                        oma.select(SQLTenant.class).
                eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName).
                first().
                isPresent()
    }

    def "createOrUpdateNow updating tenant"() {
        given:
        String newTenantName = "Importer_createOrUpdateNow_Test2"
        and:
        ImportContext context = ImportContext.create().set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName)
        and:
        SQLTenant tenant = importer.load(SQLTenant.class, context)
        tenant = importer.createOrUpdateNow(tenant)
        when:
        context = ImportContext.create().
        set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName + "new").
        set(SQLTenant.ID, tenant.getId())
        tenant = importer.tryFind(SQLTenant.class, context).orElse(null)
        and:
        tenant = importer.load(SQLTenant.class, context, tenant)
        importer.createOrUpdateNow(tenant)
        then:
        !oma.select(SQLTenant.class).
        eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName).
        first().
        isPresent()
                and:
                oma.select(SQLTenant.class).
        eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName + "new").
        first().
        isPresent()
                and:
                oma.select(SQLTenant.class).
        eq(SQLTenant.TENANT_DATA.inner(TenantData.NAME), newTenantName + "new").
        queryFirst().
        getId() == tenant.getId()
    }

    def "createOrUpdate in batch inserting tenants"() {
        given:
        String basicTenantName = "Importer_batchInsert"
        and:
        long tenantCount = oma.select(SQLTenant.class).count()
        when:
        for (int i = 0; i < 200; i++) {
        ImportContext context = ImportContext.create().
        set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + i)
        and:
        SQLTenant tenant = importer.load(SQLTenant.class, context)
        importer.createOrUpdateInBatch(tenant)
    }
        and:
        importer.getContext().getBatchContext().tryCommit()
        then:
        tenantCount + 200 == oma.select(SQLTenant.class).count()
    }

    def "createOrUpdate in batch updating tenants"() {
        given:
        String basicTenantName = "Importer_batchUpdate"
        and:
        long tenantCount = oma.select(SQLTenant.class).count()
                and:
        for (int i = 0; i < 200; i++) {
        ImportContext context = ImportContext.create().
        set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + i)
        SQLTenant tenant = importer.load(SQLTenant.class, context)
        importer.createOrUpdateInBatch(tenant)
    }
        importer.getContext().getBatchContext().tryCommit()
        when:
        oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).contains(basicTenantName).build()).
        iterateAll { tenant ->
            tenant.getTenantData().setName(tenant.getTenantData().getName() + "AFTERUPDATE")
            importer.createOrUpdateInBatch(tenant)
        }
                importer.getContext().getBatchContext().tryCommit()
                then:
                oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).contains("AFTERUPDATE").build()).
        count() == 200
    }

    def "deleteNow"() {
        given:
        String basicTenantName = "Importer_delete"
        and:
        for (int i = 0; i < 10; i++) {
        ImportContext context = ImportContext.create().
        set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + i)
        SQLTenant tenant = importer.load(SQLTenant.class, context)
        importer.createOrUpdateInBatch(tenant)
    }
        importer.getContext().getBatchContext().tryCommit()
        and:
        oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).
        startsWith(basicTenantName).
        build()).
        count() == 10
                when:
        oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).
        startsWith(basicTenantName).
        build()).
        iterateAll() { entity ->
            importer.deleteNow(entity)
        }
                then:
                oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).
        startsWith(basicTenantName).
        build()).
        count() == 0
    }

    def "deleteInBatch"() {
        given:
        String basicTenantName = "Importer_batchDelete"
        and:
        for (int i = 0; i < 200; i++) {
        ImportContext context = ImportContext.create().
        set(SQLTenant.TENANT_DATA.inner(TenantData.NAME), basicTenantName + i)
        Tenant tenant = importer.load(SQLTenant.class, context)
        importer.createOrUpdateInBatch(tenant)
    }
        importer.getContext().getBatchContext().tryCommit()
        and:
        oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).
        startsWith(basicTenantName).
        build()).count() == 200
                when:
        oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).
        startsWith(basicTenantName).
        build()).
        iterateAll() { entity ->
            importer.deleteInBatch(entity)
        }
                and:
                importer.getContext().getBatchContext().tryCommit()
                then:
                oma.select(SQLTenant.class).
        where(OMA.FILTERS.like(SQLTenant.TENANT_DATA.inner(TenantData.NAME)).
        startsWith(basicTenantName).
        build()).
        count() == 0
    }
}
