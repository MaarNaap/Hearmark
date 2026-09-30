package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.data.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// --- REAL AUDIO IMPORT SYSTEM EXTENSIONS ---
fun AppViewModel.importFolder(folderName: String, uris: List<Uri>) {
    viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val resolvedFolderName = if (folderName.isBlank()) {
            if (Loc.currentLanguage == "ar") "مجلد مستورد" else "Imported Folder"
        } else folderName
        val parentDir = File(context.filesDir, "imported")
        val folderDir = File(parentDir, resolvedFolderName.replace("/", "_").replace(" ", "_"))
        folderDir.mkdirs()

        // 1. Create Folder record in Room
        val folderPath = folderDir.absolutePath
        val existingFolder = repository.getFolderByPath(folderPath)
        val folderId = if (existingFolder != null) {
            existingFolder.id
        } else {
            repository.addFolder(folderPath, resolvedFolderName)
        }

        var totalProcessed = 0
        var newlyIndexedCount = 0
        var existingCount = 0
        val newlyIndexedFiles = mutableListOf<String>()

        // Separate subtitle URIs (.srt, .vtt, .lrc) and audio URIs
        val subUris = mutableListOf<Uri>()
        val audioUris = mutableListOf<Uri>()
        uris.forEach { uri ->
            val rawName = getFileNameFromUri(context, uri)?.lowercase() ?: ""
            if (rawName.endsWith(".srt") || rawName.endsWith(".vtt") || rawName.endsWith(".lrc")) {
                subUris.add(uri)
            } else {
                audioUris.add(uri)
            }
        }

        // Copy subtitle files into folderDir first
        subUris.forEach { uri ->
            try {
                val rawFileName = getFileNameFromUri(context, uri) ?: "sub_${System.currentTimeMillis()}"
                val extension = rawFileName.substringAfterLast(".", "srt")
                val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                val fileName = "$cleanBase.$extension"
                val targetFile = File(folderDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Copy each audio file and attach matching subtitle
        audioUris.forEach { uri ->
            try {
                totalProcessed++
                val rawFileName = getFileNameFromUri(context, uri) ?: "track_${System.currentTimeMillis()}"
                // Safely format name keeping extension, but replacing bad chars
                val extension = rawFileName.substringAfterLast(".", "mp3")
                val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                val fileName = "$cleanBase.$extension"
                
                val targetFile = File(folderDir, fileName)
                
                // Difference check: if track already exists, reuse it and preserve database metadata
                val existingTrack = repository.getTrackByPath(targetFile.absolutePath)
                if (existingTrack != null) {
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        // Copy stream only if physical file is missing or empty
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            targetFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                    val updatedSubPath = if (existingTrack.subtitlePath.isNullOrBlank() && existingTrack.subtitleContent.isNullOrBlank() && autoSub != null) {
                        autoSub.absolutePath
                    } else {
                        existingTrack.subtitlePath
                    }
                    repository.updateTrack(existingTrack.copy(
                        isMissing = false,
                        subtitlePath = updatedSubPath,
                        parentFolderId = folderId,
                        isIndependent = false
                    ))
                    existingCount++
                    return@forEach
                }
                
                // Copy stream
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    val duration = getAudioDuration(targetFile.absolutePath)
                    val fileNameWithoutExt = rawFileName.substringBeforeLast(".")
                    val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                    val track = AudioTrack(
                        filePath = targetFile.absolutePath,
                        fileName = fileNameWithoutExt,
                        duration = duration,
                        parentFolderId = folderId,
                        isIndependent = false,
                        subtitlePath = autoSub?.absolutePath
                    )
                    repository.insertTrack(track)
                    newlyIndexedCount++
                    newlyIndexedFiles.add(fileNameWithoutExt)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        withContext(Dispatchers.Main) {
            lastImportSummary.value = AppViewModel.ImportSummary(
                folderName = resolvedFolderName,
                totalProcessed = totalProcessed,
                newlyIndexedCount = newlyIndexedCount,
                existingCount = existingCount,
                newlyIndexedFiles = newlyIndexedFiles
            )
            Toast.makeText(context, String.format(Loc.getText("imported_files_success"), newlyIndexedCount), Toast.LENGTH_SHORT).show()
            checkFilesSanity()
        }
    }
}

fun AppViewModel.importFolderFromTreeUri(folderName: String, treeUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val resolvedFolderName = if (folderName.isBlank()) {
            getDirNameFromTreeUri(context, treeUri)
        } else {
            folderName
        }
        val resolver = context.contentResolver
        
        // Prepare local target dir
        val parentDir = File(context.filesDir, "imported")
        
        var totalProcessed = 0
        var newlyIndexedCount = 0
        var existingCount = 0
        val newlyIndexedFiles = mutableListOf<String>()
        
        try {
            // Ensure proper persistable permission (for security standard)
            try {
                resolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val rootDocId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            
            // Define local ScanTask representation with parentFolderId and parentLocalDir
            class ScanTask(
                val docId: String,
                val folderName: String,
                val parentFolderId: Long?,
                val parentLocalDir: File
            )
            val scanQueue = kotlin.collections.ArrayDeque<ScanTask>()
            scanQueue.add(ScanTask(rootDocId, resolvedFolderName, null, parentDir))
            
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            
            while (scanQueue.isNotEmpty()) {
                val currentTask = scanQueue.removeFirst()
                val currentDocId = currentTask.docId
                val currentFolderName = currentTask.folderName
                val currentParentFolderId = currentTask.parentFolderId
                val currentParentLocalDir = currentTask.parentLocalDir
                
                val safeFolderName = currentFolderName.replace("/", "_").replace(" ", "_").replace(":", "_")
                val uniqueId = Math.abs(currentDocId.hashCode())
                val folderDir = File(currentParentLocalDir, "${safeFolderName}_$uniqueId")
                folderDir.mkdirs()

                // Always ensure a Room Folder record exists for this directory and links to parent
                val folderPath = folderDir.absolutePath
                val existingFolder = repository.getFolderByPath(folderPath)
                val folderId = if (existingFolder != null) {
                    if (currentParentFolderId != null && existingFolder.parentFolderId != currentParentFolderId) {
                        repository.dao.updateFolder(existingFolder.copy(parentFolderId = currentParentFolderId))
                    }
                    existingFolder.id
                } else {
                    repository.addFolder(folderPath, currentFolderName, currentParentFolderId)
                }

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                val filesToImport = mutableListOf<Triple<String, String, String>>() // childDocId, displayName, mimeType
                val subtitlesToImport = mutableListOf<Triple<String, String, String>>() // childDocId, displayName, mimeType
                val subfoldersToScan = mutableListOf<Pair<String, String>>() // childDocId, subfolderName
                
                try {
                    resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        
                        if (idCol != -1 && nameCol != -1 && mimeCol != -1) {
                            while (cursor.moveToNext()) {
                                val childDocId = cursor.getString(idCol)
                                val displayName = cursor.getString(nameCol) ?: "track_${System.currentTimeMillis()}"
                                val mimeType = cursor.getString(mimeCol)
                                
                                val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                                if (isDir) {
                                    subfoldersToScan.add(Pair(childDocId, displayName))
                                } else {
                                    val lowerName = displayName.lowercase()
                                    val isSubtitle = lowerName.endsWith(".srt") || lowerName.endsWith(".vtt") || lowerName.endsWith(".lrc")
                                    val isMedia = mimeType?.startsWith("audio/") == true || 
                                                  mimeType?.startsWith("video/") == true ||
                                                  lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || 
                                                  lowerName.endsWith(".m4a") || lowerName.endsWith(".ogg") || 
                                                  lowerName.endsWith(".aac") || lowerName.endsWith(".wma") || lowerName.endsWith(".flac") ||
                                                  lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") ||
                                                  lowerName.endsWith(".avi") || lowerName.endsWith(".mov") || lowerName.endsWith(".3gp") ||
                                                  lowerName.endsWith(".flv") || lowerName.endsWith(".m4v") || lowerName.endsWith(".ts")
                                    if (isSubtitle) {
                                        subtitlesToImport.add(Triple(childDocId, displayName, mimeType ?: ""))
                                    } else if (isMedia) {
                                        filesToImport.add(Triple(childDocId, displayName, mimeType ?: ""))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Copy subtitle files first
                for ((childDocId, displayName, _) in subtitlesToImport) {
                    try {
                        val subUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val extension = displayName.substringAfterLast(".", "srt")
                        val cleanBase = displayName.substringBeforeLast(".").replace("/", "_").replace(" ", "_").replace(":", "_")
                        val fileName = "$cleanBase.$extension"
                        val targetSubFile = File(folderDir, fileName)
                        resolver.openInputStream(subUri)?.use { inputStream ->
                            targetSubFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                for ((childDocId, displayName, mimeType) in filesToImport) {
                    try {
                        totalProcessed++
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val extension = displayName.substringAfterLast(".", "mp3")
                        val cleanBase = displayName.substringBeforeLast(".").replace("/", "_").replace(" ", "_").replace(":", "_")
                        val fileName = "$cleanBase.$extension"
                        
                        val targetFile = File(folderDir, fileName)
                        
                        // Difference check: if track already exists, reuse it and preserve database metadata
                        val existingTrack = repository.getTrackByPath(targetFile.absolutePath)
                        if (existingTrack != null) {
                            if (!targetFile.exists() || targetFile.length() == 0L) {
                                // Copy stream only if physical file is missing or empty
                                resolver.openInputStream(childUri)?.use { inputStream ->
                                    targetFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                            val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                            val updatedSubPath = if (existingTrack.subtitlePath.isNullOrBlank() && existingTrack.subtitleContent.isNullOrBlank() && autoSub != null) {
                                autoSub.absolutePath
                            } else {
                                existingTrack.subtitlePath
                            }
                            repository.updateTrack(existingTrack.copy(isMissing = false, subtitlePath = updatedSubPath, parentFolderId = folderId))
                            existingCount++
                            continue
                        }

                        resolver.openInputStream(childUri)?.use { inputStream ->
                            targetFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        
                        if (targetFile.exists() && targetFile.length() > 0) {
                            val duration = getAudioDuration(targetFile.absolutePath)
                            val fileNameWithoutExt = displayName.substringBeforeLast(".")
                            val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                            val track = AudioTrack(
                                filePath = targetFile.absolutePath,
                                fileName = fileNameWithoutExt,
                                duration = duration,
                                parentFolderId = folderId,
                                isIndependent = false,
                                subtitlePath = autoSub?.absolutePath
                            )
                            repository.insertTrack(track)
                            newlyIndexedCount++
                            newlyIndexedFiles.add(fileNameWithoutExt)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Add subdirectories to scanning queue with folderId as parentFolderId and folderDir as parentLocalDir
                for ((subDocId, subName) in subfoldersToScan) {
                    scanQueue.add(ScanTask(subDocId, subName, folderId, folderDir))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        withContext(Dispatchers.Main) {
            lastImportSummary.value = AppViewModel.ImportSummary(
                folderName = resolvedFolderName,
                totalProcessed = totalProcessed,
                newlyIndexedCount = newlyIndexedCount,
                existingCount = existingCount,
                newlyIndexedFiles = newlyIndexedFiles
            )
            Toast.makeText(context, String.format(Loc.getText("imported_files_success"), newlyIndexedCount), Toast.LENGTH_SHORT).show()
            checkFilesSanity()
        }
    }
}

fun AppViewModel.importIndependentTracks(uris: List<Uri>) {
    viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val parentDir = File(context.filesDir, "imported")
        val independentDir = File(parentDir, "Independent")
        independentDir.mkdirs()

        val subUris = mutableListOf<Uri>()
        val audioUris = mutableListOf<Uri>()
        uris.forEach { uri ->
            val rawName = getFileNameFromUri(context, uri)?.lowercase() ?: ""
            if (rawName.endsWith(".srt") || rawName.endsWith(".vtt") || rawName.endsWith(".lrc")) {
                subUris.add(uri)
            } else {
                audioUris.add(uri)
            }
        }

        // Copy subtitle files first
        subUris.forEach { uri ->
            try {
                val rawFileName = getFileNameFromUri(context, uri) ?: "sub_${System.currentTimeMillis()}"
                val extension = rawFileName.substringAfterLast(".", "srt")
                val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                val fileName = "$cleanBase.$extension"
                val targetFile = File(independentDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        var successCount = 0
        audioUris.forEach { uri ->
            try {
                val rawFileName = getFileNameFromUri(context, uri) ?: "track_${System.currentTimeMillis()}"
                val extension = rawFileName.substringAfterLast(".", "mp3")
                val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                val fileName = "$cleanBase.$extension"
                
                val targetFile = File(independentDir, fileName)
                
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    val duration = getAudioDuration(targetFile.absolutePath)
                    val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                    val track = AudioTrack(
                        filePath = targetFile.absolutePath,
                        fileName = rawFileName.substringBeforeLast("."),
                        duration = duration,
                        parentFolderId = null,
                        isIndependent = true,
                        subtitlePath = autoSub?.absolutePath
                    )
                    repository.insertTrack(track)
                    successCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        withContext(Dispatchers.Main) {
            Toast.makeText(context, String.format(Loc.getText("imported_files_success"), successCount), Toast.LENGTH_SHORT).show()
            checkFilesSanity()
        }
    }
}

fun AppViewModel.getDirNameFromTreeUri(context: Context, treeUri: Uri): String {
    var folderName = Loc.getText("imported_folder_default")
    try {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val cursor = context.contentResolver.query(docUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIndex != -1) {
                    val name = it.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        folderName = name
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        treeUri.lastPathSegment?.let { segment ->
            val clean = segment.substringAfterLast(":").substringAfterLast("/")
            if (clean.isNotBlank()) {
                folderName = clean
            }
        }
    }
    return folderName
}

fun AppViewModel.getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun AppViewModel.getAudioDuration(filePath: String): Long {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        time?.toLongOrNull() ?: 10000L // 10s fallback
    } catch (e: Exception) {
        try {
            val mp = android.media.MediaPlayer()
            mp.setDataSource(filePath)
            mp.prepare()
            val duration = mp.duration.toLong()
            mp.release()
            duration
        } catch (ex: Exception) {
            10000L // 10s fallback
        }
    }
}
