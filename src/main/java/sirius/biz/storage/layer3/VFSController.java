/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import io.netty.handler.codec.http.HttpHeaderNames;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.InputStreamHandler;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;
import sirius.web.util.LinkBuilder;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides a web based UI for the {@link VirtualFileSystem}.
 */
@Register
public class VFSController extends BizController {

    @Part
    private VirtualFileSystem vfs;

    /**
     * Lists all children of a given directory.
     *
     * @param ctx the request to handle
     */
    @LoginRequired
    @Routed("/fs")
    public void list(WebContext ctx) {
        String path = ctx.get("path").asString("/");
        VirtualFile file = resolveToExistingFile(path);

        if (!file.isDirectory()) {
            file.deliverDownloadTo(ctx);
        } else {
            ctx.respondWith()
               .template("/templates/biz/storage/list.html.pasta",
                         file,
                         file.pathList(),
                         computeChildrenAsPage(ctx, file));
        }
    }

    private VirtualFile resolveToExistingFile(String path) {
        VirtualFile file = vfs.resolve(path);
        if (!file.exists()) {
            UserContext.get()
                       .addMessage(Message.error(NLS.fmtr("VFSController.unknownPath").set("path", path).format()));

            while (file != null && !file.exists()) {
                file = file.parent();
            }

            if (!file.exists()) {
                file = vfs.root();
            }
        }

        return file;
    }

    private Page<VirtualFile> computeChildrenAsPage(WebContext ctx, VirtualFile file) {
        Page<VirtualFile> page = new Page<VirtualFile>().withStart(1).bindToRequest(ctx);
        page.withLimitedItemsSupplier(limit -> {
            List<VirtualFile> childFiles = new ArrayList<>();
            file.children(FileSearch.iterateAll(childFiles::add).withPrefixFilter(page.getQuery()).withLimit(limit));
            return childFiles;
        });

        return page;
    }

    /**
     * Uploads a new file or replaces the contents of an existing one.
     *
     * @param ctx         the request to handle
     * @param out         the JSON response
     * @param inputStream the contents to process
     * @throws Exception in case of an error
     */
    @LoginRequired
    @Routed(value = "/fs/upload", preDispatchable = true, jsonCall = true)
    public void upload(WebContext ctx, JSONStructuredOutput out, InputStreamHandler inputStream) throws Exception {
        VirtualFile parent = vfs.resolve(ctx.get("path").asString());
        parent.assertExistingDirectory();

        String filename = ctx.get("filename").asString(ctx.get("qqfile").asString());
        VirtualFile file = parent.resolve(filename);

        boolean exists = file.exists();
        if (exists) {
            file.assertExistingFile();
        }

        try {
            ctx.markAsLongCall();
            long size = ctx.getHeaderValue(HttpHeaderNames.CONTENT_LENGTH).asLong(0);
            if (size > 0) {
                file.consumeStream(inputStream, size);
            } else {
                try (OutputStream outputStream = file.createOutputStream()) {
                    ctx.markAsLongCall();
                    Streams.transfer(inputStream, outputStream);
                } finally {
                    inputStream.close();
                }
            }

            out.property("file", file.path());
            out.property("refresh", true);
        } catch (Exception e) {
            if (!exists) {
                try {
                    file.delete();
                } catch (Exception ex) {
                    Exceptions.ignore(ex);
                }
            }

            throw Exceptions.createHandled().error(e).handle();
        }
    }

    /**
     * Deletes the given file or directory.
     *
     * @param ctx the request to handle
     */
    @LoginRequired
    @Routed("/fs/delete")
    public void delete(WebContext ctx) {
        VirtualFile file = vfs.resolve(ctx.get("path").asString());
        if (ctx.isSafePOST()) {
            try {
                if (file.exists()) {
                    file.delete();
                    showDeletedMessage();
                }
            } catch (Exception e) {
                UserContext.handle(e);
            }
            ctx.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
        } else {
            ctx.respondWith().redirectToGet("/fs");
        }
    }

    /**
     * Renames the given file or directory.
     *
     * @param ctx the request to handle
     */
    @LoginRequired
    @Routed("/fs/rename")
    public void rename(WebContext ctx) {
        VirtualFile file = vfs.resolve(ctx.get("path").asString());
        if (ctx.isSafePOST()) {
            try {
                String name = ctx.get("name").asString();
                if (file.exists() && Strings.isFilled(name)) {
                    file.rename(name);
                    UserContext.message(Message.info(NLS.get("VFSController.renamed")));
                }
            } catch (Exception e) {
                UserContext.handle(e);
            }
            ctx.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
        } else {
            ctx.respondWith().redirectToGet("/fs");
        }
    }

    /**
     * Creates a new directory.
     *
     * @param ctx the request to handle
     */
    @LoginRequired
    @Routed("/fs/createDirectory")
    public void createDirectory(WebContext ctx) {
        VirtualFile parent = Optional.ofNullable(vfs.resolve(ctx.get("parent").asString()))
                                     .filter(VirtualFile::exists)
                                     .filter(VirtualFile::isDirectory)
                                     .orElse(null);
        if (ctx.isSafePOST()) {
            try {
                String name = ctx.get("name").asString();
                VirtualFile newDirectory = parent.resolve(name);
                newDirectory.createAsDirectory();
                UserContext.message(Message.info(NLS.get("VFSController.directoryCreated")));
                ctx.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", newDirectory.path()).toString());
            } catch (Exception e) {
                UserContext.handle(e);
                ctx.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", parent.path()).toString());
            }
        } else {
            ctx.respondWith().redirectToGet("/fs");
        }
    }

    /**
     * Moves the given file or directory to a new parent directory.
     *
     * @param ctx the request to handle
     */
    @LoginRequired
    @Routed("/fs/move")
    public void move(WebContext ctx) {
        VirtualFile file = vfs.resolve(ctx.get("path").asString());
        VirtualFile newParent = vfs.resolve(ctx.get("newParent").asString());
        if (!file.exists()) {
            ctx.respondWith().redirectToGet("/fs");
            return;
        }

        try {
            if (newParent.exists() && newParent.isDirectory()) {
                Optional<String> processId = file.transferTo(newParent).move();
                if (processId.isPresent()) {
                    UserContext.message(Message.info(NLS.get("VFSController.movedInProcess"))
                                               .withAction("/ps/" + processId.get(),
                                                           NLS.get("VFSController.moveProcess")));
                } else {
                    UserContext.message(Message.info(NLS.get("VFSController.moved")));
                }
            }
        } catch (Exception e) {
            UserContext.handle(e);
        }

        ctx.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
    }

    /**
     * Provides a JSON API which lists the contents of a given directory.
     * <p>
     * This is used by the selectVFSFile or selectVFSDirectory JavaScript calls/modals.
     *
     * @param ctx the request to handle
     * @param out the JSON response to populate
     */
    @LoginRequired
    @Routed(value = "/fs/list", jsonCall = true)
    public void listAPI(WebContext ctx, JSONStructuredOutput out) {
        VirtualFile parent = vfs.resolve(ctx.get("path").asString("/"));
        if (parent.exists() && !parent.isDirectory()) {
            parent = parent.parent();
        }
        parent.assertExistingDirectory();

        FileSearch search = FileSearch.iterateAll(child -> outputFile(out, child.name(), child));

        if (ctx.get("onlyDirectories").asBoolean()) {
            search.withOnlyDirectories();
        }

        search.withPrefixFilter(ctx.get("filter").asString());
        search.withLimit(new Limit(0, 100));
        out.beginArray("path");
        for (VirtualFile element : parent.pathList()) {
            out.beginObject("element");
            out.property("name", element.name());
            out.property("path", element.path());
            out.endObject();
        }
        out.endArray();
        out.beginArray("children");
        if (parent.parent() != null) {
            outputFile(out, "..", parent.parent());
        }
        parent.children(search);
        out.endArray();
        out.property("canCreateChildren", parent.canCreateChildren());
    }

    private void outputFile(JSONStructuredOutput out, String name, VirtualFile child) {
        out.beginObject("child");
        try {
            out.property("name", name);
            out.property("path", child.path());
            out.property("directory", child.isDirectory());
            out.property("size", child.isDirectory() ? 0 : child.size());
            out.property("sizeString", child.isDirectory() ? "" : NLS.formatSize(child.size()));
            out.property("lastModified", child.isDirectory() ? null : NLS.toMachineString(child.lastModifiedDate()));
            out.property("lastModifiedString", child.isDirectory() ? "" : NLS.toUserString(child.lastModifiedDate()));
        } finally {
            out.endObject();
        }
    }
}
