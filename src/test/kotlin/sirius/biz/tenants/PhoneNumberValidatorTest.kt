/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import sirius.db.mongo.Mango
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import kotlin.test.assertEquals

@ExtendWith(SiriusExtension::class)
class PhoneNumberValidatorTest {

    @ParameterizedTest
    @CsvSource(
            textBlock = """
            12; true
            789 - 12; true
            456/789 - 12; true
            123 456/789 - 12; true
            +49 123 456/789 - 12; true
            789-12; true
            789 -12; true
            789  -   12; true
            789- 12; true
            456/789; true
            456 / 789; true
            456/ 789; true
            456 /789; true
            123 456; true
            123 456 789; true
            + 49 123 456/789 - 12; false
            "1234 "; false
            1234/123/23 ; false
            1234-12-12 ; false
            +49+1 test ; false""", delimiter = ';'
    )
    fun testPhoneNumberValidation(number: String, shouldBeConsideredValid: Boolean) {
        val contactDataTestEntity = ContactDataTestEntity()
        contactDataTestEntity.contact.phone = number

        val messages = mango.validate(contactDataTestEntity)

        assertEquals(if (shouldBeConsideredValid) 0 else 1, messages.size)
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango
    }
}
