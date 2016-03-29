/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.web.Autoloaded;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.mixing.Column;
import sirius.mixing.Composite;
import sirius.mixing.annotations.BeforeSave;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.web.mails.Mails;

/**
 * Created by gerhardhaufler on 04.03.16.
 */
public class ContactData extends Composite
{
    @NullAllowed
    @Autoloaded
    @Length(length = 150)
    private String email;
    public static final Column EMAIL = Column.named("email");


    @NullAllowed
    @Autoloaded
    @Length(length = 150)
    private String phone;
    public static final Column PHONE = Column.named("phone");


    @NullAllowed
    @Autoloaded
    @Length(length = 150)
    private String fax;
    public static final Column FAX = Column.named("fax");


    @NullAllowed
    @Autoloaded
    @Length(length = 150)
    private String mobile;
    public static final Column MOBILE = Column.named("mobile");

    @Part
    private static Mails mails;

    @BeforeSave
    protected void onSave() throws Exception {
        // check the eMail-Adress
        if(Strings.isFilled(this.getEmail())) {
            this.setEmail(this.getEmail().trim());
            if (!(mails.isValidMailAddress(this.getEmail(), null))) {
                throw Exceptions.createHandled()
                                .withNLSKey("Model.invalidEmail").set("value", this.getEmail()).handle();
            }
        }
        // normalize phone, fax and mobile Nr
        this.setPhone(normalizePhoneNumber(this.getPhone()));
        this.setFax(normalizePhoneNumber(this.getFax()));
        this.setMobile(normalizePhoneNumber(this.getMobile()));
    }

    private String normalizePhoneNumber(String number) {
        if(Strings.isFilled(number)) {

            number = number.replace(" ", "");
            // (0) am Anfang durch 0 ersetzen
            if(number.startsWith("(0)")) {
                number = "0" + number.substring(3);
            }
            // (0 nach +49 o.ä. weglöschen
            number = number.replace("(0", "");
            // Aus +49 0049 machen
            number = number.replace("+", "00");
            // Alles außer Ziffern kommt raus.
            number = number.replaceAll("[^\\d]", "");
            // Ohne Ländervorwahl ist es Deutschland
            if(number.startsWith("0") && !number.startsWith("00")) {
                number = "0049" + number.substring(1);
            }

            // set "000" to "00"

            for(int i=0; i<number.length()-1; i++)   {
                if(!(number.substring(i, i+1).equals("0"))) {
                    number = "00" + number.substring(i);
                    break;
                }
            }

        }
        return number;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
}
