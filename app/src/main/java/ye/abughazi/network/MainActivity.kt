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
        cards.forEach { label ->
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "هذا القسم واجهة أولية وسيتم ربط وظائفه بالراوتر في مراحل التطوير التالية.", Toast.LENGTH_LONG).show()
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

        val test = Button(this).apply {
            text = "اختبار الوصول إلى منفذ API"
            isAllCaps = false
            setOnClickListener { testApiPort() }
        }
        content.addView(test)
        status = TextView(this).apply {
            text = "لم يتم اختبار الاتصال بعد"
            textSize = 16f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 12, 0, 18)
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "ملاحظة: اختبار المنفذ يتحقق من إمكانية الوصول الشبكي فقط، ولا يثبت نجاح تسجيل الدخول أو تنفيذ أوامر المايكروتك."
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
