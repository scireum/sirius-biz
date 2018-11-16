/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import com.amazonaws.services.s3.model.ObjectMetadata;
import sirius.biz.s3.BucketName;
import sirius.biz.s3.ObjectStore;
import sirius.biz.s3.ObjectStores;
import sirius.db.KeyGenerator;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.WebContext;

import java.io.File;

//TODO maybe we should directly use Storage..
@Register
public class ObjectStoreFileStorage implements ProcessFileStorage {

    @Part
    private ObjectStores objectStores;

    @Part
    private KeyGenerator keyGen;

    @Override
    public ProcessFile upload(Process process, String filename, File data) {
        ObjectStore store = objectStores.store();
        BucketName bucket = getBucket(process, store);

        String id = keyGen.generateId();
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.addUserMetadata("process", process.getId());
        objectMetadata.addUserMetadata("tenant", process.getTenantId());
        objectMetadata.addUserMetadata("filename", filename);
        store.upload(bucket, getObjectId(process, id), data, objectMetadata);

        return new ProcessFile().withFileId(id).withFilename(filename).withSize(data.length());
    }

    private BucketName getBucket(Process process, ObjectStore store) {
        return store.getBucketForYear("process-files", process.getStarted().getYear());
    }

    @Override
    public File download(Process process, ProcessFile file) {
        ObjectStore store = objectStores.store();
        BucketName bucket = getBucket(process, store);

        return store.download(bucket, getObjectId(process, file.getFileId()));
    }

    @Override
    public void serve(WebContext request, Process process, ProcessFile file) {
        ObjectStore store = objectStores.store();
        BucketName bucket = getBucket(process, store);

        request.respondWith()
               .named(file.getFilename())
               .tunnel(store.objectUrl(bucket, getObjectId(process, file.getFileId())));
    }

    private String getObjectId(Process process, String fileId) {
        return process.getId() + "/" + fileId;
    }

    @Override
    public void delete(Process process, ProcessFile file) {
        ObjectStore store = objectStores.store();
        BucketName bucket = getBucket(process, store);

        store.deleteObject(bucket, getObjectId(process, file.getFileId()));
    }
}
