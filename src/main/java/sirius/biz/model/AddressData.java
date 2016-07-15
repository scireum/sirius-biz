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
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.Formatter;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    private final Requirements requirements;
    @Transient
    private String fieldLabel;

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

    /**
     * Contains the street and street number.
     */
    public static final Column STREET = Column.named("street");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(255)
    private String street;

    /**
     * Contains the ZIP code.
     */
    public static final Column ZIP = Column.named("zip");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(9)
    private String zip;

    /**
     * Contains the name of the city.
     */
    public static final Column CITY = Column.named("city");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(255)
    private String city;

    /**
     * Contains the country code.
     * <p>
     * Note that a code list "country" exists which enumerates possible countries.
     */
    public static final Column COUNTRY = Column.named("country");
    @Trim
    @NullAllowed
    @Autoloaded
    @Length(3)
    private String country;

    @Part
    private static CodeLists cls;

    @BeforeSave
    protected void onSave() {
        if (requirements == Requirements.NONE) {
            return;
        }
        boolean allEmpty =
                Strings.isEmpty(street) && Strings.isEmpty(zip) && Strings.isEmpty(city) && Strings.isEmpty(country);
        boolean oneEmpty =
                Strings.isEmpty(street) || Strings.isEmpty(zip) || Strings.isEmpty(city) || Strings.isEmpty(country);
        if (oneEmpty) {
            if (requirements == Requirements.FULL_ADDRESS) {
                throw Exceptions.createHandled()
                                .withNLSKey("AddressData.fullAddressRequired")
                                .set("name", fieldLabel)
                                .handle();
            }
            if (!allEmpty && requirements == Requirements.NOT_PARTIAL) {
                throw Exceptions.createHandled()
                                .withNLSKey("AddressData.partialAddressRejected")
                                .set("name", fieldLabel)
                                .handle();
            }
        }

        if (Strings.isFilled(country)) {
            String zipRegEx = cls.getValues("country", country).getSecond();
            if (Strings.isFilled(zipRegEx)) {
                try {
                    if (!Pattern.compile(zipRegEx).matcher(zip).matches()) {
                        throw Exceptions.createHandled()
                                        .withNLSKey("AddressData.badZip")
                                        .set("name", fieldLabel)
                                        .set("zip", zip)
                                        .handle();
                    }
                } catch (PatternSyntaxException e) {
                    Exceptions.handle(e);
                }
            }
        }
    }

    @Override
    public String toString() {
        return Formatter.create("[${steet} ][${zip} ]${city}")
                        .set("steet", street)
                        .set("zip", zip)
                        .set("city", city)
                        .smartFormat();
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

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
