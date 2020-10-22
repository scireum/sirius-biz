/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.codelists.CodeLists;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Provides a street address which can be embedded into other entities or mixins.
 */
public class InternationalAddressData extends AddressData {

    /**
     * Contains the name of the code list which defines all valid countries for {@link #COUNTRY}.
     * <p>
     * In this code list the key is the value in the field (probably a country code). The <tt>value</tt> is the name
     * of the country and the <tt>additionalValue</tt> can be a regular expression which validates ZIP codes for this
     * country.
     */
    private static final String CODE_LIST_COUNTRIES = "countries";

    @Part
    @Nullable
    private static CodeLists<?, ?, ?> codeLists;

    /**
     * Contains the country code.
     * <p>
     * Note that a code list "country" exists which enumerates possible countries.
     */
    @SuppressWarnings("squid:S1192")
    @Explain("We provide a second constant here as they are semantically different.")
    public static final Mapping COUNTRY = Mapping.named("country");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(3)
    private String country;

    @Override
    public boolean areAllFieldsEmpty() {
        return super.areAllFieldsEmpty() && Strings.isEmpty(country);
    }

    @Override
    public boolean isAnyFieldEmpty() {
        return super.isAnyFieldEmpty() || Strings.isEmpty(country);
    }

    /**
     * Determines if the given address is partially filled.
     * <p>
     * Note that this excludes the check if only a country if filled and nothing else (as this is commonly
     * provided by a drop-down selector). Therefore we treat an address with only a country as "empty".
     *
     * @return <tt>true</tt> if there is at least one field filled and at least one field left empty.
     * <tt>false</tt> otherwise.
     */
    @Override
    public boolean isPartiallyFilled() {
        return isAnyFieldEmpty() && !super.areAllFieldsEmpty();
    }

    @Override
    public void clear() {
        super.clear();
        country = null;
    }

    /**
     * Verifies that the given country is part of the underlying code list.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     * Note that this will skip empty values.
     *
     * @param fieldLabel the alternative label for the address to use
     * @throws sirius.kernel.health.HandledException in case of an invalid country
     */
    public void verifyCountry(@Nullable String fieldLabel) {
        try {
            codeLists.verifyValue(CODE_LIST_COUNTRIES, country);
        } catch (Exception e) {
            throw Exceptions.createHandled()
                            .withNLSKey("AddressData.invalidCountry")
                            .set("name", determineFieldLabel(fieldLabel))
                            .set("error", e.getMessage())
                            .handle();
        }
    }

    /**
     * Validates that the given country is part of the underlying code list.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     * Note that this will skip empty values.
     *
     * @param fieldLabel                the alternative label for the address to use
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validateCountry(@Nullable String fieldLabel, Consumer<String> validationMessageConsumer) {
        try {
            codeLists.verifyValue(CODE_LIST_COUNTRIES, country);
        } catch (Exception e) {
            validationMessageConsumer.accept(NLS.fmtr("AddressData.invalidCountry")
                                                .set("name", determineFieldLabel(fieldLabel))
                                                .set("error", e.getMessage())
                                                .format());
        }
    }

    /**
     * Verifies that the given ZIP is valid for the selected country.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     * Note that this will skip empty values.
     *
     * @param fieldLabel the alternative label for the address to use
     * @throws sirius.kernel.health.HandledException in case of an invalid ZIP code
     */
    public void verifyZip(@Nullable String fieldLabel) {
        if (Strings.isEmpty(country)) {
            return;
        }

        String zipRegEx = codeLists.getValues(CODE_LIST_COUNTRIES, country).getSecond();
        if (Strings.isEmpty(zipRegEx)) {
            return;
        }

        try {
            if (!Pattern.compile(zipRegEx).matcher(getZip()).matches()) {
                throw Exceptions.createHandled()
                                .withNLSKey("AddressData.badZip")
                                .set("name", determineFieldLabel(fieldLabel))
                                .set("zip", getZip())
                                .handle();
            }
        } catch (PatternSyntaxException e) {
            Exceptions.handle(e);
        }
    }

    /**
     * Validates that the given ZIP is valid for the given country.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     * Note that this will skip empty values.
     *
     * @param fieldLabel                the alternative label for the address to use
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validateZIP(@Nullable String fieldLabel, Consumer<String> validationMessageConsumer) {
        if (Strings.isEmpty(country)) {
            return;
        }

        String zipRegEx = codeLists.getValues(CODE_LIST_COUNTRIES, country).getSecond();
        if (Strings.isEmpty(zipRegEx)) {
            return;
        }

        try {
            if (!Pattern.compile(zipRegEx).matcher(getZip()).matches()) {
                validationMessageConsumer.accept(NLS.fmtr("AddressData.badZip")
                                                    .set("name", determineFieldLabel(fieldLabel))
                                                    .set("zip", getZip())
                                                    .format());
            }
        } catch (PatternSyntaxException e) {
            Exceptions.handle(e);
        }
    }

    @Override
    @SuppressWarnings("java:S2159")
    @Explain("In this case the call to super.equals works, as this accepts all subclasses of AddressData")
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }

        if (!(obj instanceof InternationalAddressData)) {
            return false;
        }

        return Strings.areEqual(country, ((InternationalAddressData) obj).country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStreet(), getZip(), getCity(), getCountry());
    }

    /**
     * Returns the value (translated name) of the country.
     *
     * @return the value for <tt>country</tt> from the <tt>countries</tt> code list
     */
    public String getTranslatedCountry() {
        return codeLists.getTranslatedValue(CODE_LIST_COUNTRIES, country);
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
