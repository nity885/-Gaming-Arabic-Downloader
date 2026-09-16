package com.gad.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ---------- Static data ----------

data class GadCategory(val id: String, val label: String, val queries: List<String>)

val CATEGORIES = listOf(
    GadCategory("gaming", "ألعاب", listOf("العاب فيديو", "تختيم لعبة", "مراجعة لعبة")),
    GadCategory("pc", "كمبيوتر / عتاد", listOf("تجميعة كمبيوتر", "مراجعة معالج", "قطع كمبيوتر")),
    GadCategory("consoles", "أجهزة ألعاب", listOf("بلايستيشن", "اكس بوكس", "نينتندو سويتش")),
    GadCategory("phones", "هواتف", listOf("مراجعة هاتف", "افضل هاتف", "مقارنة هواتف")),
    GadCategory("tech", "تكنولوجيا", listOf("تكنولوجيا جديدة", "اخبار تقنية", "شرح تقني")),
    GadCategory("diy", "اصنع بنفسك", listOf("اصنع بنفسك الكترونيات", "مشروع الكتروني"))
)

val MIN_VIEWS_OPTIONS = listOf(100_000 to "+100K", 500_000 to "+500K", 1_000_000 to "+1M")
val QUALITY_OPTIONS = listOf(360, 480, 720)
val COUNT_OPTIONS = listOf(5, 10, 20)

const val TERMUX_PACKAGE = "com.termux"
const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
const val OUTPUT_DIR = "/storage/emulated/0/Download/Gaming_Arabic"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GadScreen()
                }
            }
        }
    }
}

@Composable
fun GadScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedMinViews by remember { mutableStateOf(MIN_VIEWS_OPTIONS[0].first) }
    var selectedQuality by remember { mutableStateOf(QUALITY_OPTIONS[2]) }
    var selectedCount by remember { mutableStateOf(COUNT_OPTIONS[1]) }
    var customCountText by remember { mutableStateOf("") }
    var useCustomCount by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    // Ask for "All files access" so the app can poll the status file Termux writes.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // Ask for permission to send commands to Termux.
    val runCommandPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureRunCommandPermission() {
        val granted = context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCommandPermissionLauncher.launch("com.termux.permission.RUN_COMMAND")
        }
    }

    fun ensureAllFilesAccess() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            allFilesLauncher.launch(intent)
        }
    }

    fun buildQueries(): List<String> =
        CATEGORIES.filter { it.id in selectedCategories }.flatMap { it.queries }

    fun buildScript(queries: List<String>, minViews: Int, quality: Int, count: Int): String {
        val queriesArray = queries.joinToString("\n") { "\"${it.replace("\"", "")}\"" }
        return """
            #!/data/data/com.termux/files/usr/bin/bash
            set -e
            OUTDIR="$OUTPUT_DIR"
            ARCHIVE="${'$'}OUTDIR/downloaded.txt"
            STATUS="${'$'}OUTDIR/status.txt"
            mkdir -p "${'$'}OUTDIR"
            touch "${'$'}ARCHIVE"
            echo "SEARCHING" > "${'$'}STATUS"

            QUERIES=(
            $queriesArray
            )

            MIN_VIEWS=$minViews
            QUALITY=$quality
            COUNT=$count

            TMP_ALL="${'$'}OUTDIR/.tmp_all.txt"
            > "${'$'}TMP_ALL"

            for Q in "${'$'}{QUERIES[@]}"; do
              yt-dlp "ytsearch50:${'$'}Q" --skip-download --no-warnings \
                --print "%(view_count)s	%(id)s	%(title)s" >> "${'$'}TMP_ALL" 2>/dev/null || true
            done

            awk -F'\t' -v m="${'$'}MIN_VIEWS" 'NF>=3 && ${'$'}1 != "NA" && ${'$'}1+0 >= m {print}' "${'$'}TMP_ALL" > "${'$'}OUTDIR/.tmp_filtered.txt"

            awk -F'\t' '!seen[${'$'}2]++' "${'$'}OUTDIR/.tmp_filtered.txt" > "${'$'}OUTDIR/.tmp_dedup.txt"

            DONE_IDS=${'$'}(awk '{print ${'$'}2}' "${'$'}ARCHIVE" 2>/dev/null)
            > "${'$'}OUTDIR/.tmp_new.txt"
            while IFS=${'$'}'\t' read -r VIEWS ID TITLE; do
              if ! grep -qxF "${'$'}ID" <<< "${'$'}DONE_IDS"; then
                printf "%s\t%s\t%s\n" "${'$'}VIEWS" "${'$'}ID" "${'$'}TITLE" >> "${'$'}OUTDIR/.tmp_new.txt"
              fi
            done < "${'$'}OUTDIR/.tmp_dedup.txt"

            shuf "${'$'}OUTDIR/.tmp_new.txt" -o "${'$'}OUTDIR/.tmp_shuffled.txt"

            head -n "${'$'}COUNT" "${'$'}OUTDIR/.tmp_shuffled.txt" > "${'$'}OUTDIR/.tmp_selected.txt"

            SELECTED_COUNT=${'$'}(wc -l < "${'$'}OUTDIR/.tmp_selected.txt" | tr -d ' ')
            echo "SELECTED:${'$'}SELECTED_COUNT/${'$'}COUNT" > "${'$'}STATUS"

            i=0
            while IFS=${'$'}'\t' read -r VIEWS ID TITLE; do
              i=${'$'}((i+1))
              echo "DOWNLOADING:${'$'}i/${'$'}SELECTED_COUNT" > "${'$'}STATUS"
              yt-dlp "https://www.youtube.com/watch?v=${'$'}ID" \
                --download-archive "${'$'}ARCHIVE" \
                -f "bv*[height<=${'$'}QUALITY]+ba/b[height<=${'$'}QUALITY]" \
                --merge-output-format mp4 \
                -o "${'$'}OUTDIR/%(title)s [%(id)s].%(ext)s" \
                --no-warnings
            done < "${'$'}OUTDIR/.tmp_selected.txt"

            echo "DONE:${'$'}SELECTED_COUNT/${'$'}COUNT" > "${'$'}STATUS"
            rm -f "${'$'}OUTDIR"/.tmp_*
        """.trimIndent()
    }

    fun runInTermux(script: String) {
        val intent = Intent()
        intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
        intent.action = "com.termux.RUN_COMMAND"
        intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            statusText = "تعذر تشغيل Termux: ${e.message}"
        }
    }

    fun startPolling() {
        scope.launch {
            isRunning = true
            statusText = "جارٍ البحث..."
            val statusFile = File("$OUTPUT_DIR/status.txt")
            var elapsed = 0
            val timeoutSeconds = 60 * 30 // 30 min safety cap
            while (isRunning && elapsed < timeoutSeconds) {
                delay(1000)
                elapsed++
                if (statusFile.exists()) {
                    val line = runCatching { statusFile.readText().trim() }.getOrDefault("")
                    when {
                        line.startsWith("SEARCHING") -> statusText = "جارٍ البحث..."
                        line.startsWith("SELECTED:") -> {
                            val xy = line.removePrefix("SELECTED:")
                            statusText = "تم الاختيار: $xy"
                        }
                        line.startsWith("DOWNLOADING:") -> {
                            val xy = line.removePrefix("DOWNLOADING:")
                            statusText = "التحميل: $xy"
                        }
                        line.startsWith("DONE:") -> {
                            val xy = line.removePrefix("DONE:")
                            statusText = "اكتمل التحميل: $xy"
                            isRunning = false
                        }
                    }
                }
            }
            if (isRunning) {
                statusText = "انتهت المهلة، تحقق من Termux"
                isRunning = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Gaming Arabic Downloader", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        Spacer(Modifier.height(16.dp))
        Text("التصنيفات", fontWeight = FontWeight.Bold)
        CATEGORIES.forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = cat.id in selectedCategories,
                    onCheckedChange = { checked ->
                        selectedCategories = if (checked) selectedCategories + cat.id
                        else selectedCategories - cat.id
                    }
                )
                Text(cat.label)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("الحد الأدنى للمشاهدات", fontWeight = FontWeight.Bold)
        MIN_VIEWS_OPTIONS.forEach { (value, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedMinViews == value,
                    onClick = { selectedMinViews = value }
                )
                Text(label)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("عدد الفيديوهات", fontWeight = FontWeight.Bold)
        COUNT_OPTIONS.forEach { value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = !useCustomCount && selectedCount == value,
                    onClick = {
                        useCustomCount = false
                        selectedCount = value
                    }
                )
                Text(value.toString())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = useCustomCount,
                onClick = { useCustomCount = true }
            )
            Text("مخصص:")
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = customCountText,
                onValueChange = {
                    customCountText = it.filter { c -> c.isDigit() }
                    useCustomCount = true
                },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("الجودة", fontWeight = FontWeight.Bold)
        QUALITY_OPTIONS.forEach { q ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedQuality == q,
                    onClick = { selectedQuality = q }
                )
                Text("${q}p")
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                ensureAllFilesAccess()
                ensureRunCommandPermission()
                val queries = buildQueries()
                if (queries.isEmpty()) {
                    statusText = "اختر تصنيفًا واحدًا على الأقل"
                    return@Button
                }
                val count = if (useCustomCount) (customCountText.toIntOrNull() ?: 0) else selectedCount
                if (count <= 0) {
                    statusText = "أدخل عددًا صحيحًا للفيديوهات"
                    return@Button
                }
                val script = buildScript(queries, selectedMinViews, selectedQuality, count)
                runInTermux(script)
                startPolling()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            Text(if (isRunning) "جارٍ التنفيذ..." else "بحث وتحميل")
        }

        Spacer(Modifier.height(16.dp))
        if (statusText.isNotEmpty()) {
            Text(statusText, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "ملاحظة: يجب تفعيل allow-external-apps=true في ملف ~/.termux/termux.properties " +
                "وإعادة تشغيل Termux مرة واحدة قبل الاستخدام.",
            fontSize = 12.sp
        )
    }
}
