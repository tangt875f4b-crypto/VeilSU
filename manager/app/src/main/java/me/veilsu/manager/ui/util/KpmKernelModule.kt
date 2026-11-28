package me.veilsu.manager.ui.util

import android.net.Uri
import me.veilsu.manager.ksuApp
import com.topjohnwu.superuser.CallbackList
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val KPM_DIR = "/data/adb/kpm"
private const val LEGACY_KPM_BOOT_SCRIPT = "/data/adb/post-fs-data.d/kpm-host.sh"
private val KMI_PATTERN = Regex("""android\d+-\d+(?:\.\d+)*""")

/**
 * Install/load the KPM host as a standalone kernel module (`ksud insmod`).
 *
 * `persist = true` writes the `.ko` to /data/adb/kpm; on the next boot ksud's
 * own post-fs-data path loads `kpm-<kmi>.ko` before the userspace `.kpm`
 * modules.  `persist = false` is a one-boot test load: the temporary file is
 * removed after the module has been loaded.
 *
 * This path never touches the boot image.
 */
fun installKpmKernelModule(
    customModule: Uri?,
    persist: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    if (!rootAvailable()) {
        onStderr("Root is required to load a kernel module")
        return FlashResult(1, "Root is required to load a kernel module", false)
    }

    fun read(cmd: String): String = withNewRootShell {
        val stdout = ArrayList<String>()
        val stderr = ArrayList<String>()
        val result = newJob().add(cmd).to(stdout, stderr).exec()
        if (!result.isSuccess) {
            error(stderr.joinToString("\n").ifBlank {
                stdout.joinToString("\n").ifBlank { "exit ${result.code}" }
            })
        }
        stdout.joinToString("\n").trim()
    }

    fun run(cmd: String): ShellResult {
        val stdoutLines = ArrayList<String>()
        val out = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                s?.let {
                    stdoutLines.add(it)
                    onStdout(it)
                }
            }
        }
        val err = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let(onStderr) }
        }
        return withNewRootShell {
            val result = newJob().add(cmd).to(out, err).exec()
            ShellResult(
                code = result.code,
                err = result.err.joinToString("\n"),
                out = stdoutLines.joinToString("\n"),
            )
        }
    }

    fun isLoaded(): Boolean =
        read("test -e /sys/module/kpm && echo loaded || true") == "loaded"

    var cached: File? = null
    var remotePath: String? = null
    var keep = false
    try {
        val appKsud = getKsuDaemonPath()
        val installedKsud = "/data/adb/ksud"
        val ksud = if (read("test -x ${installedKsud.shellQuote()} && echo yes || true") == "yes") {
            installedKsud
        } else {
            onStdout("- /data/adb/ksud is missing; using the manager's bundled libksud")
            appKsud
        }
        val kmi = read("$ksud boot-info current-kmi")
        if (!KMI_PATTERN.matches(kmi)) {
            error("Unexpected current KMI: ${kmi.ifBlank { "<empty>" }}")
        }

        val loaded = isLoaded()
        if (!persist && loaded) {
            onStdout("- kpm is already loaded; test load skipped")
            keep = true
            return FlashResult(0, "", false)
        }

        val fileName = if (persist) "kpm-$kmi.ko" else "kpm-test-$kmi.ko"
        remotePath = "$KPM_DIR/$fileName"
        cached = File(ksuApp.cacheDir, "kpm-host-${System.currentTimeMillis()}.ko")
        if (customModule != null) {
            onStdout("- Reading selected kpm.ko")
            ksuApp.contentResolver.openInputStream(customModule)?.use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open the selected .ko")
        } else {
            val asset = "kpm/modules/kpm-$kmi.ko"
            onStdout("- Using bundled module: $asset")
            try {
                ksuApp.assets.open(asset).use { input ->
                    cached.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                error("No bundled kpm.ko for KMI $kmi; select a matching .ko file")
            }
        }

        validateKpmElf(cached)
        onStdout("- Staging kpm.ko as $remotePath")
        run(
            "mkdir -p ${KPM_DIR.shellQuote()} && " +
                "cat ${cached.absolutePath.shellQuote()} > ${remotePath.shellQuote()} && " +
                "chmod 600 ${remotePath.shellQuote()}"
        ).let { result ->
            if (result.code != 0) {
                error("Cannot stage kpm.ko: ${result.errorText()}")
            }
        }

        if (persist) {
            onStdout("- Staged for ksud native auto-load on the next boot")
        }

        if (loaded) {
            onStdout("- kpm is already loaded; skipped ksud insmod")
        } else {
            onStdout("- Loading kpm.ko with ksud insmod")
            val loadedNow = run("$ksud insmod ${remotePath.shellQuote()}")
            if (loadedNow.code != 0) {
                error("ksud insmod failed: ${loadedNow.errorText()}")
            }
            if (!isLoaded()) {
                error("ksud insmod returned success but kpm is not present in /sys/module/kpm")
            }
        }

        if (persist) {
            // Migrate devices that still carry the old post-fs-data.d hook only
            // after the new .ko is staged and, when needed, loaded successfully.
            run("rm -f ${LEGACY_KPM_BOOT_SCRIPT.shellQuote()} 2>/dev/null || true")
            onStdout("- ksud will auto-load $remotePath on the next boot")
        }

        if (!persist) {
            run("rm -f ${remotePath.shellQuote()} 2>/dev/null || true")
            remotePath = null
        }
        onStdout(
            if (persist) "- KPM kernel module installed and loaded"
            else "- KPM kernel module test-loaded; it will disappear after reboot"
        )
        keep = true
        return FlashResult(0, "", false)
    } catch (e: Exception) {
        val message = e.message ?: "Failed to load the KPM kernel module"
        onStderr(message)
        return FlashResult(1, message, false)
    } finally {
        cached?.delete()
        if (!keep) {
            remotePath?.let {
                runCatching { run("rm -f ${it.shellQuote()} 2>/dev/null || true") }
            }
        }
    }
}

/**
 * Force-remove persisted KPM host/module files, then try to unload the live host.
 * File deletion always runs first and does not depend on whether a KPM module is
 * loaded; `.kpm` files are deleted even when a live unload fails, so they cannot
 * come back on the next boot. The current boot may keep them until reboot.
 */
fun removeKpmKernelModule(
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    if (!rootAvailable()) {
        onStderr("Root is required to remove the KPM kernel module")
        return FlashResult(1, "Root is required to remove the KPM kernel module", false)
    }

    fun read(cmd: String): String = withNewRootShell {
        val stdout = ArrayList<String>()
        val stderr = ArrayList<String>()
        val result = newJob().add(cmd).to(stdout, stderr).exec()
        if (!result.isSuccess) {
            error(stderr.joinToString("\n").ifBlank {
                stdout.joinToString("\n").ifBlank { "exit ${result.code}" }
            })
        }
        stdout.joinToString("\n").trim()
    }

    fun run(cmd: String): ShellResult {
        val stdoutLines = ArrayList<String>()
        val out = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                s?.let {
                    stdoutLines.add(it)
                    onStdout(it)
                }
            }
        }
        val err = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let(onStderr) }
        }
        return withNewRootShell {
            val result = newJob().add(cmd).to(out, err).exec()
            ShellResult(result.code, result.err.joinToString("\n"), stdoutLines.joinToString("\n"))
        }
    }

    return try {
        // Remove the boot copies first. This must happen even when no KPM
        // module is loaded, and even if ksud cannot be resolved below.
        onStdout("- Removing the persisted KPM files")
        val cleanup = run(
            "rm -f -- ${KPM_DIR}/kpm-*.ko ${KPM_DIR}/*.kpm " +
                LEGACY_KPM_BOOT_SCRIPT.shellQuote()
        )
        if (cleanup.code != 0) {
            error("Cannot remove the persisted KPM files: ${cleanup.errorText()}")
        }

        val installedKsud = "/data/adb/ksud"
        val ksud = when {
            read("test -x ${installedKsud.shellQuote()} && echo yes || true") == "yes" -> installedKsud
            else -> runCatching { getKsuDaemonPath() }.getOrNull()
        }
        if (ksud == null) {
            onStderr("- ksud is unavailable; cannot unload live KPM modules")
        } else {
            val modules = runCatching { read("$ksud kpm list") }
                .getOrDefault("")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            for (name in modules) {
                val result = run("$ksud kpm unload ${name.shellQuote()}")
                val stillListed = run("$ksud kpm list").out
                    .lineSequence()
                    .any { it.trim() == name }
                if (result.code != 0 || stillListed) {
                    onStderr("- Could not unload KPM module $name: ${result.errorText()}")
                }
            }
        }

        // Always attempt rmmod, even when /proc/modules does not list kpm.
        // If it was absent the command is a harmless no-op; the authoritative
        // result is the /proc/modules check afterwards.
        onStdout("- Unloading kpm with rmmod")
        run("/system/bin/rmmod kpm 2>/dev/null || true")
        val stillLoaded =
            read("grep -q '^kpm ' /proc/modules && echo loaded || true") == "loaded"
        if (stillLoaded) {
            error(
                "KPM boot files were removed, but kpm.ko is still loaded because " +
                    "dependent KPM modules are busy. Reboot to finish removing it."
            )
        }
        onStdout("- KPM kernel module unloaded; persisted KPM files removed")
        FlashResult(0, "", false)
    } catch (e: Exception) {
        val message = e.message ?: "Failed to remove the KPM kernel module"
        onStderr(message)
        FlashResult(1, message, false)
    }
}

private data class ShellResult(val code: Int, val err: String, val out: String) {
    fun errorText(): String = err.ifBlank { out }.ifBlank { "exit $code" }
}

private fun validateKpmElf(file: File) {
    check(file.isFile && file.length() >= 1024) { "Selected .ko is empty" }
    val header = file.inputStream().use { it.readNBytes(64) }
    check(header.size == 64) { "Selected .ko is truncated" }
    check(
        header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
    ) { "Selected file is not an ELF .ko" }
    check(header[4] == 2.toByte()) { "Selected .ko is not 64-bit" }
    check(header[5] == 1.toByte()) { "Selected .ko is not little-endian" }
    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    check(buffer.getShort(16).toInt() == 1) { "Selected ELF is not a relocatable kernel module" }
    check(buffer.getShort(18).toInt() == 0xB7) { "Selected .ko is not AArch64" }
}
