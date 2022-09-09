/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.web.controller.Page;
import sirius.web.http.WebContext;

/**
 * Provides an extended interface which can be used as transformation target for {@link VirtualFile}.
 * <p>
 * This is used to fully customize the view of children within a directory in the {@link VirtualFileSystemController}.
 * This way, we can use {@link sirius.biz.storage.layer2.L3Uplink} to bridge the functionality to the "layer 2" so that
 * we can provide elaborate filter facets etc. without making the <tt>VirtualFile</tt> interface too complex for simple
 * scenarios like mapping external (CIFS, SFTP, ..) servers or while providing fully artificial directories and
 * files (e.g. {@link sirius.biz.jobs.JobsRoot}.
 */
public interface ChildPageProvider {

    /**
     * Executes a query for a page of virtual files described by the given web context and within the given parent file.
     *
     * @param parent     the directory to query in
     * @param webContext the context containing pagination and filter infos
     * @return a page representing the query result and additional filter facets
     */
    Page<VirtualFile> queryPage(VirtualFile parent, WebContext webContext);
}
