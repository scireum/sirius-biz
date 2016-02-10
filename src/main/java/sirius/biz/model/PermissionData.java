/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.kernel.commons.Strings;
import sirius.mixing.Column;
import sirius.mixing.Composite;
import sirius.mixing.annotations.BeforeSave;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.mixing.annotations.Transient;

import java.util.Set;
import java.util.TreeSet;

/**
 * Created by aha on 08.05.15.
 */
public class PermissionData extends Composite {

    @NullAllowed
    @Length(length = 4096)
    private String permissionString;
    public static final Column PERMISSION_STRING = Column.named("permissionString");

    @Transient
    private Set<String> permissions;

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

    @BeforeSave
    protected void updatePermissionString() {
        permissionString = Strings.join(getPermissions(), ",");
    }

}
