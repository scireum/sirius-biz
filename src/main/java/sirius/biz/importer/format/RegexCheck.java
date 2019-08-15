/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.Formatter;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Determines if the given values match a regular expression.
 */
public class RegexCheck implements ValueCheck {

    private String remark;
    private Pattern pattern;
    private String errorMessage;

    /**
     * Creates a new check using the given pattern, remark and error for non matching inputs.
     *
     * @param pattern      the {@link Pattern} which will be used to check the value
     * @param remark       the remark to show in the documentation. Can be <tt>null</tt> if there is no remark. This
     *                     value will be {@link NLS#smartGet(String) smart translated}
     * @param errorMessage containts the error message to show for non matching inputs. This may contain
     *                     <tt>{@literal ${value}}</tt> which will be replaced by the actual value. Also, this will be
     *                     {@link NLS#smartGet(String) smart translated}
     */
    public RegexCheck(@Nonnull String pattern, @Nullable String remark, @Nonnull String errorMessage) {
        this.remark = remark;
        this.pattern = Pattern.compile(pattern);
        this.errorMessage = errorMessage;
    }

    @Override
    public void perform(Value value) {
        String stringValue = value.asString();
        if (!pattern.matcher(stringValue).matches()) {
            throw new IllegalArgumentException(Formatter.create(NLS.smartGet(errorMessage))
                                                        .set("value", stringValue)
                                                        .format());
        }
    }

    @Nullable
    @Override
    public String generateRemark() {
        if (Strings.isEmpty(remark)) {
            return null;
        }

        return NLS.smartGet(remark);
    }
}
