/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.web.Autoloaded;
import sirius.mixing.Column;
import sirius.mixing.Composite;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.mixing.annotations.Trim;

/**
 * Created by aha on 07.05.15.
 */
public class AddressData extends Composite {

    @Trim
    @NullAllowed
    @Autoloaded
    @Length(length = 255)
    private String street;
    public static final Column STREET = Column.named("street");

    @Trim
    @NullAllowed
    @Autoloaded
    @Length(length = 9)    // ToDo In Schweden braucht man das !
    private String zip;
    public static final Column ZIP = Column.named("zip");

    @Trim
    @NullAllowed
    @Autoloaded
    @Length(length = 255)
    private String city;
    public static final Column CITY = Column.named("city");

    @Trim
    @NullAllowed
    @Autoloaded
    @Length(length = 3)
    private String country;
    public static final Column COUNTRY = Column.named("country");

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
