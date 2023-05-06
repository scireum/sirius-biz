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
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.pasta.noodle.sandbox.NoodleSandbox;
import sirius.web.mails.Mails;

import java.util.function.Consumer;
import java.util.regex.Pattern;

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
     * Matches a part of a phone number like <tt>55 55</tt> or <tt>55</tt>.
     */
    private static final String NUMERIC_PART = "( *\\d+)*+";

    /**
     * Validates a phone number.
     */
    public static final Pattern VALID_PHONE_NUMBER =
            Pattern.compile("\\+?\\d+" + NUMERIC_PART + "( */" + NUMERIC_PART + ")?( *-" + NUMERIC_PART + ")?");

    /**
     * Contains an email address.
     * <p>
     * If the field is filled, it has to be a valid email address, otherwise an exception will be thrown.
     */
    public static final Mapping EMAIL = Mapping.named("email");
    @Trim
    @NullAllowed
    @Autoloaded
    @AutoImport
    @Length(150)
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
    private String mobile;

    @Part
    private static Mails mails;

    /**
     * Verifies that the given phone number is valid using {@link #VALID_PHONE_NUMBER}.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     * Note that this will skip empty values.
     *
     * @throws HandledException in case of an invalid phone number
     */
    public void verifyPhoneNumber() {
        if (Strings.isFilled(phone) && !VALID_PHONE_NUMBER.matcher(phone).matches()) {
            throw invalidPhoneException(NLS.get("ContactData.phone"), phone);
        }
    }

    /**
     * Validates that the given phone number is valid using {@link #VALID_PHONE_NUMBER}.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     * Note that this will skip empty values.
     *
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validatePhoneNumber(Consumer<String> validationMessageConsumer) {
        if (Strings.isFilled(phone) && !VALID_PHONE_NUMBER.matcher(phone).matches()) {
            validationMessageConsumer.accept(invalidPhoneException(NLS.get("ContactData.phone"), phone).getMessage());
        }
    }

    /**
     * Verifies that the given mobile phone number is valid using {@link #VALID_PHONE_NUMBER}.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     * Note that this will skip empty values.
     *
     * @throws HandledException in case of an invalid mobile phone number
     */
    public void verifyMobileNumber() {
        if (Strings.isFilled(phone) && !VALID_PHONE_NUMBER.matcher(phone).matches()) {
            throw invalidPhoneException(NLS.get("ContactData.mobile"), phone);
        }
    }

    /**
     * Validates that the given mobile phone number is valid using {@link #VALID_PHONE_NUMBER}.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     * Note that this will skip empty values.
     *
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validateMobileNumber(Consumer<String> validationMessageConsumer) {
        if (Strings.isFilled(mobile) && !VALID_PHONE_NUMBER.matcher(mobile).matches()) {
            validationMessageConsumer.accept(invalidPhoneException(NLS.get("ContactData.mobile"), mobile).getMessage());
        }
    }

    /**
     * Verifies that the given fax number is valid using {@link #VALID_PHONE_NUMBER}.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.BeforeSave} handler.
     * Note that this will skip empty values.
     *
     * @throws HandledException in case of an invalid fax number
     */
    public void verifyFaxNumber() {
        if (Strings.isFilled(fax) && !VALID_PHONE_NUMBER.matcher(fax).matches()) {
            throw invalidPhoneException(NLS.get("ContactData.fax"), fax);
        }
    }

    /**
     * Validates that the given fax number is valid using {@link #VALID_PHONE_NUMBER}.
     * <p>
     * This is intended to be invoked within a {@link sirius.db.mixing.annotations.OnValidate} handler.
     * Note that this will skip empty values.
     *
     * @param validationMessageConsumer the consumer which is used to collect validation messages. This is normally
     *                                  passed into the on validate method and can simply be forwarded here.
     */
    public void validateFaxNumber(Consumer<String> validationMessageConsumer) {
        if (Strings.isFilled(fax) && !VALID_PHONE_NUMBER.matcher(fax).matches()) {
            validationMessageConsumer.accept(invalidPhoneException(NLS.get("ContactData.fax"), fax).getMessage());
        }
    }

    private HandledException invalidPhoneException(String field, String value) {
        return Exceptions.createHandled()
                         .withNLSKey("ContactData.invalidPhone")
                         .set("field", field)
                         .set("value", value)
                         .handle();
    }

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
