package app.akilesh.qacc

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.akilesh.qacc.databinding.ActivityMainBinding
import app.akilesh.qacc.databinding.LayoutPresetColorsBinding
import app.akilesh.qacc.databinding.LayoutWallpaperColorsBinding
import app.akilesh.qacc.model.Accent
import app.akilesh.qacc.signing.ByteArrayStream
import app.akilesh.qacc.signing.JarMap
import app.akilesh.qacc.signing.SignAPK
import app.akilesh.qacc.utils.SwipeToDeleteCallback
import app.akilesh.qacc.viewmodel.AccentViewModel
import com.afollestad.assent.Permission
import com.afollestad.assent.rationale.createDialogRationale
import com.afollestad.assent.runWithPermissions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.topjohnwu.superuser.Shell
import me.priyesh.chroma.ChromaDialog
import me.priyesh.chroma.ColorMode
import me.priyesh.chroma.ColorSelectListener
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.io.*
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec


class MainActivity : AppCompatActivity() {

    private val assetFiles = mutableListOf(
        "AndroidManifest.xml",
        "src/values/colors.xml",
        "src/values/strings.xml"
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var accentViewModel: AccentViewModel
    private lateinit var path: String
    private var accentColor = ""
    private var accentName = ""


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        copyAssets()
        path = if (SDK_INT == Q) "/data/adb/modules/qacc-mobile/system/product/overlay"
            else "/data/adb/modules/qacc-mobile/system/vendor/overlay"

        val adapter = AccentListAdapter(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        accentViewModel = ViewModelProvider(this).get(AccentViewModel::class.java)
        accentViewModel.allAccents.observe(this, Observer { accents ->
            accents?.let { adapter.setAccents(it) }
        })

        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val accent = adapter.getAccentAndRemoveAt(viewHolder.adapterPosition)
                accentViewModel.delete(accent)
                val appName = accent.pkgName.substring(accent.pkgName.lastIndexOf(".") + 1)
                val result = Shell.su("rm -f $path/$appName.apk").exec()
                if (result.isSuccess)
                    showSnackbar("${accent.name} removed.")
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        if (SDK_INT == O)
            binding.wallFrame.visibility = View.GONE

        val decorView = window.decorView
        decorView.systemUiVisibility = FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS

        when(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> {
                val colorWhite = ContextCompat.getColor(this, R.color.white)
                window.navigationBarColor = colorWhite
                window.statusBarColor = colorWhite
                decorView.systemUiVisibility = SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                val colorDefaultDark = ContextCompat.getColor(this, R.color.colorDefaultDark)
                window.statusBarColor = colorDefaultDark
                window.navigationBarColor = colorDefaultDark
            }
        }

        binding.custom.setOnClickListener { setCustomColor() }
        binding.preset.setOnClickListener { chooseFromPresets() }
        binding.create.setOnClickListener { createAccent() }
        if (SDK_INT > O) binding.wallColors.setOnClickListener { chooseFromWallpaperColors() }

        binding.fab.setOnClickListener {
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
        }

        binding.name.doAfterTextChanged {
            accentName = it.toString().trim()
            val selected = "$accentName - $accentColor"
            binding.previewSelectedText.text = selected
        }

    }

    private fun copyAssets() {
        val arch = if ( listOf(Build.SUPPORTED_64_BIT_ABIS).isNotEmpty() )  "arm64" else "arm"
        if (arch == "arm64")
            assetFiles.addAll( listOf("aapt64", "xmlstarlet64", "zipalign64") )
        else
            assetFiles.addAll( listOf("aapt", "xmlstarlet", "zipalign") )

        Log.d("assets", assetFiles.toString())
        assetFiles.forEach {
            val file = it.removeSuffix("64")
            copyFromAsset(file)
        }
    }

    private fun copyFromAsset(filename: String) {
        if( !File("$filesDir/src/values").exists() )
            File("$filesDir/src/values").mkdirs()
        assets.open(filename).use { stream ->
            File("${filesDir}/$filename").outputStream().use {
                stream.copyTo(it)
            }
        }
    }

    private fun setCustomColor() {

        ChromaDialog.Builder()
            .initialColor(Color.parseColor("#FF2800"))
            .colorMode(ColorMode.RGB)
            .onColorSelected(object : ColorSelectListener {
                override fun onColorSelected(color: Int) {
                    accentColor = toHex(color)
                    binding.customText.text = accentColor
                    binding.previewCustom.setColorFilter(color)
                    binding.previewCustom.visibility = View.VISIBLE
                    binding.previewSelected.setColorFilter(color)
                    val selected = "$accentName - $accentColor"
                    binding.previewSelectedText.text = selected
                }
            })
            .create()
            .show(supportFragmentManager, "ChromaDialog")

    }

    private fun chooseFromPresets() {
        val layoutPresetColorsBinding = LayoutPresetColorsBinding.inflate(layoutInflater)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Presets")
            .setView(layoutPresetColorsBinding.root)
        val dialog = builder.create()
        dialog.show()

        val nestedScrollView = layoutPresetColorsBinding.root as NestedScrollView
        val linearLayoutCompat = nestedScrollView.getChildAt(0) as LinearLayoutCompat

        linearLayoutCompat.children.forEach { linearViews ->
            linearViews as LinearLayoutCompat
            linearViews.setOnClickListener {
                val appCompatImageView = linearViews.getChildAt(0) as AppCompatImageView
                val textView = linearViews.getChildAt(1) as MaterialTextView
                val color = appCompatImageView.imageTintList?.defaultColor
                if (color != null) {
                    accentColor = toHex(color)
                    binding.previewPreset.setColorFilter(color)
                    binding.previewPreset.visibility = View.VISIBLE
                    binding.previewSelected.setColorFilter(color)
                    accentName = textView.text as String
                    val selected = "$accentName - $accentColor"
                    binding.previewSelectedText.text = selected
                }
                binding.presetText.text = textView.text
                binding.name.setText(textView.text)
                dialog.cancel()
            }
        }
    }

    private fun chooseFromWallpaperColors() {
        if (SDK_INT > O) {

            val rationaleHandler = createDialogRationale(R.string.app_name_full) {
                onPermission(
                    Permission.READ_EXTERNAL_STORAGE,
                    "Storage permission is required to get wallpaper colours."
                )
            }

            runWithPermissions(
                Permission.READ_EXTERNAL_STORAGE,
                rationaleHandler = rationaleHandler
            ) {
                if (it.isAllGranted()) {
                    val wallpaperManager = WallpaperManager.getInstance(this)
                    val wallDrawable = wallpaperManager.drawable
                    var wallColors = wallpaperManager.getWallpaperColors(FLAG_SYSTEM)!!

                    val colorsChangedListener = WallpaperManager.OnColorsChangedListener { colors, _ ->
                        wallColors = colors ?: WallpaperColors.fromDrawable(wallDrawable)
                    }
                    wallpaperManager.addOnColorsChangedListener(colorsChangedListener, Handler())

                    val primary = wallColors.primaryColor.toArgb()
                    val secondary = wallColors.secondaryColor?.toArgb()
                    val tertiary = wallColors.tertiaryColor?.toArgb()

                    val layoutWallpaperColorsBinding =
                        LayoutWallpaperColorsBinding.inflate(layoutInflater)
                    val primaryImage =
                        layoutWallpaperColorsBinding.primary.getChildAt(0) as ImageView
                    val secondaryImage =
                        layoutWallpaperColorsBinding.secondary.getChildAt(0) as ImageView
                    val tertiaryImage =
                        layoutWallpaperColorsBinding.tertiary.getChildAt(0) as ImageView

                    primaryImage.setColorFilter(primary)
                    if (secondary != null) secondaryImage.setColorFilter(secondary)
                    else layoutWallpaperColorsBinding.secondary.visibility = View.GONE
                    if (tertiary != null) tertiaryImage.setColorFilter(tertiary)
                    else layoutWallpaperColorsBinding.tertiary.visibility = View.GONE

                    val builder = MaterialAlertDialogBuilder(this)
                        .setTitle("Wallpaper colours")
                        .setView(layoutWallpaperColorsBinding.root)
                    val dialog = builder.create()
                    dialog.show()

                    layoutWallpaperColorsBinding.primary.setOnClickListener {
                        binding.previewWall.setColorFilter(primary)
                        accentColor = toHex(primary)
                        binding.previewWall.visibility = View.VISIBLE
                        binding.wallText.text =
                            String.format(resources.getString(R.string.wallpaper_primary))
                        binding.previewSelected.setColorFilter(primary)
                        val selected = "$accentName - $accentColor"
                        binding.previewSelectedText.text = selected
                        dialog.cancel()
                    }

                    if (secondary != null) {
                        layoutWallpaperColorsBinding.secondary.setOnClickListener {
                            binding.previewWall.setColorFilter(secondary)
                            accentColor = toHex(secondary)
                            binding.previewWall.visibility = View.VISIBLE
                            binding.wallText.text =
                                String.format(resources.getString(R.string.wallpaper_secondary))
                            binding.previewSelected.setColorFilter(secondary)
                            val selected = "$accentName - $accentColor"
                            binding.previewSelectedText.text = selected
                            dialog.cancel()
                        }
                    }

                    if (tertiary != null) {
                        layoutWallpaperColorsBinding.tertiary.setOnClickListener {
                            binding.previewWall.setColorFilter(tertiary)
                            accentColor = toHex(tertiary)
                            binding.previewWall.visibility = View.VISIBLE
                            binding.wallText.text =
                                String.format(resources.getString(R.string.wallpaper_tertiary))
                            binding.previewSelected.setColorFilter(tertiary)
                            val selected = "$accentName - $accentColor"
                            binding.previewSelectedText.text = selected
                            dialog.cancel()
                        }
                    }
                }
            }
        }
    }

    private fun createAccent() {
        if (accentColor.isNotBlank() && accentName.isNotBlank()) {

            val prefix = "com.android.theme.color.custom."
            var suffix = accentName.filter { it.isLetter() }.filterNot { it.isWhitespace() }
            if (suffix.isBlank()) suffix = genSuffix()
            val pkgName = prefix + suffix
            Log.d("pkg-name", pkgName)

            val xmlRes = Shell.su(
                "cd ${filesDir.absolutePath}",
                "chmod +x xmlstarlet",
                "./xmlstarlet ed -L -u '/manifest/@package' -v \"$pkgName\" AndroidManifest.xml",
                "./xmlstarlet ed -L -u '/resources/color[@name=\"accent_device_default_light\"]' -v \"$accentColor\" src/values/colors.xml",
                "./xmlstarlet ed -L -u '/resources/color[@name=\"accent_device_default_dark\"]' -v \"$accentColor\" src/values/colors.xml",
                "./xmlstarlet ed -L -u '/resources/color[@name=\"accent_device_default_700\"]' -v \"$accentColor\" src/values/colors.xml",
                "./xmlstarlet ed -L -u '/resources/string[@name=\"accent_color_custom_overlay\"]' -v \"$accentName\" src/values/strings.xml",
                "cd /"
            ).exec()
            Log.d("ACC-xml", xmlRes.out.toString())

            if (xmlRes.isSuccess) {
                //Toast.makeText(this, "Building overlay apk", Toast.LENGTH_SHORT).show()
                Shell.su("cd ${filesDir.absolutePath}").exec()
                val ovrRes = Shell.su(resources.openRawResource(R.raw.create_overlay)).exec()
                Log.d("ACC-ovr", ovrRes.out.toString())

                if (ovrRes.isSuccess) {
                    val certFile = assets.open("testkey.x509.pem")
                    val keyFile = assets.open("testkey.pk8")
                    val out = FileOutputStream(File(filesDir, "signed.apk").absolutePath)

                    val cert = readCertificate(certFile)
                    val key = readPrivateKey(keyFile)

                    val jar = JarMap.open("$filesDir/qacc.apk")

                    SignAPK.sign(cert, key, jar, out.buffered())

                    Shell.su("cd ${filesDir.absolutePath}").exec()
                    val zipalignRes = Shell.su(resources.openRawResource(R.raw.zipalign)).exec()
                    Log.d("ACC-zip", zipalignRes.out.toString())

                    if (zipalignRes.isSuccess) {
                        //Toast.makeText(this, "Creating Magisk module", Toast.LENGTH_SHORT).show()

                        Shell.su("mkdir -p $path").exec()
                        Shell.su(resources.openRawResource(R.raw.create_module)).exec()
                        val result = Shell.su(
                            "cp -f $filesDir/aligned.apk $path/$suffix.apk",
                            "chmod 644 $path/$suffix.apk"
                        ).exec()
                        Log.d("ACC-MM", result.out.toString())

                        if (result.isSuccess) {

                            val createdApks = listOf("qacc.apk", "signed.apk", "aligned.apk")
                            createdApks.forEach {
                                File(filesDir, it).delete()
                            }
                            val accent = Accent(pkgName, accentName, accentColor)
                            accentViewModel.insert(accent)
                            showSnackbar("$accentName created.")

                        }
                    }
                }
            }
        }

        else {
            if (accentColor.isBlank()) Toast.makeText(this, "Accent color is not selected", Toast.LENGTH_SHORT).show()
            if (accentName.isBlank()) Toast.makeText(this, "Accent name is not set", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSnackbar(text: String) {

        Snackbar.make(
            binding.root,
            text,
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction("Reboot") {
                Shell.su("/system/bin/svc power reboot || /system/bin/reboot")
                    .submit()
            }
            .show()
    }

    private fun toHex(color: Int): String {
        return String.format("#%06X", (0xFFFFFF and color))
    }

    private fun genSuffix(): String {
        val alphabets = "abcdefghijklmnopqrstuvwxyz"
        val length = 7
        val builder = StringBuilder(length)
        val random = SecureRandom()
        var next: Char
        for (i in 0 until length) {
            next = alphabets[random.nextInt(alphabets.length)]
            builder.append(next)
        }
        return builder.toString()
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun readCertificate(inputStream: InputStream): X509Certificate {
        inputStream.use { stream ->
            val cf = CertificateFactory.getInstance("X.509")
            return cf.generateCertificate(stream) as X509Certificate
        }
    }


    @Throws(IOException::class, GeneralSecurityException::class)
    fun readPrivateKey(inputStream: InputStream): PrivateKey {
        inputStream.use { stream ->
            val buf = ByteArrayStream()
            buf.readFrom(stream)
            val bytes = buf.toByteArray()
            // Check to see if this is in an EncryptedPrivateKeyInfo structure.
            val spec = PKCS8EncodedKeySpec(bytes)
            /*
             * Now it's in a PKCS#8 PrivateKeyInfo structure. Read its Algorithm
             * OID and use that to construct a KeyFactory.
             */
            val bIn = ASN1InputStream(ByteArrayInputStream(spec.encoded))
            val pki = PrivateKeyInfo.getInstance(bIn.readObject())
            val algOid = pki.privateKeyAlgorithm.algorithm.id
            return KeyFactory.getInstance(algOid).generatePrivate(spec)
        }
    }
}



