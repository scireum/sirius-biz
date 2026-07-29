/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.health.HandledException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the [AddressData] class.
 *
 * The <tt>verifyXXX</tt> / <tt>validateXXX</tt> methods emit localized messages via NLS, hence the
 * [SiriusExtension] is required to boot the framework.
 */
@ExtendWith(SiriusExtension::class)
class AddressDataTest {

    private fun address(street: String? = null, zip: String? = null, city: String? = null): AddressData {
        return AddressData().apply {
            this.street = street
            this.zip = zip
            this.city = city
        }
    }

    @Test
    fun `toString combines all filled parts`() {
        assertEquals(
            "Museumstraße 1 70000 Stuttgart",
            address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").toString()
        )
    }

    @Test
    fun `toString omits empty parts`() {
        assertEquals("70000 Stuttgart", address(zip = "70000", city = "Stuttgart").toString())
        assertEquals("Stuttgart", address(city = "Stuttgart").toString())
        assertEquals("", address().toString())
    }

    @Test
    fun `isAnyFieldEmpty reflects whether at least one field is empty`() {
        assertFalse { address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").isAnyFieldEmpty }
        assertTrue { address(street = "Museumstraße 1", city = "Stuttgart").isAnyFieldEmpty }
        assertTrue { address().isAnyFieldEmpty }
    }

    @Test
    fun `areAllFieldsEmpty reflects whether all fields are empty`() {
        assertTrue { address().areAllFieldsEmpty() }
        assertFalse { address(city = "Stuttgart").areAllFieldsEmpty() }
        assertFalse { address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").areAllFieldsEmpty() }
    }

    @Test
    fun `isPartiallyFilled is only true for a partially filled address`() {
        assertTrue { address(city = "Stuttgart").isPartiallyFilled }
        assertFalse { address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").isPartiallyFilled }
        assertFalse { address().isPartiallyFilled }
    }

    @Test
    fun `clear resets all fields`() {
        val addressData = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        addressData.clear()
        assertNull(addressData.street)
        assertNull(addressData.zip)
        assertNull(addressData.city)
        assertTrue { addressData.areAllFieldsEmpty() }
    }

    @Test
    fun `verifyFullAddress accepts a fully filled address`() {
        address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").verifyFullAddress(null)
    }

    @Test
    fun `verifyFullAddress rejects an incomplete address`() {
        assertFailsWith<HandledException> { address(street = "Museumstraße 1", city = "Stuttgart").verifyFullAddress(null) }
        assertFailsWith<HandledException> { address().verifyFullAddress(null) }
    }

    @Test
    fun `validateFullAddress collects a message only for an incomplete address`() {
        val messages = mutableListOf<String>()

        address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").validateFullAddress(null) {
            messages.add(it)
        }
        assertTrue { messages.isEmpty() }

        address(street = "Museumstraße 1", city = "Stuttgart").validateFullAddress(null) { messages.add(it) }
        assertEquals(1, messages.size)
    }

    @Test
    fun `validateFullAddress uses the given field label in the message`() {
        val messages = mutableListOf<String>()

        address(street = "Museumstraße 1", city = "Stuttgart").validateFullAddress("Rechnungsadresse") {
            messages.add(it)
        }
        assertEquals(1, messages.size)
        assertTrue { messages[0].contains("Rechnungsadresse") }
    }

    @Test
    fun `verifyNonPartialAddress accepts a fully filled or a completely empty address`() {
        address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").verifyNonPartialAddress(null)
        address().verifyNonPartialAddress(null)
    }

    @Test
    fun `verifyNonPartialAddress rejects a partially filled address`() {
        assertFailsWith<HandledException> { address(city = "Stuttgart").verifyNonPartialAddress(null) }
    }

    @Test
    fun `validateNonPartialAddress collects a message only for a partially filled address`() {
        val messages = mutableListOf<String>()

        address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart").validateNonPartialAddress(null) {
            messages.add(it)
        }
        address().validateNonPartialAddress(null) { messages.add(it) }
        assertTrue { messages.isEmpty() }

        address(city = "Stuttgart").validateNonPartialAddress(null) { messages.add(it) }
        assertEquals(1, messages.size)
    }

    @Test
    fun `equals is true for equal addresses`() {
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        val other = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
    }

    @Test
    fun `equals is false for differing addresses`() {
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        assertNotEquals(one, address(street = "Museumstraße 2", zip = "70000", city = "Stuttgart"))
        assertNotEquals(one, address(street = "Museumstraße 1", zip = "12345", city = "Wo Auch Immer"))
        assertNotEquals(one, address(street = "Museumstraße 1", zip = "70000", city = "Berlin"))
    }

    @Test
    fun `equals handles null and foreign types`() {
        val one = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        assertFalse { one.equals(null) }
        assertFalse { one.equals("Museumstraße 1 70000 Stuttgart") }
    }

    @Test
    fun `getters return the values set via the setters`() {
        val addressData = address(street = "Museumstraße 1", zip = "70000", city = "Stuttgart")
        assertEquals("Museumstraße 1", addressData.street)
        assertEquals("70000", addressData.zip)
        assertEquals("Stuttgart", addressData.city)
    }
}
