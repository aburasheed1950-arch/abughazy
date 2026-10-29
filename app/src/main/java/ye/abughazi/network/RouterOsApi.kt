package ye.abughazi.network

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** RouterOS API client for RouterOS 6.44.x and compatible versions. */
class RouterOsApi(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String
) {
    data class ApiResult(val rows: List<Map<String, String>>, val message: String = "")

    fun connectAndGetIdentity(): String {
        val rows = execute(listOf("/system/identity/print")).rows
        return rows.firstOrNull()?.get("name") ?: "MikroTik"
    }

    fun getSystemResource(): Map<String, String> = execute(listOf("/system/resource/print")).rows.firstOrNull() ?: emptyMap()
    fun getHotspotActive(): List<Map<String, String>> = execute(listOf("/ip/hotspot/active/print", "=.proplist=user,address,mac-address,uptime,server,bytes-in,bytes-out")).rows
    fun getHotspotUsers(): List<Map<String, String>> = execute(listOf("/ip/hotspot/user/print", "=.proplist=.id,name,profile,limit-uptime,disabled,comment")).rows

    fun addHotspotUser(name: String, pass: String, profile: String = "default", comment: String = "Created by Shabakat Abu Ghazi"): String {
        require(name.isNotBlank()) { "اسم المستخدم مطلوب" }
        val words = mutableListOf("/ip/hotspot/user/add", "=name=$name", "=password=$pass")
        if (profile.isNotBlank()) words.add("=profile=$profile")
        if (comment.isNotBlank()) words.add("=comment=$comment")
        execute(words)
        return "تم إرسال أمر إنشاء الكرت إلى الراوتر."
    }

    private fun execute(command: List<String>): ApiResult {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 7000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            writeSentence(output, listOf("/login", "=name=$user", "=password=$password"))
            val login = readReply(input)
            if (login.trap != null) throw IllegalStateException("رفض تسجيل الدخول: ${login.trap}")
            if (!login.done) throw IllegalStateException("لم تكتمل المصادقة مع الراوتر")

            writeSentence(output, command)
            val reply = readReply(input)
            if (reply.trap != null) throw IllegalStateException("رفض الراوتر الأمر: ${reply.trap}")
            return ApiResult(reply.rows)
        }
    }

    private data class Reply(val done: Boolean, val trap: String?, val rows: List<Map<String, String>>)

    private fun readReply(input: BufferedInputStream): Reply {
        val rows = mutableListOf<Map<String, String>>()
        var trap: String? = null
        var sentenceCount = 0
        while (sentenceCount++ < 1000) {
            val words = readSentence(input)
            if (words.isEmpty()) continue
            when (words[0]) {
                "!re" -> {
                    val attrs = linkedMapOf<String, String>()
                    words.drop(1).forEach { word ->
                        if (word.startsWith("=") && word.indexOf('=', 1) > 1) {
                            val split = word.indexOf('=', 1)
                            attrs[word.substring(1, split)] = word.substring(split + 1)
                        }
                    }
                    rows.add(attrs)
                }
                "!trap" -> trap = words.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "رفض الراوتر الطلب"
                "!done" -> return Reply(true, trap, rows)
            }
            if (trap != null) {
                // Continue reading until !done so the socket protocol remains aligned.
            }
        }
        throw IllegalStateException("رد طويل أو غير مكتمل من الراوتر")
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
            length < 0x4000 -> { output.write((length shr 8) or 0x80); output.write(length and 0xFF) }
            length < 0x200000 -> { output.write((length shr 16) or 0xC0); output.write((length shr 8) and 0xFF); output.write(length and 0xFF) }
            length < 0x10000000 -> { output.write((length shr 24) or 0xE0); output.write((length shr 16) and 0xFF); output.write((length shr 8) and 0xFF); output.write(length and 0xFF) }
            else -> { output.write(0xF0); output.write((length shr 24) and 0xFF); output.write((length shr 16) and 0xFF); output.write((length shr 8) and 0xFF); output.write(length and 0xFF) }
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
