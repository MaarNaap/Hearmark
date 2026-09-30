package com.example

import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Task
import com.example.data.TaskTrackProgress
import com.example.ui.FolderTreeItem
import com.example.ui.FolderTreeNode
import com.example.ui.computeAllowedTrackIdsForFolderFiles
import com.example.ui.computeAllowedTrackIdsForTasks
import com.example.ui.flattenFolderFileTree
import com.example.ui.formatTimestampMs
import com.example.ui.getAllSubfolderIds
import com.example.ui.getSortedTracks
import com.example.ui.parseTimestampToMs
import org.junit.Assert.*
import org.junit.Test

class TrackSortingAndStatsModelsTest {

    private val tracks = listOf(
        AudioTrack(id = 1L, filePath = "/a/b.mp3", fileName = "Beta.mp3", duration = 30_000L, playCount = 5, listenedSegments = "0,1,2", parentFolderId = 10L),
        AudioTrack(id = 2L, filePath = "/a/a.mp3", fileName = "Alpha.mp3", duration = 10_000L, playCount = 1, listenedSegments = "0,1,2,3,4,5,6,7,8,9", parentFolderId = 20L),
        AudioTrack(id = 3L, filePath = "/a/g.mp3", fileName = "Gamma.mp3", duration = 20_000L, playCount = 9, listenedSegments = "", parentFolderId = null)
    )

    @Test
    fun getSortedTracks_sortsByAllCriteriaAscendingAndDescending() {
        assertEquals(listOf(2L, 1L, 3L), getSortedTracks(tracks, "name", isAscending = true).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), getSortedTracks(tracks, "name", isAscending = false).map { it.id })

        assertEquals(listOf(2L, 3L, 1L), getSortedTracks(tracks, "duration", isAscending = true).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), getSortedTracks(tracks, "progress", isAscending = true).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), getSortedTracks(tracks, "play_count", isAscending = true).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), getSortedTracks(tracks, "date", isAscending = true).map { it.id })
    }

    @Test
    fun timeFormatUtils_formatAndParseTimestampMs_roundTrip() {
        assertEquals("00:45", formatTimestampMs(45_000L))
        assertEquals("01:02:03", formatTimestampMs(3_723_000L))

        assertEquals(45_000L, parseTimestampToMs("45"))
        assertEquals(125_000L, parseTimestampToMs("02:05"))
        assertEquals(3_723_000L, parseTimestampToMs("01:02:03"))
        assertNull(parseTimestampToMs("invalid:time"))
    }

    @Test
    fun folderHierarchyAndAllowedTrackIds_resolvesRecursiveSubfoldersAndTasks() {
        val rootFolder = Folder(id = 10L, folderPath = "/root", folderName = "Root", parentFolderId = null)
        val childFolder = Folder(id = 20L, folderPath = "/root/child", folderName = "Child", parentFolderId = 10L)
        val allFolders = listOf(rootFolder, childFolder)

        val subIds = getAllSubfolderIds(10L, allFolders)
        assertEquals(setOf(10L, 20L), subIds)

        val allowedByFolder = computeAllowedTrackIdsForFolderFiles(
            selectedFolderIds = setOf(10L),
            selectedFileTrackIds = emptySet(),
            allFolders = allFolders,
            allTracks = tracks
        )
        assertEquals(setOf(1L, 2L), allowedByFolder)

        val task = Task(
            id = 100L,
            title = "Study Task",
            sourceType = "TRACKS",
            sourceId = null,
            targetType = "PLAY_COUNT",
            targetValue = 3,
            scheduledDays = "",
            reminderTime = "",
            startDate = 0L
        )
        val progress = listOf(TaskTrackProgress(taskId = 100L, trackId = 3L, completedPlayCount = 1, completedDays = "", isTrackCompleted = false))
        val allowedByTask = computeAllowedTrackIdsForTasks(setOf(100L), listOf(task), tracks, progress)
        assertEquals(setOf(3L), allowedByTask)

        val treeNodes = listOf(FolderTreeNode(folder = rootFolder, children = listOf(FolderTreeNode(folder = childFolder))))
        val flattened = flattenFolderFileTree(
            nodes = treeNodes,
            allFolders = allFolders,
            allTracks = tracks,
            expandedFolderIds = setOf(10L),
            searchQuery = "",
            independentExpanded = true
        )
        assertTrue(flattened.any { it is FolderTreeItem.FolderRow && it.node.folder.id == 10L })
        assertTrue(flattened.any { it is FolderTreeItem.IndependentHeader && it.count == 1 })
    }
}
