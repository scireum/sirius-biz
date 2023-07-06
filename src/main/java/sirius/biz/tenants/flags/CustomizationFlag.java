/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.flags;

import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

/**
 * Represents a customization flag which can be used to enable or disable certain features for certain users of the
 * system.
 * <p>
 * Note that instances of this class should be initialized as static fields in order to be properly registered. This way,
 * {@link CustomizationFlags#getKnownFlags()} can report all known flags.
 */
public class CustomizationFlag {

    @Part
    private static CustomizationFlags flags;

    private final String name;
    private final boolean defaultValue;

    /**
     * Creates a new flag with the given name
     *
     * @param name         the name of the flag to use
     * @param defaultValue the default value to use if no config is present
     */
    public static CustomizationFlag create(String name, boolean defaultValue) {
        CustomizationFlag flag = new CustomizationFlag(name, defaultValue);
        CustomizationFlags.addKnownFlag(flag);

        return flag;
    }

    private CustomizationFlag(String name, boolean defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    /**
     * Checks if the given flag is enabled or uses the specified default value if no config is present.
     *
     * @return <tt>true</tt> if the flag is enabled or <tt>false</tt> otherwise
     */
    public boolean isEnabled() {
        return flags != null && flags.isFlagEnabled(name, defaultValue).getFirst();
    }

    /**
     * Determines if the flag is enabled and also reports the reason why it is enabled or disabled.
     *
     * @return a tuple containing the flag state and also the source which was used to determine the state.
     */
    public Tuple<Boolean, String> check() {
        return flags.isFlagEnabled(name, defaultValue);
    }

    public String getName() {
        return name;
    }
}
