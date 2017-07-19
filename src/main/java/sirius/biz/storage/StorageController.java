/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.model.TraceData;
import sirius.biz.web.BizController;
import sirius.biz.web.PageHelper;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.db.mixing.constraints.Like;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.InputStreamHandler;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Provides a management UI for the storage system.
 */
@Register(classes = Controller.class)
public class StorageController extends BizController {

    public static final String NO_REFERENCE = "-";
    @Part
    private Storage storage;

    /**
     * Lists all buckets visible to the current user.
     *
     * @param ctx the request to handle
     */
    @DefaultRoute
    @Routed("/storage")
    @LoginRequired
    public void listBuckets(WebContext ctx) {
        UserInfo currentUser = UserContext.getCurrentUser();
        ctx.respondWith()
           .template("templates/storage/buckets.html.pasta",
                     storage.getBuckets()
                            .stream()
                            .filter(bucket -> currentUser.hasPermission(bucket.getPermission()))
                            .collect(Collectors.toList()));
    }

    /**
     * Lists all objects of the given bucket.
     *
     * @param ctx the request to handle
     */
    @Routed("/storage/bucket/:1")
    @LoginRequired
    public void listObjects(WebContext ctx, String bucketName) {
        BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
        if (isBucketUnaccessible(bucket)) {
            handleAccessError(bucketName);
        }

        SmartQuery<VirtualObject> baseQuery = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.BUCKET, bucket.getName())
                                                 .eq(VirtualObject.REFERENCE, null)
                                                 .where(FieldOperator.on(VirtualObject.PATH).notEqual(null))
                                                 .orderDesc(VirtualObject.TRACE.inner(TraceData.CHANGED_AT));

        applyQuery(ctx.get("query").asString(), baseQuery);

        PageHelper<VirtualObject> pageHelper = PageHelper.withQuery(baseQuery).withContext(ctx).forCurrentTenant();

        ctx.respondWith().template("templates/storage/objects.html.pasta", bucket, pageHelper.asPage());
    }

    private boolean isBucketUnaccessible(BucketInfo bucket) {
        return bucket == null || !UserContext.getCurrentUser().hasPermission(bucket.getPermission());
    }

    private void handleAccessError(String bucketName) {
        throw Exceptions.createHandled()
                        .withNLSKey("StorageController.cannotAccessBucket")
                        .set("bucket", bucketName)
                        .handle();
    }

    /**
     * Provides an autocomplete for objects within a bucket.
     *
     * @param ctx        the request to handle
     * @param bucketName the name of the bucket to search in
     */
    @Routed("/storage/autocomplete/:1")
    @LoginRequired
    public void autocompleteObjects(WebContext ctx, String bucketName) {
        AutocompleteHelper.handle(ctx, (query, result) -> findObjectsForQuery(bucketName, query, result));
    }

    private void findObjectsForQuery(String bucketName, String query, Consumer<AutocompleteHelper.Completion> result) {
        BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
        if (isBucketUnaccessible(bucket)) {
            return;
        }

        if (query.toLowerCase().startsWith("http")) {
            result.accept(new AutocompleteHelper.Completion(query, query, query));
            return;
        }

        SmartQuery<VirtualObject> baseQuery = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.TENANT, currentTenant())
                                                 .eq(VirtualObject.BUCKET, bucket.getName())
                                                 .eq(VirtualObject.REFERENCE, null)
                                                 .where(FieldOperator.on(VirtualObject.PATH).notEqual(null))
                                                 .orderDesc(VirtualObject.TRACE.inner(TraceData.CHANGED_AT))
                                                 .limit(10);

        applyQuery(query, baseQuery);
        for (VirtualObject object : baseQuery.queryList()) {
            result.accept(new AutocompleteHelper.Completion(object.getObjectKey(),
                                                            object.getFilename(),
                                                            object.getPath()));
        }
    }

    /**
     * We have to build a custom query here, as the path always starts with a <tt>/</tt>
     * but most user will search for a filename without a leading slash. Therefore we
     * gracefully fix this.
     *
     * @param query     the query string to apply
     * @param baseQuery the query to expand
     */
    private void applyQuery(String query, SmartQuery<VirtualObject> baseQuery) {
        if (Strings.isEmpty(query)) {
            return;
        }

        String queryString = query;
        if (!(queryString.startsWith("*") || queryString.startsWith("/"))) {
            queryString = "/" + queryString;
        }

        if (queryString.contains("*")) {
            baseQuery.where(Like.on(VirtualObject.PATH).matches(queryString));
        } else {
            baseQuery.eq(VirtualObject.PATH, queryString);
        }
    }

    /**
     * Provides a detai view for an object within a bucket.
     *
     * @param ctx        the reuqest to handle
     * @param bucketName the name of the bucket in which the object resides
     * @param objectKey  the key of the object
     */
    @Routed("/storage/object/:1/:2")
    @LoginRequired
    public void editObject(WebContext ctx, String bucketName, String objectKey) {
        StoredObject object = findObjectByKey(bucketName, objectKey);
        VirtualObject virtualObject = (VirtualObject) object;
        assertTenant(virtualObject);

        BucketInfo bucket = storage.getBucket(virtualObject.getBucket()).orElse(null);
        if (isBucketUnaccessible(bucket) || (ctx.isPOST() && !bucket.isCanEdit())) {
            handleAccessError(virtualObject.getBucket());
        }

        ctx.respondWith()
           .template("templates/storage/object.html.pasta",
                     bucket,
                     object,
                     oma.select(VirtualObjectVersion.class)
                        .eq(VirtualObjectVersion.VIRTUAL_OBJECT, virtualObject)
                        .orderDesc(VirtualObjectVersion.CREATED_DATE)
                        .queryList());
    }

    /**
     * Uploads a new file / object to a bucket
     *
     * @param ctx        the reqest to handle
     * @param out        the response to the AJAX call
     * @param bucketName the name of the bucket to upload to
     * @param upload     the data being uploaded
     */
    @Routed(value = "/storage/upload/:1", preDispatchable = true, jsonCall = true)
    @LoginRequired
    public void uploadObject(final WebContext ctx,
                             JSONStructuredOutput out,
                             String bucketName,
                             InputStreamHandler upload) {
        StoredObject file = null;
        try {
            BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
            if (isBucketUnaccessible(bucket) || !bucket.isCanEdit()) {
                handleAccessError(bucketName);
            }

            String name = ctx.get("filename").asString(ctx.get("qqfile").asString());
            if (bucket.isCanCreate()) {
                file = storage.findOrCreateObjectByPath(currentTenant(), bucketName, name);
            } else {
                file = storage.findByPath(currentTenant(), bucketName, name)
                              .orElseThrow(() -> Exceptions.createHandled()
                                                           .withNLSKey("StorageController.cannotAccessBucket")
                                                           .set("bucket", bucketName)
                                                           .handle());
            }

            try {
                ctx.markAsLongCall();
                storage.updateFile(file,
                                   upload,
                                   null,
                                   null,
                                   Long.parseLong(ctx.getHeader(HttpHeaderNames.CONTENT_LENGTH)));
            } finally {
                upload.close();
            }

            out.property("fileId", file.getObjectKey());
        } catch (Exception e) {
            storage.delete(file);
            throw Exceptions.createHandled().error(e).handle();
        }
    }

    /**
     * Uploads an object for a reference (unique entity name).
     * <p>
     * If an object for this reference already exists, it is updated, otherwise a new one is created.
     * xx
     *
     * @param ctx        the request to handle
     * @param out        the response to the AJAX call
     * @param bucketName the bucket name to put the object into
     * @param reference  the reference for which an object is uploaded
     * @param upload     the content of the upload
     */
    @Routed(value = "/storage/upload-reference/:1/:2", preDispatchable = true, jsonCall = true)
    @LoginRequired
    public void uploadObjectForReference(final WebContext ctx,
                                         JSONStructuredOutput out,
                                         String bucketName,
                                         String reference,
                                         InputStreamHandler upload) {
        StoredObject file = null;
        try {
            BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
            if (bucket == null) {
                handleAccessError(bucketName);
            }
            String name = ctx.get("filename").asString(ctx.get("qqfile").asString());
            file = storage.createTemporaryObject(currentTenant(), bucketName, NO_REFERENCE.equals(reference) ? null : reference, name);
            try {
                ctx.markAsLongCall();
                storage.updateFile(file,
                                   upload,
                                   null,
                                   null,
                                   Long.parseLong(ctx.getHeader(HttpHeaderNames.CONTENT_LENGTH)));
            } finally {
                upload.close();
            }

            out.property("fileId", file.getObjectKey());
            out.property("url", file.prepareURL().buildURL());
        } catch (Exception e) {
            storage.delete(file);
            throw Exceptions.createHandled().error(e).handle();
        }
    }

    /**
     * Deletes the object in the given bucket with the given id.
     *
     * @param ctx        the request to handle
     * @param bucketName the bucket in which the object resides
     * @param objectKey  the unique object key
     */
    @Routed("/storage/delete/:1/:2")
    public void deleteObject(WebContext ctx, String bucketName, String objectKey) {
        StoredObject object = findObjectByKey(bucketName, objectKey);
        VirtualObject virtualObject = (VirtualObject) object;
        assertTenant(virtualObject);
        BucketInfo bucket = storage.getBucket(virtualObject.getBucket()).orElse(null);
        if (isBucketUnaccessible(bucket) || !bucket.isCanDelete()) {
            handleAccessError(virtualObject.getBucket());
        }

        storage.delete(object);
        showDeletedMessage();
        listObjects(ctx, virtualObject.getBucket());
    }

    private StoredObject findObjectByKey(String bucket, String objectKey) {
        return storage.findByKey(tenants.getRequiredTenant(), bucket, objectKey)
                      .orElseThrow(() -> Exceptions.createHandled()
                                                   .withNLSKey("BizController.unknownObject")
                                                   .set("id", objectKey)
                                                   .handle());
    }

    /**
     * Actually delivers a physical object.
     * <p>
     * This is the handler of the download URLS which are generated by default. The content is actually delivered by the
     * {@link PhysicalStorageEngine}.
     *
     * @param ctx             the request to handle
     * @param bucket          the bucket containing the object
     * @param authHash        the authentication hash which ensures access
     * @param physicalFileKey the physical object to download
     */
    @Routed("/storage/physical/:1/:2/:3")
    public void downloadPhysicalObject(WebContext ctx, String bucket, String authHash, String physicalFileKey) {
        Tuple<String, String> keyAndExtension = Strings.split(physicalFileKey, ".");
        if (!storage.verifyHash(keyAndExtension.getFirst(), authHash)) {
            ctx.respondWith().error(HttpResponseStatus.FORBIDDEN);
            return;
        }

        storage.deliverPhysicalFile(ctx, bucket, keyAndExtension.getFirst(), keyAndExtension.getSecond());
    }
}
