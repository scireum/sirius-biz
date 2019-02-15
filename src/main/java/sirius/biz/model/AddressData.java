/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.Formatter;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Provides a street address which can be embedded into other entities or mixins.
 */
public class AddressData extends Composite {

    /**
     * As there are many different requirements for what a valid address might be, these can be specified per
     * <tt>AddressData</tt> using one of the following requirements.
     */
    public enum Requirements {
        /**
         * Each value within the address can filled or empty
         */
        NONE,

        /**
         * If one of the fields is filled, all others have to be filled
         */
        NOT_PARTIAL,

        /**
         * All fields have to be filled
         */
        FULL_ADDRESS,
    }

    @Transient
    protected final Requirements requirements;

    @Transient
    protected String fieldLabel;

    /**
     * Contains a temporary field label which can be used to
     * show messages based on the context the {@link AddressData}
     * is handled.
     * <p>
     * This can be used to show a customized property for the field
     * label when the {@link AddressData} is used in another context
     * than is is usually used.
     */
    @Transient
    protected String temporaryFieldLabel;

    /**
     * Contains the street and street number.
     */
    public static final Mapping STREET = Mapping.named("street");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(255)
    private String street;

    /**
     * Contains the ZIP code.
     */
    public static final Mapping ZIP = Mapping.named("zip");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(9)
    private String zip;

    /**
     * Contains the name of the city.
     */
    public static final Mapping CITY = Mapping.named("city");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(255)
    private String city;

    /**
     * Creates a new instance with the given requirement.
     *
     * @param requirements determines which fields are required in certain constellations
     * @param fieldLabel   the name of the compund field which represents the address
     */
    public AddressData(Requirements requirements, @Nullable String fieldLabel) {
        this.requirements = requirements;
        this.fieldLabel = Strings.isEmpty(fieldLabel) ? NLS.get("Model.address") : fieldLabel;
    }

    @BeforeSave
    protected void onSave() {
        if (requirements == Requirements.NONE) {
            return;
        }
        boolean allEmpty = areAllFieldsEmpty();
        boolean oneEmpty = isAnyFieldEmpty();
        if (oneEmpty) {
            if (requirements == Requirements.FULL_ADDRESS) {
                throw Exceptions.createHandled()
                                .withNLSKey("AddressData.fullAddressRequired")
                                .set("name", determineFieldLabel())
                                .handle();
            }
            if (!allEmpty && requirements == Requirements.NOT_PARTIAL) {
                throw Exceptions.createHandled()
                                .withNLSKey("AddressData.partialAddressRejected")
                                .set("name", determineFieldLabel())
                                .handle();
            }
        }
    }

    /**
     * Sets the temporary field label to show a customized
     * field label for special contexts.
     *
     * @param temporaryFieldLabel the temporary field label to set
     */
    public void setTemporaryFieldLabel(String temporaryFieldLabel) {
        this.temporaryFieldLabel = temporaryFieldLabel;
    }

    /**
     * Resets the temporary field label to use the regular field label again.
     */
    public void resetTemporaryFieldLabel() {
        temporaryFieldLabel = null;
    }

    /**
     * Determines the field label to use for showing messages.
     *
     * @return the field label to use to show messages
     */
    protected String determineFieldLabel() {
        if (Strings.isFilled(temporaryFieldLabel)) {
            return temporaryFieldLabel;
        }
        return fieldLabel;
    }

    /**
     * Determines if at least one field in the address is empty.
     *
     * @return <tt>true</tt> if at least one field is empty, <tt>false</tt> otherwise
     */
    public boolean isAnyFieldEmpty() {
        return Strings.isEmpty(street) || Strings.isEmpty(zip) || Strings.isEmpty(city);
    }

    /**
     * Determines if all fields are empty.
     *
     * @return <tt>true</tt> all fields are empty, <tt>false</tt> otherwise
     */
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
               && Strings.areEqual(zip,
                                   ((AddressData) obj).zip)
               && Strings.areEqual(city, ((AddressData) obj).city);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, zip, city);
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
