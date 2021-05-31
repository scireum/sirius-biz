/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.codelists.LookupValue;
import sirius.biz.util.Countries;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
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
import java.util.regex.PatternSyntaxException;

/**
 * Provides a {@link AddressData street address} along with a country code to be emdedded in entities or mixins.
 * <p>
 * Note that just like {@link AddressData} this doesn't perform any validations or verifications unless explicitly
 * told to do so.
 */
public class InternationalAddressData extends AddressData {

    @Part
    private static Countries countries;

    /**
     * Contains the country code.
     * <p>
     * Note that a code list "country" exists which enumerates possible countries.
     */
    @Explain("We provide a second constant here as they are semantically different.")
    public static final Mapping COUNTRY = Mapping.named("country");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(3)
    private final LookupValue country;

    /**
     * Creates a new composite using the default lookup table.
     */
    public InternationalAddressData() {
        this(Countries.LOOKUP_TABLE_ACTIVE_COUNTRIES);
    }

    /**
     * Creates a new composite using the given lookup table.
     * <p>
     * This can be used to provide a filtered subset in case the <tt>countries</tt> table contains too many entries.
     *
     * @param countriesLookupTable the name of the lookup table to use
     */
    public InternationalAddressData(String countriesLookupTable) {
        this.country = new LookupValue(countriesLookupTable,
                                       LookupValue.CustomValues.ACCEPT,
                                       LookupValue.Display.NAME,
                                       LookupValue.Export.CODE);
    }

    @BeforeSave
    protected void migrateLegacyCodes() {
        country.setValue(countries.all().forcedNormalizeWithMapping(country.getValue(), Countries.MAPPING_LEGACY));
    }

    @Override
    public boolean areAllFieldsEmpty() {
        return super.areAllFieldsEmpty() && Strings.isEmpty(country);
    }

    @Override
    public boolean isAnyFieldEmpty() {
        return super.isAnyFieldEmpty() || Strings.isEmpty(country);
    }

    @Override
    public void clear() {
        super.clear();
        country.setValue(null);
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
            country.verifyValue();
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
            country.verifyValue();
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
        if (Strings.isEmpty(country.getValue())) {
            return;
        }

        try {
            if (!countries.isValidZipCode(country.getValue(), getZip())) {
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
        if (Strings.isEmpty(country.getValue())) {
            return;
        }

        try {
            if (!countries.isValidZipCode(country.getValue(), getZip())) {
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
        return country.getTable().resolveName(country.getValue()).orElse(country.getValue());
    }

    public String getCountry() {
        return country.getValue();
    }

    public void setCountry(String country) {
        this.country.setValue(country);
    }
}
