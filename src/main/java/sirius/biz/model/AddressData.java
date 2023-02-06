/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.importer.AutoImport;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.Formatter;
import sirius.kernel.nls.NLS;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Provides a street address which can be embedded into other entities or mixins.
 * <p>
 * Note that this class doesn't perform any save checks or validations at all. Any entity which contains this composite
 * must decide which checks have to be performed and then either call the <tt>verifyXXX</tt> method within an
 * {@link sirius.db.mixing.annotations.BeforeSave} handler or invoke <tt>validateXXX</tt> in an
 * {@link sirius.db.mixing.annotations.OnValidate} method. Most probably these checks should be surrounded with
 * a {@link sirius.db.mixing.BaseEntity#isChanged(Mapping...)} check to only validate or verify new values.
 */
public class AddressData extends Composite {

    /**
     * Contains the street and street number.
     */
    public static final Mapping STREET = Mapping.named("street");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(255)
    private String street;

    /**
     * Contains the ZIP code.
     */
    public static final Mapping ZIP = Mapping.named("zip");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(16)
    private String zip;

    /**
     * Contains the name of the city.
     */
    public static final Mapping CITY = Mapping.named("city");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(255)
    private String city;

    /**
     * Verifies that all fields of the address are properly filled.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     *
     * @param fieldLabel the alternative label for the address to use
     * @throws sirius.kernel.health.HandledException in case of missing values
     */
    public void verifyFullAddress(@Nullable String fieldLabel) {
        if (isAnyFieldEmpty()) {
            throw Exceptions.createHandled()
                            .withNLSKey("AddressData.fullAddressRequired")
                            .set("name", determineFieldLabel(fieldLabel))
                            .handle();
        }
    }

    /**
     * Validates that all fields of the address are properly filled.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     *
     * @param fieldLabel                the alternative label for the address to use
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validateFullAddress(@Nullable String fieldLabel, Consumer<String> validationMessageConsumer) {
        if (isAnyFieldEmpty()) {
            validationMessageConsumer.accept(NLS.fmtr("AddressData.fullAddressRequired")
                                                .set("name", determineFieldLabel(fieldLabel))
                                                .format());
        }
    }

    /**
     * Verifies that either all or no fields of the address are filled.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     *
     * @param fieldLabel the alternative label for the address to use
     * @throws sirius.kernel.health.HandledException in case of a partially filled address
     */
    public void verifyNonPartialAddress(@Nullable String fieldLabel) {
        if (isPartiallyFilled()) {
            throw Exceptions.createHandled()
                            .withNLSKey("AddressData.partialAddressRejected")
                            .set("name", determineFieldLabel(fieldLabel))
                            .handle();
        }
    }

    /**
     * Determines if the given address is partially filled.
     *
     * @return <tt>true</tt> if there is at leas one field filled and at least one field left empty.
     * <tt>false</tt> otherwise.
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public boolean isPartiallyFilled() {
        return isAnyFieldEmpty() && !areAllFieldsEmpty();
    }

    /**
     * Validates that either all or no fields of the address are filled.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     *
     * @param fieldLabel                the alternative label for the address to use
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validateNonPartialAddress(@Nullable String fieldLabel, Consumer<String> validationMessageConsumer) {
        if (isPartiallyFilled()) {
            validationMessageConsumer.accept(NLS.fmtr("AddressData.partialAddressRejected")
                                                .set("name", determineFieldLabel(fieldLabel))
                                                .format());
        }
    }

    /**
     * Determines the field label to use for showing messages.
     *
     * @return the field label to use to show messages
     */
    protected String determineFieldLabel(String fieldLabel) {
        if (Strings.isFilled(fieldLabel)) {
            return fieldLabel;
        }

        return NLS.get("Model.address");
    }

    /**
     * Determines if at least one field in the address is empty.
     *
     * @return <tt>true</tt> if at least one field is empty, <tt>false</tt> otherwise
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public boolean isAnyFieldEmpty() {
        return Strings.isEmpty(street) || Strings.isEmpty(zip) || Strings.isEmpty(city);
    }

    /**
     * Determines if all fields are empty.
     *
     * @return <tt>true</tt> all fields are empty, <tt>false</tt> otherwise
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public boolean areAllFieldsEmpty() {
        return Strings.isEmpty(street) && Strings.isEmpty(zip) && Strings.isEmpty(city);
    }

    /**
     * Sets all fields to <tt>null</tt>.
     */
    public void clear() {
        street = null;
        zip = null;
        city = null;
    }

    @Override
    public String toString() {
        return Formatter.create("[${steet} ][${zip} ]${city}")
                        .set("steet", street)
                        .set("zip", zip)
                        .set("city", city)
                        .smartFormat();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof AddressData)) {
            return false;
        }

        return Strings.areEqual(street, ((AddressData) obj).street)
               && Strings.areEqual(zip, ((AddressData) obj).zip)
               && Strings.areEqual(city, ((AddressData) obj).city);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, zip, city);
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
