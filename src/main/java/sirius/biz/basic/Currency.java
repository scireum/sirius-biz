/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.basic;

import sirius.biz.model.BizEntity;
import sirius.biz.protocol.JournalData;
import sirius.db.mixing.Column;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.commons.Strings;

import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;

/**
 * Created by aha on 29.04.16.
 */
public class Currency extends BizEntity implements NumberFormat {

    private final JournalData journal = new JournalData(this);
    public static final Column JOURNAL = Column.named("journal");

    @Unique
    @Length(length = 255)
    private String code;
    public static final Column CODE = Column.named("code");

    @Unique
    @Length(length = 255)
    private String sign;
    public static final Column SIGN = Column.named("sign");

    @Unique
    @Length(length = 255)
    private String name;
    public static final Column NAME = Column.named("name");

    @Unique
    private int fractionDigits = 2;
    public static final Column FRACTION_DIGITS = Column.named("fractionDigits");

    @Length(length = 50)
    private String thousandSeparator = ".";
    public static final Column THOUSAND_SEPARATOR = Column.named("thousandSeparator");

    @Length(length = 50)
    private String decimalSeparator = ",";
    public static final Column DECIMAL_SEPARATOR = Column.named("decimalSeparator");

    @Length(length = 50)
    private RoundingMode roundingMode = RoundingMode.HALF_UP;
    public static final Column ROUNDING_MODE = Column.named("roundingMode");

    private DecimalFormatSymbols symbols;

    @Override
    public String getSuffix() {
        return getSign();
    }

    @Override
    public int getScale() {
        return getFractionDigits();
    }

    @Override
    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    @Override
    public DecimalFormatSymbols getDecimalFormatSymbols() {
        if (symbols == null) {
            DecimalFormatSymbols dcs = new DecimalFormatSymbols();
            if (Strings.isFilled(decimalSeparator)) {
                dcs.setDecimalSeparator(decimalSeparator.charAt(0));
            }
            if (Strings.isFilled(thousandSeparator)) {
                dcs.setGroupingSeparator(getThousandSeparator().charAt(0));
            }
            symbols = dcs;
        }
        return symbols;
    }

    public JournalData getJournal() {
        return journal;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFractionDigits() {
        return fractionDigits;
    }

    public void setFractionDigits(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public String getThousandSeparator() {
        return thousandSeparator;
    }

    public void setThousandSeparator(String thousandSeparator) {
        this.thousandSeparator = thousandSeparator;
        this.symbols = null;
    }

    public String getDecimalSeparator() {
        return decimalSeparator;
    }

    public void setDecimalSeparator(String decimalSeparator) {
        this.decimalSeparator = decimalSeparator;
        this.symbols = null;
    }

    public void setRoundingMode(RoundingMode roundingMode) {
        this.roundingMode = roundingMode;
    }
}
