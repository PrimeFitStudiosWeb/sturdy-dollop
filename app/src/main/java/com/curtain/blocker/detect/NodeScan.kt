package com.curtain.blocker.detect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * One bounded walk of the active window's node tree. Walking is the expensive
 * part of this app, so we do it exactly once per evaluation and hand the
 * classifiers a flat summary.
 */
class NodeScan private constructor(
    val pkg: String,
    val ids: Set<String>,
    val descs: Set<String>,
    val texts: Set<String>,
    /** View ids of nodes that were marked selected — how we read the tab bar. */
    val selectedIds: Set<String>,
    val selectedDescs: Set<String>,
    /** Full-width bounds of any Shorts shelf found inside a feed. */
    val shortsShelves: List<Rect>
) {

    fun hasId(candidates: List<String>): Boolean =
        candidates.any { c -> ids.any { it.contains(c) } }

    fun hasDesc(candidates: List<String>): Boolean =
        candidates.any { c -> descs.any { it == c || it.startsWith("$c,") || it.startsWith("$c ") } }

    fun selected(candidates: List<String>): Boolean =
        candidates.any { c -> selectedIds.any { it.contains(c) } } ||
            candidates.any { c -> selectedDescs.any { it == c || it.startsWith("$c,") } }

    companion object {

        private const val MAX_NODES = 2500
        private const val MAX_DEPTH = 45

        fun of(root: AccessibilityNodeInfo?, screenWidth: Int): NodeScan? {
            if (root == null) return null
            val ids = HashSet<String>(128)
            val descs = HashSet<String>(64)
            val texts = HashSet<String>(64)
            val selectedIds = HashSet<String>(8)
            val selectedDescs = HashSet<String>(8)
            val shelfCandidates = ArrayList<AccessibilityNodeInfo>(4)
            var visited = 0

            fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || depth > MAX_DEPTH || visited >= MAX_NODES) return
                visited++

                val shortId = node.viewIdResourceName?.substringAfter("id/")
                if (shortId != null) {
                    ids.add(shortId)
                    if (node.isSelected) selectedIds.add(shortId)
                    if (Signatures.YT_SHORTS_SHELF.any { shortId.contains(it) }) {
                        shelfCandidates.add(node)
                    }
                }

                val desc = node.contentDescription?.toString()?.trim()?.lowercase()
                if (!desc.isNullOrEmpty() && desc.length <= 80) {
                    descs.add(desc)
                    if (node.isSelected) selectedDescs.add(desc)
                }

                val text = node.text?.toString()?.trim()?.lowercase()
                if (!text.isNullOrEmpty() && text.length <= 40) texts.add(text)

                for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
            }

            walk(root, 0)

            val shelves = shelfCandidates
                .mapNotNull { fullWidthAncestorBounds(it, screenWidth) }
                .distinct()

            return NodeScan(
                pkg = root.packageName?.toString().orEmpty(),
                ids = ids,
                descs = descs,
                texts = texts,
                selectedIds = selectedIds,
                selectedDescs = selectedDescs,
                shortsShelves = shelves
            )
        }

        /**
         * A Shorts shelf item sits several levels inside the row that we
         * actually want to cover. Climb until the node spans (nearly) the whole
         * screen width, then use that.
         */
        private fun fullWidthAncestorBounds(node: AccessibilityNodeInfo, screenWidth: Int): Rect? {
            var current: AccessibilityNodeInfo? = node
            var best: Rect? = null
            var hops = 0
            while (current != null && hops < 5) {
                val r = Rect().also { current!!.getBoundsInScreen(it) }
                if (r.width() > 0 && r.height() > 0) {
                    best = r
                    if (r.width() >= screenWidth * 0.9f) return r
                }
                current = current.parent
                hops++
            }
            return best
        }
    }
}
