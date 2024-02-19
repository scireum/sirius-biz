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
 * <p>
 * Note, as most of the {@link Value} methods will perform an automatic <tt>trim</tt>, we also trim the contents before
 * applying the regular expression. Use {@link #checkUntrimmed()} to suppress this behaviour.
 * <p>
 * Also note that an empty value (<tt>null</tt>) will be treated as <tt>""</tt> here, so that the regular expression
 * can handle it.
 */
public class RegexCheck extends StringCheck {

    private final String remark;
    private final Pattern pattern;
    private final String errorMessage;

    /**
     * Creates a new check using the given pattern, remark and error for non matching inputs.
     *
     * @param pattern      the {@link Pattern} which will be used to check the value
     * @param remark       the remark to show in the documentation. Can be <tt>null</tt> if there is no remark. This
     *                     value will be {@link NLS#smartGet(String) smart translated}
     * @param errorMessage contains the error message to show for non matching inputs. This may contain
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
        String effectiveValue = determineEffectiveValue(value);
        if (effectiveValue == null) {
            effectiveValue = "";
        }
        if (!pattern.matcher(effectiveValue).matches()) {
            throw new IllegalArgumentException(Formatter.create(NLS.smartGet(errorMessage))
                                                        .setDirect("value", effectiveValue)
                                                        .set("value", effectiveValue)
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
