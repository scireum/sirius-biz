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
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Entity;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by aha on 08.05.15.
 */
public class PermissionData extends Composite {

    @Transient
    private final Entity parent;

    public PermissionData(Entity parent) {
        this.parent = parent;
    }

    @Autoloaded
    @NullAllowed
    @Length(length = 4096)
    private String permissionString;
    public static final Column PERMISSION_STRING = Column.named("permissionString");

    @Autoloaded
    @NullAllowed
    @Length(length = 4096)
    private String configString;
    public static final Column CONFIG_STRING = Column.named("configString");

    @Transient
    private Set<String> permissions;

    @Transient
    private Config config;

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

    public String getConfigString() {
        return configString;
    }

    public void setConfigString(String configString) {
        this.configString = configString;
        this.config = null;
    }

    @BeforeSave
    protected void updatePermissionString() {
        permissionString = Strings.join(getPermissions(), ",");
    }
}
