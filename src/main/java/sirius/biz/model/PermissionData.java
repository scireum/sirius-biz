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
import sirius.biz.web.Autoloaded;
import sirius.biz.web.BizController;
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Entity;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
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
    private final Entity parent;

    /**
     * Creates a new instance for the given parent.
     *
     * @param parent the parent entity which contains this composite.
     */
    public PermissionData(Entity parent) {
        this.parent = parent;
    }

    /**
     * Contains all permissions as a single string, separated with commas.
     */
    public static final Column PERMISSION_STRING = Column.named("permissionString");
    @Autoloaded
    @NullAllowed
    @Length(4096)
    private String permissionString;

    /**
     * Contains a custom configuration which is added to the config of the current {@link
     * sirius.web.security.ScopeInfo}.
     */
    public static final Column CONFIG_STRING = Column.named("configString");
    @Autoloaded
    @NullAllowed
    @Lob
    private String configString;

    @Transient
    private Set<String> permissions;

    @Transient
    private Config config;

    /**
     * Returns all granted permissions.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the set of granted permissions
     */
    public Set<String> getPermissions() {
        if (permissions == null) {
            permissions = new TreeSet<>();
            if (Strings.isFilled(permissionString)) {
                for (String permission : permissionString.split(",")) {
                    permission = permission.trim();
                    if (Strings.isFilled(permission)) {
                        permissions.add(permission);
                    }
                }
            }
        }
        return permissions;
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
                                    .withSystemErrorMessage("Cannot loag config of %s (%s): %s (%s)",
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

    @BeforeSave
    protected void updatePermissionString() {
        permissionString = Strings.join(getPermissions(), ",");
    }
}
