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
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Provides a street address which can be embedded into other entities or mixins.
 */
public class InternationalAddressData extends AddressData {

    @Transient
    private boolean verifyZip;

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

    /**
     * Creates a new instance with the given requirement.
     *
     * @param requirements determines which fields are required in certain constellations
     * @param fieldLabel   the name of the compund field which represents the address
     */
    public InternationalAddressData(Requirements requirements, @Nullable String fieldLabel) {
        this(requirements, fieldLabel, false);
    }

    /**
     * Creates a new instance with the given requirement.
     *
     * @param requirements determines which fields are required in certain constellations
     * @param fieldLabel   the name of the compund field which represents the address
     * @param verifyZip    determines if the given ZIP code should be verified using the countries code list
     */
    public InternationalAddressData(Requirements requirements, @Nullable String fieldLabel, boolean verifyZip) {
        super(requirements, fieldLabel);
        this.verifyZip = verifyZip;
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
        country = null;
    }

    @BeforeSave
    protected void verifyZip() {
        if (verifyZip && Strings.isFilled(country)) {
            String zipRegEx = cls.getValues("country", country).getSecond();
            if (Strings.isFilled(zipRegEx)) {
                try {
                    if (!Pattern.compile(zipRegEx).matcher(getZip()).matches()) {
                        throw Exceptions.createHandled()
                                        .withNLSKey("AddressData.badZip")
                                        .set("name", fieldLabel)
                                        .set("zip", getZip())
                                        .handle();
                    }
                } catch (PatternSyntaxException e) {
                    Exceptions.handle(e);
                }
            }
        }
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
