package com.example.ui

import com.example.data.AudioTrack
import com.example.data.Folder

data class TrackStatItem(
    val trackId: Long,
    val name: String,
    val periodCount: Int,
    val totalAllTimeCount: Int
)

data class DayTrackItem(
    val trackId: Long,
    val trackName: String,
    val playCount: Int,
    val durationMs: Long,
    val totalActualMs: Long,
    val avgSpeed: Float,
    val attachedTasks: List<String>
)

data class DaySummaryItem(
    val dayKey: String,
    val dayDisplay: String,
    val timestamp: Long,
    val totalActualMs: Long,
    val totalContentMs: Long,
    val tracks: List<DayTrackItem>
)

fun formatStatsDuration(ms: Long): String {
    if (ms <= 0L) return if (Loc.currentLanguage == "ar") "0 د" else "0m"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (Loc.currentLanguage == "ar") {
        when {
            hours > 0 && minutes > 0 -> "$hours س $minutes د"
            hours > 0 -> "$hours س"
            minutes > 0 -> "$minutes د"
            else -> "$seconds ث"
        }
    } else {
        when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}

data class ActivityChartDataPoint(
    val timestamp: Long,
    val axisLabel: String,
    val showAxisLabel: Boolean,
    val fullTitle: String,
    val actualDurationMs: Long,
    val contentDurationMs: Long,
    val playCount: Int
)

// --- FOLDER & FILE TREE FILTER DATA STRUCTURES & HELPERS ---

sealed class FolderTreeItem {
    data class FolderRow(
        val node: FolderTreeNode,
        val depth: Int,
        val isExpanded: Boolean,
        val hasChildren: Boolean,
        val directTracksCount: Int,
        val totalTracksCount: Int,
        val allSubfolderIds: Set<Long>,
        val allRecursiveTrackIds: Set<Long>
    ) : FolderTreeItem()

    data class TrackRow(
        val track: AudioTrack,
        val depth: Int
    ) : FolderTreeItem()

    data class IndependentHeader(
        val count: Int,
        val isExpanded: Boolean
    ) : FolderTreeItem()
}

fun getAllSubfolderIds(parentFolderId: Long, allFolders: List<Folder>): Set<Long> {
    val set = mutableSetOf(parentFolderId)
    val children = allFolders.filter { it.parentFolderId == parentFolderId }
    for (child in children) {
        set.addAll(getAllSubfolderIds(child.id, allFolders))
    }
    return set
}

fun flattenFolderFileTree(
    nodes: List<FolderTreeNode>,
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>,
    expandedFolderIds: Set<Long>,
    searchQuery: String,
    independentExpanded: Boolean
): List<FolderTreeItem> {
    val result = mutableListOf<FolderTreeItem>()
    val query = searchQuery.trim().lowercase()

    fun processNode(node: FolderTreeNode, depth: Int) {
        val allSubIds = getAllSubfolderIds(node.folder.id, allFolders)
        val recursiveTracks = allTracks.filter { it.parentFolderId in allSubIds }
        val directTracks = allTracks.filter { it.parentFolderId == node.folder.id }
        val subfolderCount = node.children.size

        val folderMatches = query.isEmpty() || node.folder.folderName.lowercase().contains(query)
        val matchingTracks = if (query.isEmpty()) directTracks else directTracks.filter { it.fileName.lowercase().contains(query) }
        val anyChildMatches = if (query.isEmpty()) false else recursiveTracks.any { it.fileName.lowercase().contains(query) } || node.children.any { c ->
            allFolders.any { it.parentFolderId == c.folder.id && it.folderName.lowercase().contains(query) } || c.folder.folderName.lowercase().contains(query)
        }

        if (query.isEmpty() || folderMatches || matchingTracks.isNotEmpty() || anyChildMatches) {
            val isExpanded = if (query.isNotEmpty()) true else expandedFolderIds.contains(node.folder.id)
            val hasChildren = subfolderCount > 0 || directTracks.isNotEmpty()

            result.add(
                FolderTreeItem.FolderRow(
                    node = node,
                    depth = depth,
                    isExpanded = isExpanded,
                    hasChildren = hasChildren,
                    directTracksCount = directTracks.size,
                    totalTracksCount = recursiveTracks.size,
                    allSubfolderIds = allSubIds,
                    allRecursiveTrackIds = recursiveTracks.map { it.id }.toSet()
                )
            )

            if (isExpanded) {
                node.children.forEach { child ->
                    processNode(child, depth + 1)
                }
                val tracksToShow = if (query.isNotEmpty() && !folderMatches) matchingTracks else directTracks
                tracksToShow.forEach { track ->
                    result.add(
                        FolderTreeItem.TrackRow(
                            track = track,
                            depth = depth + 1
                        )
                    )
                }
            }
        }
    }

    nodes.forEach { processNode(it, 0) }

    val folderIds = allFolders.map { it.id }.toSet()
    val independentTracks = allTracks.filter { it.parentFolderId == null || !folderIds.contains(it.parentFolderId) }
    if (independentTracks.isNotEmpty()) {
        val matchingIndTracks = if (query.isEmpty()) independentTracks else independentTracks.filter { it.fileName.lowercase().contains(query) }
        if (matchingIndTracks.isNotEmpty()) {
            val isIndExpanded = if (query.isNotEmpty()) true else independentExpanded
            result.add(FolderTreeItem.IndependentHeader(count = independentTracks.size, isExpanded = isIndExpanded))
            if (isIndExpanded) {
                matchingIndTracks.forEach { track ->
                    result.add(FolderTreeItem.TrackRow(track = track, depth = 1))
                }
            }
        }
    }

    return result
}
