package ye.abughazi.network

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var ip: EditText
    private lateinit var port: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 39, 70)
        window.navigationBarColor = android.graphics.Color.rgb(8, 39, 70)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(22)
            setBackgroundColor(android.graphics.Color.rgb(244, 247, 251))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = TextView(this).apply {
            text = "شبكة أبو غازي\nإدارة المايكروتك"
            textSize = 25f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(18)
            setBackgroundColor(android.graphics.Color.rgb(18, 58, 99))
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "لوحة التحكم الأولية"
            textSize = 21f
            setTextColor(android.graphics.Color.rgb(18, 58, 99))
            setPadding(0, 22, 0, 10)
        })
        val cards = listOf(
            "🎫  إنشاء كروت الإنترنت",
            "👥  إدارة المشتركين",
            "⚡  السرعات والباقات",
            "🌐  مراقبة اتصالات الإنترنت",
            "📡  مراقبة الشبكة",
            "🖨️  طباعة الكروت وملفات PDF",
            "📊  التقارير والإحصائيات"
        )
        cards.forEachIndexed { index, label ->
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    when (index) {
                        0 -> showCreateHotspotUserDialog()
                        1 -> loadHotspotUsers()
                        2 -> loadHotspotUsers()
                        3 -> loadRouterInfo()
                        4 -> loadHotspotActive()
                        5 -> loadHotspotUsers()
                        6 -> loadRouterInfo()
                    }
                }
            }
            content.addView(b, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4, 0, 4) })
        }
        content.addView(TextView(this).apply {
            text = "إعداد الاتصال بالراوتر"
            textSize = 20f
            setTextColor(android.graphics.Color.rgb(18, 58, 99))
            setPadding(0, 20, 0, 8)
        })
        ip = field("عنوان IP", "192.168.1.150")
        port = field("منفذ API", "8728")
        username = field("اسم المستخدم", "admin")
        password = field("كلمة المرور", "")
        password.inputType = 129
        listOf(ip, port, username, password).forEach { content.addView(it) }

        val connect = Button(this).apply {
            text = "اتصال بالمايكروتك"
            isAllCaps = false
            setOnClickListener { connectToRouter(this) }
        }
        content.addView(connect)

        val test = Button(this).apply {
            text = "اختبار الوصول إلى منفذ API فقط"
            isAllCaps = false
            setOnClickListener { testApiPort() }
        }
        content.addView(test)
        status = TextView(this).apply {
            text = "لم يتم الاتصال بالمايكروتك بعد"
            textSize = 16f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 12, 0, 18)
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "استخدم زر اتصال بالمايكروتك للتحقق من بيانات الدخول وقراءة اسم الراوتر. يجب تفعيل API والسماح لهذا الجهاز بالوصول. لا تفتح منفذ API للإنترنت العام."
            textSize = 13f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 8, 0, 20)
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun field(label: String, value: String): EditText {
        return EditText(this).apply {
            hint = label
            setText(value)
            textSize = 16f
            maxLines = 1
            setPadding(12)
            contentDescription = label
        }
    }


    private fun connectToRouter(button: Button) {
        val host = ip.text.toString().trim()
        val p = port.text.toString().trim().toIntOrNull()
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (host.isBlank() || p == null || p !in 1..65535 || user.isBlank()) {
            status.text = "تحقق من عنوان IP والمنفذ واسم المستخدم."
            return
        }
        button.isEnabled = false
        status.text = "جارٍ الاتصال والتحقق من بيانات الدخول..."
        thread {
            val result = try {
                val identity = RouterOsApi(host, p, user, pass).connectAndGetIdentity()
                "✅ تم تسجيل الدخول بنجاح إلى الراوتر: $identity ($host)"
            } catch (e: Exception) {
                val detail = when (e) {
                    is java.net.SocketTimeoutException -> "انتهت المهلة. تحقق من الشبكة وعنوان IP ومنفذ API."
                    is java.net.ConnectException -> "تعذر فتح الاتصال. تحقق من تفعيل API على المنفذ $p وقواعد الجدار الناري."
                    else -> e.message ?: "خطأ غير معروف (${e.javaClass.simpleName})"
                }
                "❌ فشل الاتصال: $detail"
            }
            runOnUiThread {
                status.text = result
                button.isEnabled = true
            }
        }
    }

    private fun credentialsOrNull(): RouterOsApi? {
        val host = ip.text.toString().trim()
        val p = port.text.toString().trim().toIntOrNull()
        val user = username.text.toString().trim()
        val pass = password.text.toString()
        if (host.isBlank() || p == null || p !in 1..65535 || user.isBlank()) {
            status.text = "تحقق من عنوان IP والمنفذ واسم المستخدم."
            return null
        }
        return RouterOsApi(host, p, user, pass)
    }

    private fun runApiAction(loading: String, action: (RouterOsApi) -> String) {
        val api = credentialsOrNull() ?: return
        status.text = loading
        thread {
            val result = try { action(api) } catch (e: Exception) {
                "❌ تعذر تنفيذ العملية: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread { status.text = result }
        }
    }

    private fun loadRouterInfo() {
        runApiAction("جارٍ قراءة معلومات الراوتر...") { api ->
            val identity = api.connectAndGetIdentity()
            val r = api.getSystemResource()
            "✅ الراوتر: $identity\nإصدار النظام: ${r["version"] ?: "غير معروف"}\nمدة التشغيل: ${r["uptime"] ?: "غير معروفة"}\nاستخدام المعالج: ${r["cpu-load"] ?: "؟"}%\nالذاكرة الحرة: ${r["free-memory"] ?: "؟"}"
        }
    }

    private fun loadHotspotActive() {
        runApiAction("جارٍ جلب المشتركين المتصلين...") { api ->
            val rows = api.getHotspotActive()
            if (rows.isEmpty()) "لا يوجد مشتركون متصلون حاليًا." else rows.take(100).mapIndexed { i, r ->
                "${i + 1}. ${r["user"] ?: "بدون اسم"} | IP: ${r["address"] ?: "-"} | MAC: ${r["mac-address"] ?: "-"} | مدة: ${r["uptime"] ?: "-"}"
            }.joinToString("\n") + if (rows.size > 100) "\n... تم عرض أول 100 مشترك" else ""
        }
    }

    private fun loadHotspotUsers() {
        runApiAction("جارٍ جلب كروت Hotspot...") { api ->
            val rows = api.getHotspotUsers()
            if (rows.isEmpty()) "لم يتم العثور على كروت Hotspot." else rows.take(100).mapIndexed { i, r ->
                "${i + 1}. ${r["name"] ?: "بدون اسم"} | الباقة: ${r["profile"] ?: "default"} | معطل: ${r["disabled"] ?: "false"} | المدة: ${r["limit-uptime"] ?: "-"}"
            }.joinToString("\n") + if (rows.size > 100) "\n... تم عرض أول 100 كرت" else ""
        }
    }

    private fun showCreateHotspotUserDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24) }
        val userField = EditText(this).apply { hint = "اسم الكرت / اسم المستخدم"; maxLines = 1 }
        val passField = EditText(this).apply { hint = "كلمة المرور (اتركها فارغة لمطابقة الاسم)"; maxLines = 1 }
        val profileField = EditText(this).apply { hint = "اسم الباقة / Profile (default)"; setText("default"); maxLines = 1 }
        box.addView(userField); box.addView(passField); box.addView(profileField)
        android.app.AlertDialog.Builder(this)
            .setTitle("إنشاء كرت Hotspot")
            .setView(box)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إنشاء على الراوتر", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val u = userField.text.toString().trim()
                        val p = passField.text.toString().ifBlank { u }
                        val profile = profileField.text.toString().trim().ifBlank { "default" }
                        if (u.isBlank()) { userField.error = "أدخل اسم الكرت"; return@setOnClickListener }
                        dialog.dismiss()
                        runApiAction("جارٍ إنشاء الكرت على المايكروتك...") { api ->
                            api.addHotspotUser(u, p, profile)
                            "✅ تم إنشاء الكرت $u على الراوتر ضمن الباقة $profile. افتح إدارة المشتركين للتحقق منه."
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun testApiPort() {
        val host = ip.text.toString().trim()
        val p = port.text.toString().trim().toIntOrNull()
        if (host.isBlank() || p == null || p !in 1..65535) {
            status.text = "تحقق من عنوان IP والمنفذ."
            return
        }
        status.text = "جارٍ اختبار الوصول..."
        thread {
            val result = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, p), 3500)
                }
                "يمكن الوصول إلى المنفذ $p على $host. يلزم اختبار تسجيل الدخول بشكل منفصل."
            } catch (e: Exception) {
                "تعذر الوصول إلى المنفذ. تحقق من الشبكة، وتفعيل API، وقواعد الجدار الناري. (${e.javaClass.simpleName})"
            }
            runOnUiThread { status.text = result }
        }
    }
}
