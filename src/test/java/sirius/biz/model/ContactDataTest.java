/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicBoolean;

class ContactDataTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
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
            +49+1 test ; false
            """, delimiter = ';')
    void testPhoneNumberValidation(String number, String valid) {
        ContactData data = new ContactData();
        data.setPhone(number);
        AtomicBoolean validated = new AtomicBoolean(true);
        data.validatePhoneNumber(ignored -> validated.set(false));

        Assertions.assertEquals(Boolean.parseBoolean(valid), validated.get());
    }
}
