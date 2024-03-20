/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink;

import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.VFSRoot;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Collects all available {@link ConfigBasedUplink uplinks} and enumerates them in the root directory.
 */
@Register
public class ConfigBasedUplinksRoot implements VFSRoot {

    private List<ConfigBasedUplink> uplinks;

    @Part
    private GlobalContext ctx;

    @Override
    @Nullable
    public VirtualFile findChild(VirtualFile parent, String name) {
        return getUplinks().stream()
                           .filter(uplink -> Strings.areEqual(name, uplink.getDirectoryName()))
                           .filter(ConfigBasedUplink::checkPermission)
                           .map(uplink -> uplink.getFile(parent))
                           .findFirst()
                           .orElse(null);
    }

    private List<ConfigBasedUplink> getUplinks() {
        if (uplinks == null) {
            uplinks = initializeUplinks();
        }
        return uplinks;
    }

    private List<ConfigBasedUplink> initializeUplinks() {
        return Sirius.getSettings()
                     .getExtensions("storage.layer3.roots")
                     .stream()
                     .map(this::makeUplink)
                     .filter(Objects::nonNull)
                     .toList();
    }

    private ConfigBasedUplink makeUplink(Extension extension) {
        try {
            return ctx.findPart(extension.get("type").asString(), UplinkFactory.class)
                      .make(extension.get("name").asString(extension.getId()), extension::getRaw);
        } catch (IllegalArgumentException exception) {
            StorageUtils.LOG.SEVERE(Strings.apply(
                    "Layer 3: An error occurred while initializing the uplink '%s' from the system configuration: %s",
                    extension.getId(),
                    exception.getMessage()));
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 3: An error occurred while initializing the uplink '%s' from the system configuration: %s (%s)",
                              extension.getId())
                      .handle();
        }

        return null;
    }

    @Override
    public void enumerate(@Nonnull VirtualFile parent, FileSearch search) {
        getUplinks().stream()
                    .filter(ConfigBasedUplink::checkPermission)
                    .map(uplink -> uplink.getFile(parent))
                    .forEach(search::processResult);
    }

    @Override
    public int getPriority() {
        return 200;
    }
}
