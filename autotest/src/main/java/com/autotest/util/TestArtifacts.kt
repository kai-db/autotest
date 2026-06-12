package com.autotest.util

import java.io.File

/**
 * 测试产物（截图/日志/报告）清理工具，防止在设备/本地无限堆积。
 */
object TestArtifacts {

    /**
     * 保留 [dir] 下（含子目录）按修改时间最近的 [keepRecent] 个文件，删除更旧的。
     * 当前 run 刚生成的文件是最新的，不会被误删。
     */
    fun cleanup(dir: File, keepRecent: Int = 100) {
        if (!dir.exists()) return
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.size <= keepRecent) return
        files.sortedByDescending { it.lastModified() }
            .drop(keepRecent)
            .forEach { runCatching { it.delete() } }
    }
}
