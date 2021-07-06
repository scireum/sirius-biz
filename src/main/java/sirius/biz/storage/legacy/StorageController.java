/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.protocol.TraceData;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.biz.web.BizController;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.web.controller.AutocompleteHelper;
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
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
@Register(framework = Storage.FRAMEWORK_STORAGE)
public class StorageController extends BizController {

    private static final String NO_REFERENCE = "-";
    private static final String RESPONSE_FILE_ID = "fileId";
    private static final String RESPONSE_REFRESH = "refresh";

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
           .template("/templates/biz/storage/buckets.html.pasta",
                     storage.getBuckets()
                            .stream()
                            .filter(bucket -> currentUser.hasPermission(bucket.getPermission()))
                            .collect(Collectors.toList()));
    }

    /**
     * Lists all objects of the given bucket.
     *
     * @param ctx        the request to handle
     * @param bucketName the bucket to list
     */
    @Routed("/storage/bucket/:1")
    @LoginRequired
    public void listObjects(WebContext ctx, String bucketName) {
        BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
        if (isBucketUnaccessible(bucket)) {
            throw cannotAccessBucketException(bucketName);
        }

        SmartQuery<VirtualObject> baseQuery = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.BUCKET, bucket.getName())
                                                 .eq(VirtualObject.REFERENCE, null)
                                                 .ne(VirtualObject.PATH, null)
                                                 .orderDesc(VirtualObject.TRACE.inner(TraceData.CHANGED_AT));

        applyQuery(ctx.get("query").asString(), baseQuery, bucket.isAlwaysUseLikeSearch(), false);

        SQLPageHelper<VirtualObject> pageHelper =
                SQLPageHelper.withQuery(tenants.forCurrentTenant(baseQuery)).withContext(ctx);

        ctx.respondWith().template("/templates/biz/storage/objects.html.pasta", bucket, pageHelper.asPage());
    }

    private boolean isBucketUnaccessible(BucketInfo bucket) {
        return bucket == null || !UserContext.getCurrentUser().hasPermission(bucket.getPermission());
    }

    private HandledException cannotAccessBucketException(String bucketName) {
        return Exceptions.createHandled()
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
            result.accept(AutocompleteHelper.suggest(query));
            return;
        }

        SmartQuery<VirtualObject> baseQuery = oma.select(VirtualObject.class)
                                                 .eq(VirtualObject.TENANT, tenants.getRequiredTenant())
                                                 .eq(VirtualObject.BUCKET, bucket.getName())
                                                 .eq(VirtualObject.REFERENCE, null)
                                                 .ne(VirtualObject.PATH, null)
                                                 .orderDesc(VirtualObject.TRACE.inner(TraceData.CHANGED_AT))
                                                 .limit(10);

        applyQuery(query, baseQuery, bucket.isAlwaysUseLikeSearch(), true);
        for (VirtualObject object : baseQuery.queryList()) {
            result.accept(AutocompleteHelper.suggest(object.getObjectKey())
                                            .withFieldLabel(object.getFilename())
                                            .withCompletionDescription(object.getPath()));
        }
    }

    /**
     * We have to build a custom query here, as the path always starts with a <tt>/</tt>
     * but most user will search for a filename without a leading slash. Therefore we
     * gracefully fix this.
     *
     * @param query               the query string to apply
     * @param baseQuery           the query to expand
     * @param alwaysUseLikeSearch determines whether we always use a like on for matching
     * @param autocomplete        determines if the query is for an autocomplete request
     */
    private void applyQuery(String query,
                            SmartQuery<VirtualObject> baseQuery,
                            boolean alwaysUseLikeSearch,
                            boolean autocomplete) {
        if (Strings.isEmpty(query)) {
            return;
        }

        String queryString = query;
        if (alwaysUseLikeSearch) {
            baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.like(VirtualObject.PATH).contains(queryString).build(),
                                           OMA.FILTERS.like(VirtualObject.OBJECT_KEY).matches(query).build()));
            return;
        }
        if (!(queryString.startsWith("*") || queryString.startsWith("/"))) {
            queryString = "/" + queryString;
        }

        if (queryString.contains("*")) {
            baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.like(VirtualObject.PATH).matches(queryString).build(),
                                           OMA.FILTERS.like(VirtualObject.OBJECT_KEY).matches(query).build()));
        } else if (autocomplete) {
            baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.like(VirtualObject.PATH).startsWith(queryString).build(),
                                           OMA.FILTERS.like(VirtualObject.OBJECT_KEY).startsWith(query).build()));
        } else {
            baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.eq(VirtualObject.PATH, queryString),
                                           OMA.FILTERS.eq(VirtualObject.OBJECT_KEY, query)));
        }
    }

    /**
     * Provides a detail view for an object within a bucket.
     *
     * @param ctx        the request to handle
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
        if (isBucketUnaccessible(bucket) || (ctx.isUnsafePOST() && !bucket.isCanEdit())) {
            throw cannotAccessBucketException(virtualObject.getBucket());
        }

        ctx.respondWith()
           .template("/templates/biz/storage/object.html.pasta",
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
     * @param ctx        the request to handle
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
                throw cannotAccessBucketException(bucketName);
            }

            String name = ctx.get("filename").asString(ctx.get("qqfile").asString());
            if (bucket.isCanCreate()) {
                file = storage.findOrCreateObjectByPath((SQLTenant) tenants.getRequiredTenant(), bucketName, name);
            } else {
                file = storage.findByPath((SQLTenant) tenants.getRequiredTenant(), bucketName, name)
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

            out.property(RESPONSE_FILE_ID, file.getObjectKey());
            out.property(RESPONSE_REFRESH, true);
        } catch (Exception e) {
            storage.delete(file);
            throw Exceptions.createHandled().error(e).handle();
        }
    }

    /**
     * Uploads new contents for the given file.
     *
     * @param ctx        the request to handle
     * @param out        the response to the AJAX call
     * @param bucketName the name of the bucket to upload to
     * @param objectId   the id of the object for replace
     * @param upload     the upload to handle
     */
    @Routed(value = "/storage/replace/:1/:2", preDispatchable = true, jsonCall = true)
    @LoginRequired
    public void uploadObject(final WebContext ctx,
                             JSONStructuredOutput out,
                             String bucketName,
                             String objectId,
                             InputStreamHandler upload) {
        try {
            BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
            if (isBucketUnaccessible(bucket) || !bucket.isCanEdit()) {
                throw cannotAccessBucketException(bucketName);
            }

            StoredObject file = storage.findByKey((SQLTenant) tenants.getRequiredTenant(), bucketName, objectId)
                                       .orElseThrow(() -> cannotAccessBucketException(bucketName));

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

            out.property(RESPONSE_FILE_ID, file.getObjectKey());
            out.property(RESPONSE_REFRESH, true);
        } catch (Exception e) {
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
                throw cannotAccessBucketException(bucketName);
            }
            String name = ctx.get("filename").asString(ctx.get("qqfile").asString());
            file = storage.createTemporaryObject((SQLTenant) tenants.getRequiredTenant(),
                                                 bucketName,
                                                 NO_REFERENCE.equals(reference) ? null : reference,
                                                 name);
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

            out.property(RESPONSE_FILE_ID, file.getObjectKey());
            out.property("previewUrl", file.prepareURL().buildURL().orElse(""));
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
            throw cannotAccessBucketException(virtualObject.getBucket());
        }

        storage.delete(object);
        showDeletedMessage();
        listObjects(ctx, virtualObject.getBucket());
    }

    /**
     * Removes the reference binding for the given object.
     *
     * @param ctx        the request to handle
     * @param bucketName the bucket in which the object resides
     * @param objectKey  the unique object key
     */
    @Routed("/storage/unreference/:1/:2")
    public void unreferenceObject(WebContext ctx, String bucketName, String objectKey) {
        BucketInfo bucket = storage.getBucket(bucketName).orElse(null);
        if (isBucketUnaccessible(bucket) || !bucket.isCanDelete()) {
            throw cannotAccessBucketException(bucketName);
        }

        StoredObject object = findObjectByKey(bucketName, objectKey);
        VirtualObject virtualObject = (VirtualObject) object;
        assertTenant(virtualObject);

        virtualObject.setReference(null);
        oma.update(virtualObject);

        ctx.respondWith().redirectToGet(Strings.apply("/storage/object/%s/%s", bucketName, objectKey));
    }

    private StoredObject findObjectByKey(String bucket, String objectKey) {
        return storage.findByKey((SQLTenant) tenants.getRequiredTenant(), bucket, objectKey)
                      .orElseThrow(() -> Exceptions.createHandled()
                                                   .withNLSKey("BizController.unknownObject")
                                                   .set("type", StoredObject.class)
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
        Tuple<String, String> keyAndExtension = determineKeyAndExtension(physicalFileKey);
        String key = keyAndExtension.getFirst();

        if (!storage.verifyHash(key, authHash)) {
            ctx.respondWith().error(HttpResponseStatus.FORBIDDEN);
            return;
        }

        storage.deliverPhysicalFile(ctx, bucket, key, keyAndExtension.getSecond());
    }

    private Tuple<String, String> determineKeyAndExtension(String physicalFileKey) {
        if (physicalFileKey.contains("--")) {
            String effectiveKey = Strings.splitAtLast(physicalFileKey, "--").getSecond();
            return Strings.splitAtLast(effectiveKey, ".");
        }

        return Strings.splitAtLast(physicalFileKey, ".");
    }
}
