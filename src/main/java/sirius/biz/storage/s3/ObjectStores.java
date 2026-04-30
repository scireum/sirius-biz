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
import sirius.kernel.commons.Strings;
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
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder;

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
    private static final Region DEFAULT_REGION = Region.EU_CENTRAL_1;

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

        S3Client newClient = createClient(name, extension);
        S3AsyncClient newAsyncClient = createAsyncClient(name, extension);
        S3Presigner newPresigner = createPresigner(name, extension);
        result = new ObjectStore(this,
                                 name,
                                 newClient,
                                 newAsyncClient,
                                 newPresigner,
                                 extension.get(KEY_BUCKET_SUFFIX).asString());

        stores.put(name, result);

        return result;
    }

    protected S3Client createClient(String name, Settings extension) {
        if (!extension.get(KEY_SIGNER).isEmptyString()) {
            LOG.WARN("Ignoring legacy signer override '%s' for object store '%s'. AWS SDK v2 uses SigV4 by default.",
                     extension.get(KEY_SIGNER).asString(),
                     name);
        }

        StaticCredentialsProvider credentialsProvider = createCredentialsProvider(extension);
        S3Configuration serviceConfiguration =
                S3Configuration.builder().pathStyleAccessEnabled(extension.get(KEY_PATH_STYLE_ACCESS).asBoolean()).build();
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                                                                     .socketTimeout(extension.getDuration(
                                                                             KEY_SOCKET_TIMEOUT))
                                                                     .connectionTimeout(extension.getDuration(
                                                                             KEY_CONNECTION_TIMEOUT))
                                                                     .maxConnections(extension.getInt(
                                                                             KEY_MAX_CONNECTIONS));
        Duration connectionTTL = extension.getDuration(KEY_CONNECTION_TTL);
        if (connectionTTL.isPositive()) {
            httpClientBuilder.connectionTimeToLive(connectionTTL);
        }

        S3ClientBuilder clientBuilder = S3Client.builder()
                                                .serviceConfiguration(serviceConfiguration)
                                                .credentialsProvider(credentialsProvider)
                                                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                                                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                                                .httpClientBuilder(httpClientBuilder);

        try {
            URI endpoint = new URI(extension.get(KEY_END_POINT).asString());
            int defaultPort =
                    PROTOCOL_HTTPS.equalsIgnoreCase(endpoint.getScheme()) ? DEFAULT_PORT_HTTPS : DEFAULT_PORT_HTTP;
            clientBuilder.endpointOverride(mapEndpoint(name, endpoint, defaultPort))
                         .region(determineRegion(endpoint));
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

    protected S3AsyncClient createAsyncClient(String name, Settings extension) {
        StaticCredentialsProvider credentialsProvider = createCredentialsProvider(extension);
        S3Configuration serviceConfiguration =
                S3Configuration.builder().pathStyleAccessEnabled(extension.get(KEY_PATH_STYLE_ACCESS).asBoolean()).build();
        NettyNioAsyncHttpClient.Builder httpClientBuilder = NettyNioAsyncHttpClient.builder()
                                                                                   .readTimeout(extension.getDuration(
                                                                                           KEY_SOCKET_TIMEOUT))
                                                                                   .connectionTimeout(extension.getDuration(
                                                                                           KEY_CONNECTION_TIMEOUT))
                                                                                   .maxConcurrency(extension.getInt(
                                                                                           KEY_MAX_CONNECTIONS));
        Duration connectionTTL = extension.getDuration(KEY_CONNECTION_TTL);
        if (connectionTTL.isPositive()) {
            httpClientBuilder.connectionTimeToLive(connectionTTL);
        }

        S3AsyncClientBuilder clientBuilder = S3AsyncClient.builder()
                                                          .serviceConfiguration(serviceConfiguration)
                                                          .credentialsProvider(credentialsProvider)
                                                          .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                                                          .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                                                          .httpClientBuilder(httpClientBuilder);
        try {
            URI endpoint = new URI(extension.get(KEY_END_POINT).asString());
            int defaultPort =
                    PROTOCOL_HTTPS.equalsIgnoreCase(endpoint.getScheme()) ? DEFAULT_PORT_HTTPS : DEFAULT_PORT_HTTP;
            clientBuilder.endpointOverride(mapEndpoint(name, endpoint, defaultPort))
                         .region(determineRegion(endpoint));
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

    protected S3Presigner createPresigner(String name, Settings extension) {
        StaticCredentialsProvider credentialsProvider = createCredentialsProvider(extension);
        S3Configuration serviceConfiguration =
                S3Configuration.builder().pathStyleAccessEnabled(extension.get(KEY_PATH_STYLE_ACCESS).asBoolean()).build();
        Builder presignerBuilder = S3Presigner.builder()
                                              .serviceConfiguration(serviceConfiguration)
                                              .credentialsProvider(credentialsProvider);
        try {
            URI endpoint = new URI(extension.get(KEY_END_POINT).asString());
            int defaultPort =
                    PROTOCOL_HTTPS.equalsIgnoreCase(endpoint.getScheme()) ? DEFAULT_PORT_HTTPS : DEFAULT_PORT_HTTP;
            presignerBuilder.endpointOverride(mapEndpoint(name, endpoint, defaultPort))
                            .region(determineRegion(endpoint));
        } catch (URISyntaxException exception) {
            throw Exceptions.handle()
                            .error(exception)
                            .to(LOG)
                            .withSystemErrorMessage("Invalid endpoint for object store %s: %s - %s (%s)",
                                                    name,
                                                    extension.get(KEY_END_POINT).asString())
                            .handle();
        }

        return presignerBuilder.build();
    }

    private StaticCredentialsProvider createCredentialsProvider(Settings extension) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(extension.get(KEY_ACCESS_KEY).asString(),
                                                                          extension.get(KEY_SECRET_KEY).asString()));
    }

    private Region determineRegion(URI endpoint) {
        String host = endpoint.getHost();
        if (host == null) {
            return DEFAULT_REGION;
        }

        if (host.startsWith("s3.")) {
            String region = extractRegion(host, "s3.".length());
            return Strings.isFilled(region) && !Strings.areEqual(region, "amazonaws") ? Region.of(region) : DEFAULT_REGION;
        }

        if (host.startsWith("s3-")) {
            String region = extractRegion(host, "s3-".length());
            return Strings.isFilled(region) ? Region.of(region) : DEFAULT_REGION;
        }

        return DEFAULT_REGION;
    }

    private String extractRegion(String host, int start) {
        int end = host.indexOf('.', start);
        if (end < 0) {
            return null;
        }

        return host.substring(start, end);
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
