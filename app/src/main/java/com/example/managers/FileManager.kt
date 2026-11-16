package com.example.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class NovaFileEntry(
    val name: String,
    val uriString: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String,
    val lastModified: Long,
    val isSandboxFile: Boolean
)

class FileManager(private val context: Context) {

    fun getSandboxDir(): File {
        val dir = context.getExternalFilesDir("nova_workspace") ?: File(context.filesDir, "nova_workspace")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listSandboxFiles(): List<NovaFileEntry> {
        val root = getSandboxDir()
        return root.listFiles().orEmpty()
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                NovaFileEntry(
                    name = file.name,
                    uriString = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    ).toString(),
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    mimeType = guessMime(file.name),
                    lastModified = file.lastModified(),
                    isSandboxFile = true
                )
            }
    }

    fun createSandboxFolder(name: String): Boolean {
        val clean = sanitizeFileName(name)
        if (clean.isEmpty()) return false
        val target = File(getSandboxDir(), clean)
        return target.exists() || target.mkdirs()
    }

    fun createSandboxSampleNoteFile(fileName: String, content: String): NovaFileEntry? {
        val clean = sanitizeFileName(fileName).ifEmpty { "nova_note.txt" }
        val file = File(getSandboxDir(), clean)
        file.writeText(content)
        return listSandboxFiles().firstOrNull { it.name == clean }
    }

    fun listSafTree(treeUriString: String): List<NovaFileEntry> {
        if (treeUriString.isBlank()) return emptyList()
        val uri = Uri.parse(treeUriString)
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        return doc.listFiles()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
            .map { item ->
                NovaFileEntry(
                    name = item.name ?: "Unnamed",
                    uriString = item.uri.toString(),
                    isDirectory = item.isDirectory,
                    sizeBytes = item.length(),
                    mimeType = item.type ?: guessMime(item.name.orEmpty()),
                    lastModified = item.lastModified(),
                    isSandboxFile = false
                )
            }
    }

    fun searchFiles(query: String, treeUriString: String): List<NovaFileEntry> {
        val combined = listSandboxFiles() + listSafTree(treeUriString)
        if (query.isBlank()) return combined
        return combined.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    fun createFolder(treeUriString: String, folderName: String): Pair<Boolean, String> {
        val clean = sanitizeFileName(folderName)
        if (clean.isEmpty()) return false to "Invalid folder name."
        if (treeUriString.isBlank()) {
            val ok = createSandboxFolder(clean)
            return ok to if (ok) "Created folder '$clean' in NOVA workspace." else "Could not create folder."
        }
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
            ?: return false to "SAF folder access expired."
        val created = parent.createDirectory(clean)
        return (created != null) to if (created != null) "Created folder '$clean'." else "Failed to create folder."
    }

    fun renameEntry(entry: NovaFileEntry, newName: String): Pair<Boolean, String> {
        val clean = sanitizeFileName(newName)
        if (clean.isEmpty()) return false to "Invalid name."
        return if (entry.isSandboxFile) {
            val file = File(getSandboxDir(), entry.name)
            val target = File(getSandboxDir(), clean)
            val ok = file.exists() && file.renameTo(target)
            ok to if (ok) "Renamed to '$clean'." else "Rename failed."
        } else {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.uriString))
            val ok = doc?.renameTo(clean) == true
            ok to if (ok) "Renamed to '$clean'." else "Rename failed in SAF folder."
        }
    }

    fun copyToSandbox(entry: NovaFileEntry, copyName: String = "copy_${entry.name}"): Pair<Boolean, String> {
        if (entry.isDirectory) return false to "Folder copy not supported; select a file."
        return runCatching {
            val dest = File(getSandboxDir(), sanitizeFileName(copyName))
            context.contentResolver.openInputStream(Uri.parse(entry.uriString))?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return false to "Cannot read source file."
            true to "Copied to NOVA workspace as '${dest.name}'."
        }.getOrElse {
            false to "Copy failed: ${it.localizedMessage}"
        }
    }

    fun moveToSandbox(entry: NovaFileEntry): Pair<Boolean, String> {
        val (copied, msg) = copyToSandbox(entry, entry.name)
        if (!copied) return false to msg
        val (deleted, _) = deleteEntry(entry)
        return if (deleted) {
            true to "Moved '${entry.name}' to NOVA workspace."
        } else {
            true to "Copied '${entry.name}' to NOVA workspace (source kept due to provider lock)."
        }
    }

    fun deleteEntry(entry: NovaFileEntry): Pair<Boolean, String> {
        return if (entry.isSandboxFile) {
            val file = File(getSandboxDir(), entry.name)
            val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
            ok to if (ok) "Deleted '${entry.name}'." else "Failed to delete '${entry.name}'."
        } else {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.uriString))
            val ok = doc?.delete() == true
            ok to if (ok) "Deleted '${entry.name}'." else "SAF provider refused deletion."
        }
    }

    fun openEntry(entry: NovaFileEntry): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(entry.uriString), entry.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened '${entry.name}'."
        }.getOrElse {
            "No viewer installed for ${entry.mimeType}."
        }
    }

    fun shareEntry(entry: NovaFileEntry): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = entry.mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(entry.uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Share ${entry.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Sharing '${entry.name}'."
        }.getOrElse {
            "Share failed: ${it.localizedMessage}"
        }
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
