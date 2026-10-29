package ye.abughazi.network

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Minimal RouterOS API client for RouterOS 6.43+ (plain API login). */
class RouterOsApi(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String
) {
    fun connectAndGetIdentity(): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 7000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            writeSentence(output, listOf("/login", "=name=$user", "=password=$password"))
            val loginReply = readReply(input)
            if (loginReply.trap != null) throw IllegalStateException("رفض تسجيل الدخول: ${loginReply.trap}")
            if (!loginReply.done) throw IllegalStateException("لم تكتمل المصادقة مع الراوتر")

            writeSentence(output, listOf("/system/identity/print"))
            val identityReply = readReply(input)
            if (identityReply.trap != null) throw IllegalStateException("تم الاتصال لكن تعذر قراءة هوية الراوتر: ${identityReply.trap}")
            return identityReply.attributes["name"] ?: "MikroTik"
        }
    }

    private data class Reply(val done: Boolean, val trap: String?, val attributes: Map<String, String>)

    private fun readReply(input: BufferedInputStream): Reply {
        val attrs = linkedMapOf<String, String>()
        var done = false
        var trap: String? = null
        var sentenceCount = 0
        while (!done && sentenceCount++ < 100) {
            val words = readSentence(input)
            if (words.isEmpty()) continue
            when (words[0]) {
                "!re" -> words.drop(1).forEach { word ->
                    if (word.startsWith("=") && word.indexOf('=', 1) > 1) {
                        val split = word.indexOf('=', 1)
                        attrs[word.substring(1, split)] = word.substring(split + 1)
                    }
                }
                "!trap" -> {
                    trap = words.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                        ?: "رفض الراوتر الطلب"
                }
                "!done" -> done = true
            }
            if (trap != null) break
        }
        if (!done && trap == null) throw IllegalStateException("انتهت مهلة انتظار رد الراوتر")
        return Reply(done, trap, attrs)
    }

    private fun writeSentence(output: BufferedOutputStream, words: List<String>) {
        words.forEach { word ->
            val bytes = word.toByteArray(Charsets.UTF_8)
            writeLength(output, bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.flush()
    }

    private fun readSentence(input: BufferedInputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val length = readLength(input)
            if (length == 0) return words
            if (length < 0 || length > 1024 * 1024) throw IllegalStateException("رد غير صالح من الراوتر")
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(bytes, offset, length - offset)
                if (count < 0) throw IllegalStateException("انقطع الاتصال بالراوتر")
                offset += count
            }
            words.add(String(bytes, Charsets.UTF_8))
        }
    }

    private fun writeLength(output: BufferedOutputStream, length: Int) {
        when {
            length < 0x80 -> output.write(length)
            length < 0x4000 -> {
                output.write((length shr 8) or 0x80)
                output.write(length and 0xFF)
            }
            length < 0x200000 -> {
                output.write((length shr 16) or 0xC0)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
            length < 0x10000000 -> {
                output.write((length shr 24) or 0xE0)
                output.write((length shr 16) and 0xFF)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
            else -> {
                output.write(0xF0)
                output.write((length shr 24) and 0xFF)
                output.write((length shr 16) and 0xFF)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
        }
    }

    private fun readLength(input: BufferedInputStream): Int {
        val c = input.read()
        if (c < 0) throw IllegalStateException("أغلق الراوتر الاتصال")
        return when {
            c and 0x80 == 0 -> c
            c and 0xC0 == 0x80 -> ((c and 0x3F) shl 8) or readByte(input)
            c and 0xE0 == 0xC0 -> ((c and 0x1F) shl 16) or (readByte(input) shl 8) or readByte(input)
            c and 0xF0 == 0xE0 -> ((c and 0x0F) shl 24) or (readByte(input) shl 16) or (readByte(input) shl 8) or readByte(input)
            c == 0xF0 -> (readByte(input) shl 24) or (readByte(input) shl 16) or (readByte(input) shl 8) or readByte(input)
            else -> throw IllegalStateException("ترميز طول غير معروف من الراوتر")
        }
    }

    private fun readByte(input: BufferedInputStream): Int {
        val value = input.read()
        if (value < 0) throw IllegalStateException("انقطع رد الراوتر")
        return value
    }
}
