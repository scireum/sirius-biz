/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy

import sirius.biz.tenants.Tenant

import sirius.db.jdbc.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.Sirius
import sirius.kernel.di.std.Part
import sirius.web.security.UserContext

import java.time.Duration

/**
 * Provides tests for {@link VersionedFiles}.
 */
class VersionedFilesSpec extends BaseSpecification {
    @Part
    private static OMA oma

    @Part
    private static VersionedFiles versionedFiles

    private Tenant tenant


    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60))
    }

    def setup() {
        TenantsHelper.installTestTenant()
        tenant = UserContext.getCurrentUser().as(Tenant.class)
    }

    def "has version and has no version"() {
        given:
        String identifier = "versioned-file-hasVersion"
        assert !versionedFiles.hasVersions(tenant, identifier)
        when:
        versionedFiles.createVersion(tenant, identifier, "test content", "test comment")
        then:
        versionedFiles.hasVersions(tenant, identifier)
        !versionedFiles.hasVersions(tenant, "versioned-file-hasNoVersion")
    }

    def "createVersion"() {
        given:
        String identifier = "created-version"
        when:
        versionedFiles.createVersion(tenant, identifier, "test content", "test comment")
        then:
        versionedFiles.hasVersions(tenant, identifier)
        and:
        VersionedFile file = versionedFiles.getVersions(tenant, identifier).get(0)
        "test content" == versionedFiles.getContent(file).get(0)
        and:
        file.getComment() == "test comment"
    }

    def "delete older versions"() {
        given:
        String identifier = "created-version"
        int maxNumberOfVersions = Sirius.
                getSettings().
                get("storage.buckets.versioned-files.maxNumberOfVersions").
                asInt(1)
        when:
        for (int i = 0; i < maxNumberOfVersions * 2; i++) {
            versionedFiles.createVersion(tenant, identifier, "test content", "test comment " + i)
        }
        then:
        versionedFiles.hasVersions(tenant, identifier)
        and:
        versionedFiles.getVersions(tenant, identifier).size() == maxNumberOfVersions
    }

    def "null as version content does not cause an exception"() {
        given:
        String identifier = "null-version"
        when:
        versionedFiles.createVersion(tenant, identifier, null, null)
        then:
        versionedFiles.getVersions(tenant, identifier).size() == 1
    }

    def "reading out a null version returns an empty list as content"() {
        given:
        String identifier = "null-version-content"
        when:
        def nullContentFile = versionedFiles.createVersion(tenant, identifier, null, null)
        then:
        versionedFiles.getContent(nullContentFile).isEmpty()
    }
}
