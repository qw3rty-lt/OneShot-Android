package app.oneshot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {
    private val purple = Color.rgb(166, 112, 255)
    private val bg = Color.rgb(13, 11, 22)
    private val panel = Color.rgb(29, 24, 43)
    private val white = Color.rgb(245, 241, 255)
    private val muted = Color.rgb(176, 165, 196)

    private lateinit var status: TextView
    private lateinit var password: EditText
    private var selectedUri: Uri? = null
    private var selectedName: String = ""
    private var decryptedTemp: File? = null
    private var selectedMode = ""

    companion object {
        private const val PICK_SOURCE = 100
        private const val SAVE_CONTAINER = 101
        private const val PICK_CONTAINER = 102
        private const val MAGIC = "ONESHOT1"
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val ITERATIONS = 310_000
        private const val KEY_BITS = 256
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Best-effort screenshot/recents protection for sensitive screens.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        showHome()
    }

    private fun baseLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(22))
            setBackgroundColor(bg)
        }
    }

    private fun showHome() {
        val layout = baseLayout()
        val title = TextView(this).apply {
            text = "OneShot"
            textSize = 32f
            setTextColor(purple)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        layout.addView(title)
        layout.addView(label("一次性加密文件工具 · 本地处理"))
        layout.addView(space(28))
        layout.addView(section("创建加密文件"))
        layout.addView(label("选择任意文件，使用密码加密为 .oneshot 文件。"))
        layout.addView(space(10))
        layout.addView(actionButton("选择文件并加密") { chooseSource() })
        layout.addView(space(24))
        layout.addView(section("打开加密文件"))
        layout.addView(label("选择 .oneshot 文件，验证密码并在应用私有目录中临时解密。"))
        layout.addView(space(10))
        layout.addView(actionButton("打开 .oneshot 文件") { chooseContainer() })
        layout.addView(space(24))
        status = label("第一阶段原型：加密、密码验证与临时解密。")
        layout.addView(status)
        layout.addView(space(12))
        layout.addView(label("提示：本版本尚未实现格式预览器。解密内容仅写入应用私有缓存，不会调用外部应用。"))
        setContentView(layout)
    }

    private fun showPassword(mode: String) {
        selectedMode = mode
        val layout = baseLayout()
        layout.addView(section(if (mode == "encrypt") "设置加密密码" else "输入解密密码"))
        layout.addView(label(if (mode == "encrypt") "请设置至少 8 位密码。密码不会上传。" else "输入创建加密包时设置的密码。"))
        password = EditText(this).apply {
            setTextColor(white)
            setHintTextColor(muted)
            hint = "至少 8 位"
            inputType = 129
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(panel)
        }
        layout.addView(password, LinearLayout.LayoutParams(-1, dp(54)))
        layout.addView(space(16))
        layout.addView(actionButton(if (mode == "encrypt") "继续选择保存位置" else "验证并解密") {
            val pass = password.text.toString()
            if (pass.length < 8) {
                toast("密码至少需要 8 位")
                return@actionButton
            }
            if (mode == "encrypt") {
                val source = selectedUri ?: return@actionButton
                val name = selectedName.ifBlank { "secret.bin" }
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "$name.oneshot")
                }
                pendingPassword = pass
                startActivityForResult(intent, SAVE_CONTAINER)
            } else {
                decryptSelected(pass)
            }
        })
        layout.addView(space(8))
        layout.addView(actionButton("返回", false) { showHome() })
        setContentView(layout)
    }

    private var pendingPassword: String = ""

    private fun chooseSource() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, PICK_SOURCE)
    }

    private fun chooseContainer() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, PICK_CONTAINER)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            PICK_SOURCE -> {
                selectedUri = uri
                selectedName = queryName(uri)
                showPassword("encrypt")
            }
            SAVE_CONTAINER -> {
                try {
                    contentResolver.openInputStream(selectedUri!!).use { input ->
                        contentResolver.openOutputStream(uri).use { output ->
                            if (input == null || output == null) error("无法打开文件")
                            encryptStream(input, output, pendingPassword)
                        }
                    }
                    pendingPassword = ""
                    toast("加密完成，已保存 .oneshot 文件")
                    showHome()
                } catch (e: Exception) {
                    pendingPassword = ""
                    toast("加密失败：" + (e.message ?: "未知错误"))
                    showHome()
                }
            }
            PICK_CONTAINER -> {
                selectedUri = uri
                showPassword("decrypt")
            }
        }
    }

    private fun encryptStream(input: InputStream, output: OutputStream, pass: String) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val key = derive(pass, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        DataOutputStream(BufferedOutputStream(output)).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.writeByte(SALT_SIZE)
            out.writeByte(IV_SIZE)
            out.writeInt(ITERATIONS)
            out.write(salt)
            out.write(iv)
            CipherOutputStream(out, cipher).use { encrypted ->
                input.copyTo(encrypted, 64 * 1024)
            }
        }
    }

    private fun decryptSelected(pass: String) {
        val uri = selectedUri ?: return
        val temp = File(cacheDir, "read_${System.currentTimeMillis()}.tmp")
        try {
            contentResolver.openInputStream(uri).use { raw ->
                if (raw == null) error("无法读取文件")
                DataInputStream(BufferedInputStream(raw)).use { input ->
                    val magic = ByteArray(MAGIC.length)
                    input.readFully(magic)
                    if (String(magic, Charsets.US_ASCII) != MAGIC) error("不是有效的 OneShot 文件")
                    val saltLen = input.readUnsignedByte()
                    val ivLen = input.readUnsignedByte()
                    val iterations = input.readInt()
                    if (saltLen != SALT_SIZE || ivLen != IV_SIZE || iterations !in 100_000..1_000_000) {
                        error("文件头无效")
                    }
                    val salt = ByteArray(saltLen).also { input.readFully(it) }
                    val iv = ByteArray(ivLen).also { input.readFully(it) }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, derive(pass, salt), GCMParameterSpec(128, iv))
                    FileOutputStream(temp).use { fileOut ->
                        CipherInputStream(input, cipher).use { encrypted ->
                            encrypted.copyTo(fileOut, 64 * 1024)
                        }
                    }
                }
            }
            decryptedTemp?.delete()
            decryptedTemp = temp
            showDecryptedContent(temp)
        } catch (e: Exception) {
            temp.delete()
            toast("验证失败或文件损坏，请检查密码")
            showHome()
        }
    }

    private fun showDecryptedContent(file: File) {
        val layout = baseLayout()
        layout.addView(section("解密成功"))
        layout.addView(label("文件大小：" + android.text.format.Formatter.formatFileSize(this, file.length())))
        layout.addView(label("内容仅在 OneShot 内显示；离开此页面会清理临时明文。"))

        val preview = TextView(this).apply {
            textSize = 14f
            setTextColor(white)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(panel)
            setTextIsSelectable(false)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val maxPreviewBytes = 2L * 1024L * 1024L
        val textContent = if (file.length() > maxPreviewBytes) {
            null
        } else {
            try {
                val bytes = file.readBytes()
                if (bytes.any { it == 0.toByte() }) null else {
                    val decoder = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    var decoded = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    if (decoded.startsWith("\uFEFF")) decoded = decoded.substring(1)
                    decoded
                }
            } catch (_: Exception) {
                null
            }
        }

        if (textContent != null) {
            layout.addView(label("文本预览（UTF-8）"))
            preview.text = if (textContent.isBlank()) "（这是一个空文本文件）" else textContent
            val scroll = ScrollView(this).apply {
                isFillViewport = true
                addView(preview, android.view.ViewGroup.LayoutParams(-1, -2))
            }
            layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            layout.addView(space(12))
            layout.addView(label(
                if (file.length() > maxPreviewBytes)
                    "文件超过 2 MB，暂不直接预览。临时明文仍保存在应用私有缓存中。"
                else
                    "暂不支持此文件格式或字符编码的预览。当前版本支持 UTF-8 文本文件（最大 2 MB）。"
            ))
            layout.addView(space(12))
        }

        layout.addView(actionButton("关闭并清理临时文件") {
            wipeTemp()
            showHome()
            toast("已清理本机临时解密文件")
        })
        setContentView(layout)
    }

    private fun derive(pass: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun queryName(uri: Uri): String {
        var name = "secret.bin"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
        }
        return name
    }

    private fun wipeTemp() {
        decryptedTemp?.let { f ->
            if (f.exists()) {
                try {
                    FileOutputStream(f, false).use { it.write(ByteArray(0)) }
                } catch (_: Exception) {}
                f.delete()
            }
        }
        decryptedTemp = null
    }

    override fun onDestroy() {
        wipeTemp()
        pendingPassword = ""
        super.onDestroy()
    }

    private fun section(s: String) = TextView(this).apply {
        text = s
        textSize = 21f
        setTextColor(white)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun label(s: String) = TextView(this).apply {
        text = s
        textSize = 14f
        setTextColor(muted)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun actionButton(s: String, primary: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            text = s
            textSize = 15f
            setTextColor(if (primary) Color.WHITE else purple)
            setBackgroundColor(if (primary) Color.rgb(105, 65, 170) else panel)
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun space(h: Int) = TextView(this).apply { height = dp(h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
