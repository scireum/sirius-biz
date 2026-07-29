/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.util.Countries
import sirius.kernel.SiriusExtension
import sirius.kernel.async.CallContext
import sirius.kernel.health.HandledException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [InternationalAddressData] class.
 *
 * The <tt>countries</tt> and <tt>active-countries</tt> lookup tables are provided with config based test data
 * (see <tt>test.conf</tt>): "de" resolves to "Germany" / "Deutschland" and accepts a five-digit ZIP code,
 * "at" resolves to "Austria" / "Österreich" and accepts a four-digit ZIP code. The current language is pinned
 * to german (the configured default, see develop.conf) so that translations are asserted deterministically
 * regardless of the environment's default language.
 */
@ExtendWith(SiriusExtension::class)
class InternationalAddressDataTest {

    @BeforeEach
    fun pinLanguage() {
        CallContext.getCurrent().setLanguage("de")
    }

    private fun address(
        street: String? = null,
        zip: String? = null,
        city: String? = null,
        country: String? = null
    ): InternationalAddressData {
        return InternationalAddressData().apply {
            this.street = street
            this.zip = zip
            this.city = city
            country?.let { this.country.value = it }
        }
    }

    @Test
    fun `the default constructor uses the active countries lookup table`() {
        assertEquals(Countries.LOOKUP_TABLE_ACTIVE_COUNTRIES, InternationalAddressData().country.tableName)
    }

    @Test
    fun `a custom constructor uses the given lookup table`() {
        assertEquals("countries", InternationalAddressData("countries").country.tableName)
    }

    @Test
    fun `isAnyFieldEmpty is false only when street, zip, city and country are filled`() {
        assertFalse {
            address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de").isAnyFieldEmpty
        }
        assertTrue { address(zip = "70000", city = "Stuttgart", country = "de").isAnyFieldEmpty }
    }

    @Test
    fun `clear also resets the country`() {
        val addressData = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de")
        addressData.clear()
        assertNull(addressData.street)
        assertNull(addressData.zip)
        assertNull(addressData.city)
        assertNull(addressData.country.value)
    }

    @Test
    fun `getTranslatedCountry resolves the country in the current language`() {
        assertEquals("Deutschland", address(country = "de").translatedCountry)
    }

    @Test
    fun `getTranslatedCountry is null for an empty country`() {
        assertNull(address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").translatedCountry)
    }

    @Test
    fun `getTranslatedCountry returns an empty string for an unknown value`() {
        // A code that is not part of the lookup table resolves to an empty name (see ConfigLookupTable).
        assertEquals("", address(country = "xx").translatedCountry)
    }

    @Test
    fun `verifyCountry accepts a known or empty country`() {
        address(country = "de").verifyCountry(null)
        address().verifyCountry(null)
    }

    @Test
    fun `verifyCountry rejects an unknown country`() {
        assertFailsWith<HandledException> { address(country = "unknown").verifyCountry(null) }
    }

    @Test
    fun `validateCountry collects a message only for an unknown country`() {
        val messages = mutableListOf<String>()

        address(country = "de").validateCountry(null) { messages.add(it) }
        address().validateCountry(null) { messages.add(it) }
        assertTrue { messages.isEmpty() }

        address(country = "unknown").validateCountry(null) { messages.add(it) }
        assertEquals(1, messages.size)
    }

    @Test
    fun `verifyZip accepts a ZIP matching the country pattern`() {
        address(country = "de", zip = "70000").verifyZip(null)
        // A four-digit ZIP is valid for Austria...
        address(country = "at", zip = "1010").verifyZip(null)
        // ...an empty ZIP is always accepted...
        address(country = "de").verifyZip(null)
        // ...and so is any ZIP if no country is selected.
        address(zip = "not-a-zip").verifyZip(null)
    }

    @Test
    fun `verifyZip rejects a ZIP violating the country pattern`() {
        assertFailsWith<HandledException> { address(country = "de", zip = "ABC").verifyZip(null) }
        // A four-digit ZIP is too short for Germany.
        assertFailsWith<HandledException> { address(country = "de", zip = "1010").verifyZip(null) }
    }

    @Test
    fun `validateZIP collects a message only for an invalid ZIP`() {
        val messages = mutableListOf<String>()

        address(country = "de", zip = "70000").validateZIP(null) { messages.add(it) }
        assertTrue { messages.isEmpty() }

        address(country = "de", zip = "ABC").validateZIP(null) { messages.add(it) }
        assertEquals(1, messages.size)
    }

    @Test
    fun `equals is reflexive`() {
        // Note: equals() currently compares the country LookupValue by reference (Strings.areEqual falls back
        // to Objects.equals and LookupValue has no equals/hashCode), so two value-identical instances are NOT
        // considered equal - even though hashCode() is value based. See SIRI-1258. Hence, only reflexivity is
        // asserted here.
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de")
        assertEquals(one, one)
    }

    @Test
    fun `hashCode is based on the field values including the country`() {
        // Note: unlike equals(), hashCode() is value based (uses country.getValue()), so two value-identical
        // instances share a hash code. See SIRI-1258 for the resulting equals/hashCode inconsistency.
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de")
        val other = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de")
        assertEquals(one.hashCode(), other.hashCode())
    }

    @Test
    fun `equals is false for a differing country`() {
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de")
        val other = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "at")
        assertNotEquals(one, other)
    }

    @Test
    fun `equals is false compared to a plain address data`() {
        val international = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        val plain = AddressData().apply {
            street = "Museumstraße 1"
            zip = "70000"
            city = "Stuttgart"
        }
        assertNotEquals(international, plain)
    }

    @Test
    fun `equals handles null and foreign types`() {
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart", country = "de")
        assertFalse { one.equals(null) }
        assertFalse { one.equals("Museumstraße 1 70000 Stuttgart") }
    }
}
