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
 * Tests the [PersonData] class.
 *
 * The <tt>salutations</tt> lookup table is provided with config based test data (see <tt>test.conf</tt>),
 * so that the salutation "SIR" resolves to "Sir" (english) resp. "Herr" (german). The current language is
 * pinned to german (the configured default, see develop.conf) so that translations are asserted
 * deterministically regardless of the environment's default language.
 */
@ExtendWith(SiriusExtension::class)
class PersonDataTest {

    @BeforeEach
    fun pinLanguage() {
        CallContext.getCurrent().setLanguage("de")
    }

    private fun person(
        title: String? = null,
        salutation: String? = null,
        firstname: String? = null,
        lastname: String? = null
    ): PersonData {
        return PersonData().apply {
            this.title = title
            this.firstname = firstname
            this.lastname = lastname
            salutation?.let { this.salutation.value = it }
        }
    }

    @Test
    fun `getShortName combines firstname and lastname`() {
        assertEquals("Skip Baker", person(firstname = "Skip", lastname = "Baker").shortName)
    }

    @Test
    fun `getShortName returns only the lastname if no firstname is present`() {
        assertEquals("Baker", person(lastname = "Baker").shortName)
    }

    @Test
    fun `getShortName is empty if no lastname is present`() {
        assertEquals("", person(firstname = "Skip").shortName)
        assertEquals("", person().shortName)
    }

    @Test
    fun `getInitials returns up to two uppercase initials`() {
        assertEquals("SB", person(firstname = "skip", lastname = "baker").initials)
    }

    @Test
    fun `getInitials handles partially filled names`() {
        assertEquals("S", person(firstname = "Skip").initials)
        assertEquals("B", person(lastname = "Baker").initials)
        assertEquals("", person().initials)
    }

    @Test
    fun `getTranslatedSalutation resolves the salutation in the current and given language`() {
        val personData = person(salutation = "SIR")
        assertEquals("Herr", personData.translatedSalutation)
        assertEquals("Herr", personData.getTranslatedSalutation("de"))
        assertEquals("Sir", personData.getTranslatedSalutation("en"))
    }

    @Test
    fun `getTranslatedSalutation is null for an empty salutation`() {
        assertNull(person().translatedSalutation)
    }

    @Test
    fun `getTranslatedSalutation returns an empty string for an unknown value`() {
        // A code that is not part of the lookup table resolves to an empty name (see ConfigLookupTable).
        assertEquals("", person(salutation = "custom").translatedSalutation)
    }

    @Test
    fun `getAddressableName is empty if no lastname is present`() {
        assertEquals("", person(salutation = "SIR", title = "Prof.", firstname = "Skip").addressableName)
    }

    @Test
    fun `getAddressableName combines the resolved salutation, title and lastname but omits the firstname`() {
        assertEquals("Prof. Baker", person(title = "Prof.", firstname = "Skip", lastname = "Baker").addressableName)
        assertEquals(
            "Herr Prof. Baker",
            person(salutation = "SIR", title = "Prof.", firstname = "Skip", lastname = "Baker").addressableName
        )
    }

    @Test
    fun `getAddressableName translates the salutation into the given language`() {
        assertEquals(
            "Sir Prof. Baker",
            person(salutation = "SIR", title = "Prof.", firstname = "Skip", lastname = "Baker").getAddressableName("en")
        )
    }

    @Test
    fun `toString combines all filled parts and resolves the salutation`() {
        assertEquals(
            "Herr Prof. Skip Baker",
            person(salutation = "SIR", title = "Prof.", firstname = "Skip", lastname = "Baker").toString()
        )
        assertEquals("Skip Baker", person(firstname = "Skip", lastname = "Baker").toString())
        assertEquals("", person().toString())
    }

    @Test
    fun `toTranslatedString resolves the salutation into the given language`() {
        assertEquals(
            "Sir Prof. Skip Baker",
            person(salutation = "SIR", title = "Prof.", firstname = "Skip", lastname = "Baker").toTranslatedString("en")
        )
    }

    @Test
    fun `verifySalutation accepts a known or empty salutation`() {
        person(salutation = "SIR").verifySalutation()
        person().verifySalutation()
    }

    @Test
    fun `verifySalutation rejects an unknown salutation`() {
        assertFailsWith<HandledException> { person(salutation = "unknown").verifySalutation() }
    }

    @Test
    fun `validateSalutation collects a message only for an unknown salutation`() {
        val messages = mutableListOf<String>()

        person(salutation = "SIR").validateSalutation { messages.add(it) }
        person().validateSalutation { messages.add(it) }
        assertTrue { messages.isEmpty() }

        person(salutation = "unknown").validateSalutation { messages.add(it) }
        assertEquals(1, messages.size)
    }

    @Test
    fun `equals is true for equal person data`() {
        val one = person(title = "Prof.", salutation = "SIR", firstname = "Skip", lastname = "Baker")
        val other = person(title = "Prof.", salutation = "SIR", firstname = "Skip", lastname = "Baker")
        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
    }

    @Test
    fun `equals is false for differing person data`() {
        val one = person(firstname = "Skip", lastname = "Baker")
        assertNotEquals(one, person(firstname = "Bob", lastname = "Baker"))
        assertNotEquals(one, person(firstname = "Skip", lastname = "Miller"))
    }

    @Test
    fun `equals handles null and foreign types`() {
        val one = person(firstname = "Skip", lastname = "Baker")
        assertFalse { one.equals(null) }
        assertFalse { one.equals("Skip Baker") }
    }

    @Test
    fun `getters return the values set via the setters`() {
        val personData = person(title = "Prof.", salutation = "SIR", firstname = "Skip", lastname = "Baker")
        assertEquals("Prof.", personData.title)
        assertEquals("Skip", personData.firstname)
        assertEquals("Baker", personData.lastname)
        assertEquals("SIR", personData.salutation.value)
    }
}
