/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.ftp

import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.Ftplet
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ssl.SslConfiguration
import org.apache.ftpserver.ssl.SslConfigurationFactory
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.storage.layer3.FileSearch
import sirius.biz.storage.layer3.VirtualFile
import sirius.biz.storage.layer3.VirtualFileSystem
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Value
import sirius.kernel.di.std.Part
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests the FTPS uplink with enabled TLS session reuse against an FTPS server running within the test.
 *
 * With session reuse, the uplink replaces the TLS implementation of the JDK by Bouncy Castle JSSE. Each test
 * therefore runs a real handshake and real transfers through it, which pins the behaviour we rely on whenever
 * Bouncy Castle is updated - most notably that data connections resume the session of the control connection, as
 * servers like vsftpd or FileZilla Server reject them otherwise.
 */
@ExtendWith(SiriusExtension::class)
class FTPSUplinkTest {

    private val controlConnections = AtomicInteger()
    private val previousTrustStore: String? = System.getProperty(TRUST_STORE_PROPERTY)

    private var server: FtpServer? = null
    private var sslConfiguration: SslConfiguration? = null
    private var serverPort = 0
    private var uplinkRoot: VirtualFile? = null

    @Test
    fun `file written through the FTPS uplink arrives in the served directory`() {
        resolveViaFtps("written.txt").consumeStream("Hello from the client".byteInputStream(), 21)

        assertEquals("Hello from the client", File(serverDirectory, "written.txt").readText())
    }

    @Test
    fun `file served by the FTPS server is read through the FTPS uplink`() {
        File(serverDirectory, "served.txt").writeText("Hello from the server")

        val file = resolveViaFtps("served.txt")

        assertTrue(file.exists())
        assertEquals("Hello from the server", file.createInputStream().use { it.readBytes().decodeToString() })
    }

    @Test
    fun `directory listing through the FTPS uplink shows served files and directories`() {
        File(serverDirectory, "listed.txt").writeText("listed")
        File(serverDirectory, "listed-directory").mkdirs()

        val children = mutableMapOf<String, Boolean>()
        uplinkRoot!!.children(FileSearch.iterateAll { children[it.name()] = it.isDirectory })

        assertEquals(false, children["listed.txt"])
        assertEquals(true, children["listed-directory"])
    }

    @Test
    fun `data connections resume the TLS session of the control connection`() {
        File(serverDirectory, "served.txt").writeText("Hello from the server")

        resolveViaFtps("served.txt").createInputStream().use { it.readBytes() }
        resolveViaFtps("written.txt").consumeStream("Hello from the client".byteInputStream(), 21)

        // Each control connection negotiates one TLS session. If the data connections resume it, the server
        // never gets to see another one, no matter how many transfers have been performed. Note that the uplink
        // opens several control connections for these transfers, as none of them is kept in its pool...
        assertTrue(controlConnections.get() > 0)
        assertEquals(controlConnections.get(), countTlsSessionsOfServer())
    }

    @BeforeEach
    fun setUp() {
        serverDirectory.deleteRecursively()
        serverDirectory.mkdirs()

        trustCertificate(startServer())
        uplinkRoot = createUplinkRoot()
    }

    @AfterEach
    fun tearDown() {
        try {
            if (previousTrustStore == null) {
                System.clearProperty(TRUST_STORE_PROPERTY)
            } else {
                System.setProperty(TRUST_STORE_PROPERTY, previousTrustStore)
            }
        } finally {
            server?.stop()
        }
    }

    private fun resolveViaFtps(name: String): VirtualFile {
        return uplinkRoot!!.resolve(name)
    }

    /**
     * Creates an FTPS uplink with enabled session reuse which points to the server of the current test.
     *
     * The uplink is created via its factory, just like the ones defined in the system configuration. It doesn't keep
     * idle connections in its pool, as nothing would close them once the server of the test is stopped.
     */
    private fun createUplinkRoot(): VirtualFile {
        val config = mapOf(
            "host" to "localhost",
            "port" to serverPort.toString(),
            "user" to USER,
            "password" to PASSWORD,
            "sslSessionReuse" to "true",
            "maxIdle" to "0"
        )
        val uplink = FTPSUplink.Factory().make("ftps-test-$serverPort") { key -> Value.of(config[key]) }

        return uplink.getFile(vfs!!.root())
    }

    /**
     * Starts an FTPS server which serves [serverDirectory] using a newly created self-signed certificate.
     *
     * A new server is started for each test, so that the connections and TLS sessions it has seen can be attributed
     * to the test.
     *
     * @return the certificate the server identifies itself with
     */
    private fun startServer(): X509Certificate {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val subject = X500Name("CN=localhost")
        val now = Instant.now()
        val certificate = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                subject,
                BigInteger.ONE,
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(Duration.ofDays(1))),
                subject,
                keyPair.public
            ).build(JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private))
        )

        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("ftps-test", keyPair.private, STORE_PASSWORD.toCharArray(), arrayOf(certificate))
            keyStoreFile.outputStream().use { store(it, STORE_PASSWORD.toCharArray()) }
        }

        val serverSslConfiguration = SslConfigurationFactory().apply {
            keystoreFile = keyStoreFile
            keystorePassword = STORE_PASSWORD
        }.createSslConfiguration()
        sslConfiguration = serverSslConfiguration

        val listener = ListenerFactory().apply {
            port = 0
            sslConfiguration = serverSslConfiguration
        }.createListener()

        val userManager = PropertiesUserManagerFactory().apply {
            passwordEncryptor = ClearTextPasswordEncryptor()
        }.createUserManager()
        userManager.save(BaseUser().apply {
            name = USER
            password = PASSWORD
            homeDirectory = serverDirectory.absolutePath
            authorities = listOf(WritePermission())
        })

        server = FtpServerFactory().apply {
            addListener("default", listener)
            this.userManager = userManager
            ftplets = mutableMapOf<String, Ftplet>("counter" to object : DefaultFtplet() {
                override fun onConnect(session: FtpSession): FtpletResult {
                    controlConnections.incrementAndGet()
                    return FtpletResult.DEFAULT
                }
            })
        }.createServer()
        server!!.start()
        serverPort = listener.port

        return certificate
    }

    /**
     * Makes the default trust store contain the given certificate in addition to the certificates of the JDK.
     *
     * With session reuse, the uplink verifies the certificate of the server against the default trust store, which
     * Bouncy Castle JSSE looks up whenever a TLS context is created. The certificates of the JDK are kept, so that
     * anything else which happens to set up TLS while a test is running remains unaffected.
     */
    private fun trustCertificate(certificate: X509Certificate) {
        val defaultTrustStore = File(System.getProperty("java.home"), "lib/security/cacerts")

        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            defaultTrustStore.inputStream().use { load(it, null) }
            setCertificateEntry("ftps-test", certificate)
            trustStoreFile.outputStream().use { store(it, STORE_PASSWORD.toCharArray()) }
        }

        System.setProperty(TRUST_STORE_PROPERTY, trustStoreFile.absolutePath)
    }

    /**
     * Counts the TLS sessions the server has negotiated since it was started.
     *
     * A resumed session keeps its ID and therefore doesn't show up as another session. This relies on the server
     * keeping its TLS 1.2 sessions in its session cache, which it only does for clients not offering the session
     * ticket extension - like Bouncy Castle JSSE. Should that ever change, the cache remains empty and the test
     * fails instead of passing by accident.
     */
    private fun countTlsSessionsOfServer(): Int {
        return sslConfiguration!!.sslContext.serverSessionContext.ids.toList().size
    }

    companion object {
        private const val DIRECTORY_NAME = "ftps-uplink-test"
        private const val USER = "test"
        private const val PASSWORD = "test"
        private const val STORE_PASSWORD = "changeit"
        private const val TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore"

        private val serverDirectory = File("target", DIRECTORY_NAME)
        private val keyStoreFile = File("target", "ftps-test-keystore.p12")
        private val trustStoreFile = File("target", "ftps-test-truststore.p12")

        @Part
        private var vfs: VirtualFileSystem? = null
    }
}
