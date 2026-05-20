/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo

import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import sirius.biz.importer.Importer
import sirius.biz.importer.ImporterContext
import sirius.biz.tenants.jdbc.SQLUserAccount
import sirius.biz.tenants.jdbc.SQLUserAccountImportHandler
import sirius.kernel.SiriusExtension
import kotlin.test.assertContains

/**
 * Tests the [MongoUserAccountImportHandler].
 */
@ExtendWith(SiriusExtension::class)
class SqlUserAccountImportHandlerTest {

    private lateinit var importer: Importer

    @BeforeEach
    fun initImporter() {
        importer = Importer("testImporter")
    }

    @AfterEach
    fun closeImporter() {
        importer.close()
    }

    @ParameterizedTest()
    @CsvSource(
        "trace_createdAt",
        "userAccountData_login_username",
        "userAccountData_email",
        "userAccountData_person_salutation",
        "userAccountData_person_title",
        "userAccountData_person_firstname",
        "userAccountData_person_lastname",
        "userAccountData_permissions_permissions",
        "userAccountData_login_accountLocked",
        "performance-flags",
        "userAccountData_login_lastSeen",
        "userAccountData_login_lastLogin",
        "userAccountData_login_numberOfLogins",
        "userAccountData_login_lastExternalLogin",
        "userAccountData_login_lastPasswordChange"
    )
    fun `default export mappings contain the expected columns`(expectedColumnName: String) {
        val factory = SQLUserAccountImportHandler.SQLUserAccountImportHandlerFactory()
        val sut = factory.create(SQLUserAccount::class.java, importer.context)

        val result = sut.getDefaultExportMapping()

        assertContains(
            result,
            expectedColumnName,
            "getDefaultExportMapping() does not contain column $expectedColumnName"
        )
    }
}
