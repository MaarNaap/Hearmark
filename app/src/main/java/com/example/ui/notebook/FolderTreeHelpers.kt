package com.example.ui

import com.example.data.Folder
import com.example.data.Note

// --- FOLDER HIERARCHY TREE HELPERS ---

data class FolderTreeNode(
    val folder: Folder,
    val children: List<FolderTreeNode> = emptyList(),
    val depth: Int = 0
)

fun buildFolderTree(folders: List<Folder>): List<FolderTreeNode> {
    if (folders.isEmpty()) return emptyList()
    val folderMap = folders.associateBy { it.id }
    val validIds = folderMap.keys

    val childrenMap = mutableMapOf<Long?, MutableList<Folder>>()
    for (folder in folders) {
        val parentId = if (folder.parentFolderId != null && validIds.contains(folder.parentFolderId) && folder.parentFolderId != folder.id) {
            folder.parentFolderId
        } else {
            // Path-based hierarchy fallback
            val pathParent = folders.filter { other ->
                other.id != folder.id && folder.folderPath.startsWith(other.folderPath + java.io.File.separator)
            }.maxByOrNull { it.folderPath.length }
            pathParent?.id
        }
        childrenMap.getOrPut(parentId) { mutableListOf() }.add(folder)
    }

    fun buildNodes(parentId: Long?, depth: Int): List<FolderTreeNode> {
        val list = childrenMap[parentId]?.sortedBy { it.folderName.lowercase() } ?: emptyList()
        return list.map { folder ->
            FolderTreeNode(
                folder = folder,
                children = buildNodes(folder.id, depth + 1),
                depth = depth
            )
        }
    }

    val roots = buildNodes(null, 0)
    val includedIds = mutableSetOf<Long>()
    fun collectIncluded(nodes: List<FolderTreeNode>) {
        nodes.forEach {
            includedIds.add(it.folder.id)
            collectIncluded(it.children)
        }
    }
    collectIncluded(roots)

    val remaining = folders.filterNot { includedIds.contains(it.id) }.map {
        FolderTreeNode(folder = it, children = emptyList(), depth = 0)
    }

    return roots + remaining
}

fun flattenFolderTree(
    nodes: List<FolderTreeNode>,
    expandedIds: Set<Long>,
    result: MutableList<FolderTreeNode> = mutableListOf()
): List<FolderTreeNode> {
    for (node in nodes) {
        result.add(node)
        if (node.children.isNotEmpty() && expandedIds.contains(node.folder.id)) {
            flattenFolderTree(node.children, expandedIds, result)
        }
    }
    return result
}

fun getFolderNotesCount(node: FolderTreeNode, notes: List<Note>): Int {
    var count = notes.count { it.folderId == node.folder.id }
    for (child in node.children) {
        count += getFolderNotesCount(child, notes)
    }
    return count
}

fun getAllNodeIds(node: FolderTreeNode): Set<Long> {
    val set = mutableSetOf(node.folder.id)
    for (child in node.children) {
        set.addAll(getAllNodeIds(child))
    }
    return set
}
