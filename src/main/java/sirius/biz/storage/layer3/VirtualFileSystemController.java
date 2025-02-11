/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import io.netty.handler.codec.http.HttpHeaderNames;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.tycho.QuickAction;
import sirius.biz.tycho.UserAssistant;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.InputStreamHandler;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;
import sirius.web.util.LinkBuilder;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides a web based UI for the {@link VirtualFileSystem}.
 */
@Register
public class VirtualFileSystemController extends BizController {

    private static final String PARENT_DIR = "..";

    /**
     * Permissions required to view files.
     */
    public static final String PERMISSION_VIEW_FILES = "permission-view-files";

    @Part
    private VirtualFileSystem vfs;

    @PriorityParts(FileQuickActionProvider.class)
    private List<FileQuickActionProvider> quickActionProviders;

    /**
     * Lists all children of a given directory.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs")
    @Permission(PERMISSION_VIEW_FILES)
    public void list(WebContext webContext) {
        String path = webContext.get("path").asString("/");
        VirtualFile file = resolveToExistingFile(path);

        if (!file.isDirectory()) {
            file.deliverDownloadTo(webContext);
        } else {
            // We sort of break layers here as using yet another interface doesn't provide much
            // value.
            file.tryAs(Blob.class).ifPresent(blob -> {
                webContext.setAttribute(UserAssistant.WEB_CONTEXT_SETTING_ACADEMY_TRACK,
                                        blob.getStorageSpace().getAcademyVideoTrackId());
                webContext.setAttribute(UserAssistant.WEB_CONTEXT_SETTING_ACADEMY_VIDEO,
                                        blob.getStorageSpace().getAcademyVideoCode());
                webContext.setAttribute(UserAssistant.WEB_CONTEXT_SETTING_KBA,
                                        blob.getStorageSpace().getKnowledgeBaseArticleCode());
            });

            webContext.respondWith()
                      .template("/templates/biz/storage/list.html.pasta",
                                this,
                                file,
                                file.pathList(),
                                computeChildrenAsPage(webContext, file));
        }
    }

    /**
     * Resolves quick actions which are applicable for the provided file.
     *
     * @param virtualFile the file for which the quick actions have to be resolved for
     * @return a list of quick actions applicable for the file
     */
    public List<QuickAction> resolveQuickActionsForFile(VirtualFile virtualFile) {
        List<QuickAction> quickActionList = new ArrayList<>();
        quickActionProviders.forEach(provider -> provider.computeQuickAction(virtualFile, quickActionList::add));
        return quickActionList;
    }

    private VirtualFile resolveToExistingFile(String path) {
        VirtualFile file = vfs.resolve(path);
        if (!file.exists()) {
            UserContext.get()
                       .addMessage(Message.error()
                                          .withTextMessage(NLS.fmtr("VFSController.unknownPath")
                                                              .set("path", path)
                                                              .format()));

            while (file != null && !file.exists()) {
                file = file.parent();
            }

            if (!file.exists()) {
                file = vfs.root();
            }
        }

        return file;
    }

    private Page<VirtualFile> computeChildrenAsPage(WebContext webContext, VirtualFile file) {
        return file.tryAs(ChildPageProvider.class)
                   .map(provider -> provider.queryPage(file, webContext))
                   .orElseGet(() -> {
                       Page<VirtualFile> page = new Page<VirtualFile>().withStart(1).bindToRequest(webContext);
                       page.withLimitedItemsSupplier(limit -> {
                           List<VirtualFile> childFiles = new ArrayList<>();
                           file.children(FileSearch.iterateAll(childFiles::add)
                                                   .withPrefixFilter(page.getQuery())
                                                   .withLimit(limit));
                           return childFiles;
                       });

                       return page;
                   });
    }

    /**
     * Uploads a new file or replaces the contents of an existing one.
     *
     * @param webContext  the request to handle
     * @param output      the JSON response
     * @param inputStream the contents to process
     * @throws Exception in case of an error
     */
    @LoginRequired
    @Routed(value = "/fs/upload", preDispatchable = true)
    @InternalService
    @Permission(PERMISSION_VIEW_FILES)
    public void upload(WebContext webContext, JSONStructuredOutput output, InputStreamHandler inputStream)
            throws Exception {
        VirtualFile parent = vfs.resolve(webContext.get("path").asString());
        parent.assertExistingDirectory();

        String filename = webContext.get("filename").asString(webContext.get("qqfile").asString());
        VirtualFile file = parent.resolve(filename);

        boolean exists = file.exists();
        if (exists) {
            file.assertExistingFile();
        }

        try {
            webContext.markAsLongCall();
            long size = webContext.getHeaderValue(HttpHeaderNames.CONTENT_LENGTH).asLong(0);
            if (size > 0) {
                file.consumeStream(inputStream, size);
            } else {
                try (inputStream; OutputStream outputStream = file.createOutputStream()) {
                    webContext.markAsLongCall();
                    Streams.transfer(inputStream, outputStream);
                }
            }

            output.property("file", file.path());
            output.property("refresh", true);
        } catch (Exception exception) {
            if (!exists) {
                try {
                    file.delete();
                } catch (Exception innerException) {
                    Exceptions.ignore(innerException);
                }
            }

            throw Exceptions.createHandled().error(exception).handle();
        }
    }

    /**
     * Deletes the given file or directory.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs/delete")
    @Permission(PERMISSION_VIEW_FILES)
    public void delete(WebContext webContext) {
        VirtualFile file = vfs.resolve(webContext.get("path").asString());
        if (webContext.isSafePOST()) {
            try {
                if (file.exists()) {
                    file.delete();
                    showDeletedMessage();
                }
            } catch (Exception exception) {
                UserContext.handle(exception);
            }
            webContext.respondWith()
                      .redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
        } else {
            webContext.respondWith().redirectToGet("/fs");
        }
    }

    /**
     * Deletes the given list of files or directories.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs/delete/multiple")
    @Permission(PERMISSION_VIEW_FILES)
    public void deleteMulitple(WebContext webContext) {
        List<String> filePaths = Arrays.stream(webContext.get("paths").asString().split("[,;]"))
                                       .map(String::trim)
                                       .filter(Strings::isFilled)
                                       .toList();
        AtomicReference<String> workingDirectory = new AtomicReference<>();
        filePaths.forEach(filePath -> {
            VirtualFile file = vfs.resolve(filePath);
            workingDirectory.set(file.parent.path());
            if (webContext.isSafePOST()) {
                try {
                    if (file.exists()) {
                        file.delete();
                    }
                } catch (Exception exception) {
                    UserContext.handle(exception);
                }
            }
        });
        UserContext.get()
                   .addMessage(Message.info()
                                      .withTextMessage(NLS.fmtr("VFSController.deletedMultipleMessage")
                                                          .set("count", filePaths.size())
                                                          .format()));
        webContext.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", workingDirectory).toString());
    }

    /**
     * Renames the given file or directory.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs/rename")
    @Permission(PERMISSION_VIEW_FILES)
    public void rename(WebContext webContext) {
        VirtualFile file = vfs.resolve(webContext.get("path").asString());
        if (webContext.isSafePOST()) {
            try {
                String name = webContext.get("name").asString();
                if (file.exists() && Strings.isFilled(name)) {
                    file.rename(name);
                    UserContext.message(Message.info().withTextMessage(NLS.get("VFSController.renamed")));
                }
            } catch (Exception exception) {
                UserContext.handle(exception);
            }
            webContext.respondWith()
                      .redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
        } else {
            webContext.respondWith().redirectToGet("/fs");
        }
    }

    /**
     * Creates a new directory.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs/createDirectory")
    @Permission(PERMISSION_VIEW_FILES)
    public void createDirectory(WebContext webContext) {
        VirtualFile parent = Optional.ofNullable(vfs.resolve(webContext.get("parent").asString()))
                                     .filter(VirtualFile::exists)
                                     .filter(VirtualFile::isDirectory)
                                     .orElse(null);
        if (webContext.isSafePOST()) {
            try {
                String name = webContext.get("name").asString();
                if (Strings.isEmpty(name)) {
                    name = NLS.get("VFSController.createDirectory");
                }
                VirtualFile newDirectory = parent.resolve(name);
                newDirectory.createAsDirectory();
                UserContext.message(Message.info().withTextMessage(NLS.get("VFSController.directoryCreated")));
                webContext.respondWith()
                          .redirectToGet(new LinkBuilder("/fs").append("path", newDirectory.path()).toString());
            } catch (Exception exception) {
                UserContext.handle(exception);
                webContext.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", parent.path()).toString());
            }
        } else {
            webContext.respondWith().redirectToGet("/fs");
        }
    }

    /**
     * Moves the given file or directory to a new parent directory.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs/move")
    @Permission(PERMISSION_VIEW_FILES)
    public void move(WebContext webContext) {
        VirtualFile file = vfs.resolve(webContext.get("path").asString());
        VirtualFile newParent = vfs.resolve(webContext.get("newParent").asString());
        if (!file.exists()) {
            webContext.respondWith().redirectToGet("/fs");
            return;
        }

        try {
            if (newParent.exists() && newParent.isDirectory()) {
                Optional<String> processId = file.transferTo(newParent).move();
                if (processId.isPresent()) {
                    UserContext.message(Message.info()
                                               .withTextAndLink(NLS.get("VFSController.movedInProcess"),
                                                                NLS.get("VFSController.moveProcess"),
                                                                "/ps/" + processId.get()));
                } else {
                    UserContext.message(Message.info().withTextMessage(NLS.get("VFSController.moved")));
                }
            }
        } catch (Exception exception) {
            UserContext.handle(exception);
        }

        webContext.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
    }

    /**
     * Sets the read-only flag of the given file to false.
     *
     * @param webContext the request to handle
     */
    @LoginRequired
    @Routed("/fs/unlock")
    @Permission(VirtualFileSystem.PERMISSION_UNLOCK_FILES)
    public void unlock(WebContext webContext) {
        VirtualFile file = vfs.resolve(webContext.get("path").asString());
        if (!file.exists()) {
            webContext.respondWith().redirectToGet("/fs");
            return;
        }

        try {
            file.updateReadOnlyFlag(false);
        } catch (Exception exception) {
            UserContext.handle(exception);
        }

        webContext.respondWith().redirectToGet(new LinkBuilder("/fs").append("path", file.parent().path()).toString());
    }

    /**
     * Provides a JSON API which lists the contents of a given directory.
     * <p>
     * This is used by the selectVFSFile or selectVFSDirectory JavaScript calls/modals.
     *
     * @param webContext the request to handle
     * @param output     the JSON response to populate
     */
    @LoginRequired
    @Routed("/fs/list")
    @InternalService
    @Permission(PERMISSION_VIEW_FILES)
    public void listAPI(WebContext webContext, JSONStructuredOutput output) {
        VirtualFile parent = vfs.resolve(webContext.get("path").asString("/"));
        if (parent.exists() && !parent.isDirectory()) {
            parent = parent.parent();
        }
        parent.assertExistingDirectory();

        outputPath(output, parent);
        outputChildren(webContext, output, parent);

        output.property("canCreateChildren", parent.canCreateChildren());
    }

    private void outputChildren(WebContext webContext, JSONStructuredOutput output, VirtualFile parent) {
        output.beginArray("children");

        FileSearch search = FileSearch.iterateAll(child -> outputFile(output, child.name(), child));
        if (webContext.get("onlyDirectories").asBoolean()) {
            search.withOnlyDirectories();
        }
        if (webContext.get("skipReadOnlyFiles").asBoolean()) {
            search.skipReadOnlyFiles();
        }
        search.withPrefixFilter(webContext.get("filter").asString());
        webContext.get("extensions").ifFilled(extensionString -> {
            Arrays.stream(extensionString.asString().split(","))
                  .map(Strings::trim)
                  .filter(Strings::isFilled)
                  .map(string -> Files.isConsideredHidden(string) ? string.substring(1) : string)
                  .forEach(search::withFileExtension);
        });

        // because of the additional ".."-entry for the parent, we need to adjust the pagination skip/limit
        boolean hasParent = parent.parent() != null;
        int skip = webContext.get("skip").asInt(0);
        Integer limit = webContext.get("maxItems").getInteger();
        if (hasParent) {
            if (skip > 0) {
                skip--;
            } else {
                outputFile(output, PARENT_DIR, parent.parent());
                if (limit != null) {
                    limit--;
                }
            }
        }
        search.withLimit(new Limit(skip, limit));

        parent.children(search);
        output.endArray();
    }

    private void outputPath(JSONStructuredOutput output, VirtualFile parent) {
        output.beginArray("path");
        for (VirtualFile element : parent.pathList()) {
            output.beginObject("element");
            output.property("name", element.name());
            output.property("path", element.path());
            output.endObject();
        }
        output.endArray();
    }

    private void outputFile(JSONStructuredOutput output, String name, VirtualFile child) {
        output.beginObject("child");
        try {
            output.property("name", name);
            output.property("path", child.path());
            output.property("directory", child.isDirectory());
            output.property("size", child.isDirectory() ? 0 : child.size());
            output.property("sizeString", child.isDirectory() ? "" : NLS.formatSize(child.size()));
            output.property("lastModified", child.isDirectory() ? null : NLS.toMachineString(child.lastModifiedDate()));
            output.property("lastModifiedString",
                            child.isDirectory() ? "" : NLS.toUserString(child.lastModifiedDate()));
            output.property("lastModifiedSpokenString",
                            child.isDirectory() ? "" : NLS.toSpokenDate(child.lastModifiedDate()));
        } finally {
            output.endObject();
        }
    }
}
