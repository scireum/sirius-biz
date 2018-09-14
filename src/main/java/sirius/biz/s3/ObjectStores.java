package sirius.biz.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import sirius.kernel.Sirius;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Average;
import sirius.kernel.health.Counter;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.kernel.settings.PortMapper;
import sirius.kernel.settings.Settings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a thin layer above S3 (or compatible stores).
 * <p>
 * The configuration is read from <tt>s3.stores.[name]</tt> in the system configuration.
 */
@Register(classes = ObjectStores.class)
public class ObjectStores {

    /*
     * Extended socket timeout when talkting to our S3 store
     */
    private static final int LONG_SOCKET_TIMEOUT = 60 * 1000 * 5;
    private static final String STORES_EXTENSION_POINT = "s3.stores";
    private static final String KEY_BUCKET_SUFFIX = "bucketSuffix";
    private static final String KEY_ACCESS_KEY = "accessKey";
    private static final String KEY_SECRET_KEY = "secretKey";
    private static final String KEY_SIGNER = "signer";
    private static final String KEY_PATH_STYLE_ACCESS = "pathStyleAccess";
    private static final String KEY_END_POINT = "endPoint";

    /**
     * Contains the logger used for all concerns related to object stores
     */
    public static final Log LOG = Log.get("objectstore");

    private static final int DEFAULT_PORT_HTTPS = 443;
    private static final int DEFAULT_PORT_HTTP = 80;
    private static final String PROTOCOL_HTTPS = "https";
    private static final String SERVICE_PREFIX_S3 = "s3-";
    private static final String NAME_SYSTEM_STORE = "system";

    private ObjectStore store;
    private ConcurrentHashMap<String, ObjectStore> stores = new ConcurrentHashMap<>();

    protected Cache<Tuple<String, String>, Boolean> bucketCache = CacheManager.createLocalCache("objectstores-buckets");

    protected Average uploads = new Average();
    protected Counter failedUploads = new Counter();
    protected Counter uploadedBytes = new Counter();

    protected Average downloads = new Average();
    protected Counter failedDownloads = new Counter();
    protected Counter downloadedBytes = new Counter();

    protected Average tunnels = new Average();
    protected Counter tunnelledBytes = new Counter();

    /**
     * Provides acccess to the default or <tt>system</tt> store.
     *
     * @return the object store which uses the configuration of <tt>system</tt>
     */
    public ObjectStore store() {
        if (store == null) {
            store = createAndStore(NAME_SYSTEM_STORE);
        }

        return store;
    }

    /**
     * Returns the object store with the given name.
     *
     * @param name the name to determine the configuration to use.
     * @return the object store with the given configuration
     */
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
            throw Exceptions.handle().to(LOG).withSystemErrorMessage("Unknown object store: %s", name).handle();
        }

        AmazonS3Client newClient = createClient(name, extension);
        result = new ObjectStore(this, name, newClient, extension.get(KEY_BUCKET_SUFFIX).asString());

        stores.put(name, result);

        return result;
    }

    protected AmazonS3Client createClient(String name, Settings extension) {
        AWSCredentials credentials = new BasicAWSCredentials(extension.get(KEY_ACCESS_KEY).asString(),
                                                             extension.get(KEY_SECRET_KEY).asString());
        ClientConfiguration config = new ClientConfiguration().withSocketTimeout(LONG_SOCKET_TIMEOUT);
        if (!extension.get(KEY_SIGNER).isEmptyString()) {
            config.withSignerOverride(extension.get(KEY_SIGNER).asString());
        }

        AmazonS3Client newClient = new AmazonS3Client(credentials, config);
        if (extension.get(KEY_PATH_STYLE_ACCESS).asBoolean()) {
            newClient.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
        }

        try {
            URL endpoint = new URL(extension.get(KEY_END_POINT).asString());
            if (PROTOCOL_HTTPS.equalsIgnoreCase(endpoint.getProtocol())) {
                newClient.setEndpoint(mapEndpoint(name, endpoint, DEFAULT_PORT_HTTPS));
            } else {
                newClient.setEndpoint(mapEndpoint(name, endpoint, DEFAULT_PORT_HTTP));
            }
        } catch (MalformedURLException e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(LOG)
                            .withSystemErrorMessage("Invalid endpoint for object store %s: %s - %s (%s)",
                                                    name,
                                                    extension.get(KEY_END_POINT).asString())
                            .handle();
        }

        return newClient;
    }

    private String mapEndpoint(String name, URL endpoint, int defaultPort) throws MalformedURLException {
        Tuple<String, Integer> hostAndPort = PortMapper.mapPort(SERVICE_PREFIX_S3 + name,
                                                                endpoint.getHost(),
                                                                endpoint.getPort() < 0 ?
                                                                defaultPort :
                                                                endpoint.getPort());

        return new URL(endpoint.getProtocol(),
                       hostAndPort.getFirst(),
                       hostAndPort.getSecond(),
                       endpoint.getFile()).toString();
    }
}
