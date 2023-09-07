/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.importer.AutoImport;
import sirius.biz.tenants.EmailAddressValidator;
import sirius.biz.tenants.PhoneNumberValidator;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.LowerCase;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.ValidatedBy;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Provides various contact information for a person or company which can be embedded into other entities or mixins.
 * <p>
 * Note that this class doesn't perform any save checks or validations at all. Any entity which contains this composite
 * must decide which checks have to be performed and then either call the <tt>verifyXXX</tt> method within an
 * {@link sirius.db.mixing.annotations.BeforeSave} handler or invoke <tt>validateXXX</tt> in an
 * {@link sirius.db.mixing.annotations.OnValidate} method. Most probably these checks should be surrounded with
 * a {@link sirius.db.mixing.BaseEntity#isChanged(Mapping...)} check to only validate or verify new values.
 */
public class ContactData extends Composite {

    /**
     * Contains an email address.
     * <p>
     * If the field is filled, it has to be a valid email address, otherwise an exception will be thrown.
     */
    public static final Mapping EMAIL = Mapping.named("email");
    @Trim
    @LowerCase
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(150)
    @ValidatedBy(EmailAddressValidator.class)
    private String email;

    /**
     * Contains a phone number.
     * <p>
     * If the field is filled and <tt>validatePhoneNumbers</tt> is <tt>true</tt>, an exception is thrown
     * if the phone number has an illegal format.
     */
    public static final Mapping PHONE = Mapping.named("phone");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(150)
    @ValidatedBy(value = PhoneNumberValidator.class, strictValidation = false)
    private String phone;

    /**
     * Contains a fax number.
     * <p>
     * If the field is filled and <tt>validatePhoneNumbers</tt> is <tt>true</tt>, an exception is thrown
     * if the fax number has an illegal format.
     */
    public static final Mapping FAX = Mapping.named("fax");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(150)
    @ValidatedBy(value = PhoneNumberValidator.class, strictValidation = false)
    private String fax;

    /**
     * Contains a mobile number.
     * <p>
     * If the field is filled and <tt>validatePhoneNumbers</tt> is <tt>true</tt>, an exception is thrown
     * if the mobile phone number has an illegal format.
     */
    public static final Mapping MOBILE = Mapping.named("mobile");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(150)
    @ValidatedBy(value = PhoneNumberValidator.class, strictValidation = false)
    private String mobile;

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    @Override
    public String toString() {
        return email + " / " + phone + " / " + fax + " / " + mobile;
    }
}
