/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.flags;

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

    /**
     * Creates a new flag with the given name
     *
     * @param name the name of the flag to use
     */
    public CustomizationFlag(String name) {
        this.name = name;
        CustomizationFlags.addKnownFlag(name);
    }

    /**
     * Checks if the given flag is enabled.
     *
     * @param defaultValue determines the default value in case no flag is set
     * @return <tt>true</tt> if the flag is enabled or <tt>false</tt> otherwise
     */
    public boolean isEnabled(boolean defaultValue) {
        return flags != null && flags.isFlagEnabled(name, defaultValue).getFirst();
    }

    /**
     * Checks if the given flag is enabled, uses <tt>false</tt> as fallback if no configuration is present.
     *
     * @return <tt>true</tt> if the flag is enabled or <tt>false</tt> otherwise
     */
    public boolean isEnabled() {
        return isEnabled(false);
    }
}
