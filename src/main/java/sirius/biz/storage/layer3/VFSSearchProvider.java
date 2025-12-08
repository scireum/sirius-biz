/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.tycho.search.OpenSearchProvider;
import sirius.biz.tycho.search.OpenSearchResult;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Makes root directories of the {@link VirtualFileSystem} visible to the
 * {@link sirius.biz.tycho.search.OpenSearchController}.
 */
@Register
public class VFSSearchProvider implements OpenSearchProvider {

    @Part
    private VirtualFileSystem vfs;

    @Override
    public String getLabel() {
        return NLS.get("VFSController.root");
    }

    @Nullable
    @Override
    public String getUrl() {
        return "/fs";
    }

    @Override
    public String getIcon() {
        return "fa-folder-open";
    }

    @Override
    public boolean ensureAccess() {
        return true;
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
        Limit limit = new Limit(0, maxResults);
        vfs.root()
           .tree()
           .directChildrenOnly()
           .stream()
           .filter(file -> file.name().toLowerCase().contains(query))
           .forEach(file -> {
               OpenSearchResult result = new OpenSearchResult();
               result.withLabel(file.name());
               result.withDescription(file.path());
               result.withURL("/fs?path=" + Urls.encode(file.path()));
               if (limit.nextRow()) {
                   resultCollector.accept(result);
               }
           });
    }

    @Override
    public int getPriority() {
        return 90;
    }
}
