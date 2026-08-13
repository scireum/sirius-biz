/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Optional
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the [TransformingInputStream] along with the transformers applied by it.
 */
class TransformingInputStreamTest {

    @Test
    fun `closing an abandoned stream closes the source stream and the transformer`() {
        val source = RecordingInputStream("payload".toByteArray())
        val transformer = RecordingTransformer()

        TransformingInputStream(source, transformer).close()

        assertTrue(source.closed)
        assertTrue(transformer.closed)
    }

    @Test
    fun `closing a completely read stream closes the transformer`() {
        val source = RecordingInputStream("payload".toByteArray())
        val transformer = RecordingTransformer()

        val payload = TransformingInputStream(source, transformer).use { it.readBytes() }

        assertContentEquals("payload".toByteArray(), payload)
        assertTrue(source.closed)
        assertTrue(transformer.closed)
    }

    @Test
    fun `closing a stream twice is harmless`() {
        val transformer = RecordingTransformer()
        val stream = TransformingInputStream(RecordingInputStream("payload".toByteArray()), transformer)

        stream.close()
        stream.close()

        assertTrue(transformer.closed)
    }

    @Test
    fun `deflated data can be inflated again`() {
        val payload = "The quick brown fox jumps over the lazy dog. ".repeat(500).toByteArray()

        val compressed = transform(payload, DeflateTransformer(CompressionLevel.DEFAULT))

        assertTrue(compressed.size < payload.size)
        assertContentEquals(payload, transform(compressed, InflateTransformer()))
    }

    @Test
    fun `combining transformers closes both of them`() {
        val first = RecordingTransformer()
        val second = RecordingTransformer()

        CombinedTransformer(first, second).close()

        assertTrue(first.closed)
        assertTrue(second.closed)
    }

    @Test
    fun `closing reports a failing transformer as suppressed exception`() {
        val source = RecordingInputStream("payload".toByteArray(), failureOnClose = "source stream")
        val transformer = RecordingTransformer(failureOnClose = "transformer")

        val exception = assertThrows<IOException> {
            TransformingInputStream(source, transformer).close()
        }

        assertEquals("source stream", exception.message)
        assertContentEquals(listOf("transformer"), exception.suppressed.map { it.message })
        assertTrue(transformer.closed)
    }

    @Test
    fun `combining transformers keeps the failure of the first one as primary exception`() {
        val first = RecordingTransformer(failureOnClose = "first")
        val second = RecordingTransformer(failureOnClose = "second")

        val exception = assertThrows<IOException> {
            CombinedTransformer(first, second).close()
        }

        assertEquals("first", exception.message)
        assertContentEquals(listOf("second"), exception.suppressed.map { it.message })
    }

    private fun transform(input: ByteArray, transformer: ByteBlockTransformer): ByteArray {
        return TransformingInputStream(ByteArrayInputStream(input), transformer).use { it.readBytes() }
    }

    /**
     * Passes all data through unchanged while recording whether it has been closed. If a failure message is given,
     * closing reports it as an [IOException].
     */
    private class RecordingTransformer(private val failureOnClose: String? = null) : ByteBlockTransformer {

        var closed = false

        override fun apply(input: ByteBuf): Optional<ByteBuf> {
            // The stream releases the input buffer once this returns, therefore a copy has to be handed out.
            return Optional.of(Unpooled.copiedBuffer(input))
        }

        override fun complete(): Optional<ByteBuf> {
            return Optional.empty()
        }

        override fun close() {
            closed = true
            failureOnClose?.let { throw IOException(it) }
        }
    }

    /**
     * Provides the given data while recording whether it has been closed. If a failure message is given, closing
     * reports it as an [IOException].
     */
    private class RecordingInputStream(data: ByteArray, private val failureOnClose: String? = null) : InputStream() {

        var closed = false

        private val delegate = ByteArrayInputStream(data)

        override fun read(): Int {
            return delegate.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return delegate.read(b, off, len)
        }

        override fun close() {
            closed = true
            failureOnClose?.let { throw IOException(it) }
        }
    }
}
