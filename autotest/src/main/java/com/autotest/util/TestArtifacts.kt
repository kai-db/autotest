package com.autotest.util

import java.io.File

/**
 * 测试产物（截图/日志/报告）清理工具，防止在设备/本地无限堆积。
 */
object TestArtifacts {

    /**
     * 保留 [dir] 下（含子目录）按修改时间最近的 [keepRecent] 个文件，删除更旧的。
     * 当前 run 刚生成的文件是最新的，一般不会被误删。
     *
     * @param protectedNames 文件名白名单——**永不删除**（如 `fingerprints.json` 这类跨 run 持久资产，
     *   不能与易腐截图同池按 mtime 竞争存活；B3）。
     */
    fun cleanup(dir: File, keepRecent: Int = 100, protectedNames: Set<String> = emptySet()) {
        if (!dir.exists()) return
        val files = dir.walkTopDown()
            .filter { it.isFile && it.name !in protectedNames }
            .toList()
        if (files.size <= keepRecent) return
        files.sortedByDescending { it.lastModified() }
            .drop(keepRecent)
            .forEach { runCatching { it.delete() } }
    }
}
