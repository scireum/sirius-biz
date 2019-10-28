/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import sirius.biz.importer.AutoImport;
import sirius.biz.web.Autoloaded;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.StringList;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stores a set of permissions and optinally a custom configuration for an account or tenant which can be embedded into
 * other entities or mixins.
 */
public class PermissionData extends Composite {

    /**
     * Stores the associated user account or tenant for which the permissions and config is stored
     */
    @Transient
    private final BaseEntity<?> parent;

    /**
     * Contains all permissions.
     */
    public static final Mapping PERMISSIONS = Mapping.named("permissions");
    @Autoloaded
    @NullAllowed
    @AutoImport
    @Lob
    private final StringList permissions = new StringList();

    /**
     * Contains a custom configuration which is added to the config of the current {@link
     * sirius.web.security.ScopeInfo}.
     */
    public static final Mapping CONFIG_STRING = Mapping.named("configString");
    @Autoloaded
    @NullAllowed
    @Lob
    private String configString;

    @Transient
    private Config config;

    /**
     * Creates a new instance for the given parent.
     *
     * @param parent the parent entity which contains this composite.
     */
    public PermissionData(BaseEntity<?> parent) {
        this.parent = parent;
    }

    /**
     * Returns all granted permissions.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the set of granted permissions
     */
    public StringList getPermissions() {
        return permissions;
    }

    /**
     * Compiles a comma separated string into a set of permissions.
     *
     * @param data the string to parse
     * @return the set of permissions in the string
     */
    public static Set<String> compilePermissionString(String data) {
        Set<String> result = new TreeSet<>();
        if (Strings.isFilled(data)) {
            for (String permission : data.split(",")) {
                permission = permission.trim();
                if (Strings.isFilled(permission)) {
                    result.add(permission);
                }
            }
        }

        return result;
    }

    /**
     * Returns the parsed config for the associated entity.
     *
     * @return the parsed configuration
     */
    @Nullable
    public Config getConfig() {
        if (config == null) {
            if (Strings.isFilled(configString)) {
                try {
                    config = ConfigFactory.parseString(configString);
                } catch (Exception e) {
                    throw Exceptions.handle()
                                    .to(BizController.LOG)
                                    .error(e)
                                    .withSystemErrorMessage("Cannot load config of %s (%s): %s (%s)",
                                                            parent,
                                                            parent.getId())
                                    .handle();
                }
            } else {
                return null;
            }
        }

        return config;
    }

    /**
     * Returns the config as string.
     *
     * @return the individual config as string
     */
    public String getConfigString() {
        return configString;
    }

    /**
     * Sets the config as string.
     *
     * @param configString the individual config as string
     */
    public void setConfigString(String configString) {
        this.configString = configString;
        this.config = null;
    }
}
