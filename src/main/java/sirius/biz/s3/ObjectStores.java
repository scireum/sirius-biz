package sirius.biz.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import org.jetbrains.annotations.NotNull;
import sirius.kernel.Sirius;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.kernel.settings.Settings;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a thin layer above S3 (or compatible stores).
 */
public class ObjectStores {

    /*
     * Extended socket timeout when talkting to our S3 store
     */
    private static final int LONG_SOCKET_TIMEOUT = 60 * 1000 * 5;
    private static final String STORES_EXTENSION_POINT = "storage.stores";
    private static final String KEY_BUCKET_SUFFIX = "bucketSuffix";
    private static final String KEY_ACCESS_KEY = "accessKey";
    private static final String KEY_SECRET_KEY = "secretKey";
    private static final String KEY_SIGNER = "signer";
    private static final String KEY_PATH_STYLE_ACCESS = "pathStyleAccess";
    private static final String KEY_END_POINT = "endPoint";

    private ObjectStore store;
    private ConcurrentHashMap<String, ObjectStore> stores = new ConcurrentHashMap<>();

    public ObjectStore store() {
        if (store == null) {
            store = createAndStore("system");
        }

        return store;
    }

    public ObjectStore getStore(String name) {
        ObjectStore result = stores.get(name);
        if (result != null) {
            return result;
        }

        return createAndStore(name);
    }

    protected synchronized ObjectStore createAndStore(String name) {
        ObjectStore result = stores.get(name);
        if (result != null) {
            return result;
        }

        Extension extension = Sirius.getSettings().getExtension(STORES_EXTENSION_POINT, name);
        if (extension == null || extension.isDefault()) {
            throw Exceptions.handle().withSystemErrorMessage("Unknown object store: %s", name).handle();
        }

        AmazonS3Client newClient = createClient(extension);
        result = new ObjectStore(newClient, extension.get(KEY_BUCKET_SUFFIX).asString());

        stores.put(name, result);

        return result;
    }

    @NotNull
    protected AmazonS3Client createClient(Settings extension) {
        AWSCredentials credentials =
                new BasicAWSCredentials(extension.get(KEY_ACCESS_KEY).asString(), extension.get(KEY_SECRET_KEY).asString());
        ClientConfiguration config = new ClientConfiguration().withSocketTimeout(LONG_SOCKET_TIMEOUT);
        if (!extension.get(KEY_SIGNER).isEmptyString()) {
            config.withSignerOverride(extension.get(KEY_SIGNER).asString());
        }

        AmazonS3Client newClient = new AmazonS3Client(credentials, config);
        if (extension.get(KEY_PATH_STYLE_ACCESS).asBoolean()) {
            newClient.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
        }
        newClient.setEndpoint(extension.get(KEY_END_POINT).asString());
        return newClient;
    }
}
