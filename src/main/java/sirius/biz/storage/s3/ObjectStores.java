/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3;

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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.util.AwsHostNameUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a thin layer above S3 (or compatible stores).
 * <p>
 * The configuration is read from <tt>s3.stores.[name]</tt> in the system configuration.
 */
@Register(classes = ObjectStores.class)
public class ObjectStores {

    private static final String STORES_EXTENSION_POINT = "s3.stores";
    private static final String KEY_BUCKET_SUFFIX = "bucketSuffix";
    private static final String KEY_ACCESS_KEY = "accessKey";
    private static final String KEY_SECRET_KEY = "secretKey";
    private static final String KEY_SIGNER = "signer";
    private static final String KEY_PATH_STYLE_ACCESS = "pathStyleAccess";
    private static final String KEY_END_POINT = "endPoint";
    private static final String KEY_SOCKET_TIMEOUT = "socketTimeout";
    private static final String KEY_CONNECTION_TIMEOUT = "connectionTimeout";
    private static final String KEY_MAX_CONNECTIONS = "maxConnections";
    private static final String KEY_CONNECTION_TTL = "connectionTTL";

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
    private final ConcurrentHashMap<String, ObjectStore> stores = new ConcurrentHashMap<>();

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
     * Provides access to the default or <tt>system</tt> store.
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

    /**
     * Determines if there is a (most probably) valid configuration present for the given object store.
     *
     * @param name the name of the store to check for a configuration
     * @return <tt>true</tt> if there is at least a configuration present, <tt>false</tt> otherwise
     */
    public boolean isConfigured(String name) {
        Extension extension = Sirius.getSettings().getExtension(STORES_EXTENSION_POINT, name);
        if (extension == null || extension.isDefault()) {
            return false;
        }

        return extension.get(KEY_END_POINT).isFilled();
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

        S3AsyncClient newClient = createClient(name, extension);
        result = new ObjectStore(this, name, newClient, extension.get(KEY_BUCKET_SUFFIX).asString());

        stores.put(name, result);

        return result;
    }

    protected S3AsyncClient createClient(String name, Settings extension) {
        // TODO SIRI-1025: Make relevant timeouts configurable again
        NettyNioAsyncHttpClient.Builder httpClientBuilder = NettyNioAsyncHttpClient.builder()
//                                                                                   .socketTimeout(extension.getDuration(
//                                                                                           KEY_SOCKET_TIMEOUT))
                                                                                   .connectionTimeout(extension.getDuration(
                                                                                           KEY_CONNECTION_TIMEOUT));
//                                                                                   .maxConnections(extension.getInt(
//                                                                                           KEY_MAX_CONNECTIONS));
        Duration connectionTTL = extension.getDuration(KEY_CONNECTION_TTL);
        if (connectionTTL.isPositive()) {
            httpClientBuilder.connectionTimeToLive(connectionTTL);
        }

        ClientOverrideConfiguration.Builder overrideBuilder = ClientOverrideConfiguration.builder();

        if (!extension.get(KEY_SIGNER).isEmptyString()) {
            // TODO SIRI-1025 still necessary?
//            overrideBuilder.putAdvancedOption(SdkAdvancedClientOption.SIGNER, extension.get(KEY_SIGNER).asString());
        }

        S3AsyncClientBuilder clientBuilder = S3AsyncClient.builder()
                                                          .forcePathStyle(extension.get(KEY_PATH_STYLE_ACCESS)
                                                                                   .asBoolean())
                                                          .credentialsProvider(StaticCredentialsProvider.create(
                                                                  AwsBasicCredentials.create(extension.get(
                                                                                                     KEY_ACCESS_KEY).asString(),
                                                                                             extension.get(
                                                                                                              KEY_SECRET_KEY)
                                                                                                      .asString())))
                                                          .httpClient(httpClientBuilder.build())
                                                          .overrideConfiguration(overrideBuilder.build());

        try {
            URI endpoint = URI.create(extension.get(KEY_END_POINT).asString());
            int defaultPort =
                    PROTOCOL_HTTPS.equalsIgnoreCase(endpoint.getScheme()) ? DEFAULT_PORT_HTTPS : DEFAULT_PORT_HTTP;
            AwsHostNameUtils.parseSigningRegion(endpoint.getHost(), S3Client.SERVICE_NAME)
                            .ifPresent(clientBuilder::region);
            clientBuilder.endpointOverride(mapEndpoint(name, endpoint, defaultPort));
        } catch (URISyntaxException exception) {
            throw Exceptions.handle()
                            .error(exception)
                            .to(LOG)
                            .withSystemErrorMessage("Invalid endpoint for object store %s: %s - %s (%s)",
                                                    name,
                                                    extension.get(KEY_END_POINT).asString())
                            .handle();
        }

        return clientBuilder.build();
    }

    private URI mapEndpoint(String name, URI endpoint, int defaultPort) throws URISyntaxException {
        Tuple<String, Integer> hostAndPort = PortMapper.mapPort(SERVICE_PREFIX_S3 + name,
                                                                endpoint.getHost(),
                                                                endpoint.getPort() < 0 ?
                                                                defaultPort :
                                                                endpoint.getPort());

        return new URI(endpoint.getScheme(),
                       endpoint.getUserInfo(),
                       hostAndPort.getFirst(),
                       Integer.valueOf(defaultPort).equals(hostAndPort.getSecond()) ? -1 : hostAndPort.getSecond(),
                       endpoint.getPath(),
                       endpoint.getQuery(),
                       endpoint.getFragment());
    }
}
