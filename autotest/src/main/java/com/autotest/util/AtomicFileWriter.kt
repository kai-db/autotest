package com.autotest.util

import com.autotest.log.TestLogger
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 原子文件写：写临时文件 → rename 替换目标（同目录 rename 在多数文件系统是原子操作）。
 *
 * **fail-closed（B2）**：rename 失败时**保留旧目标文件不动**、临时文件转 `<file>.failed` 便于排查、
 * 显式报错并返回 false——**绝不回退直写覆盖目标**，避免进程中断留下半截 JSON 覆盖旧库、
 * load 端 catch 后按空库处理造成「静默清空全部资产」。
 */
object AtomicFileWriter {

    private val seq = AtomicLong(0)

    /** @return true 写入并原子替换成功；false 失败（旧文件保持不变） */
    fun writeText(file: File, text: String, logger: TestLogger? = null): Boolean {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger?.e("AtomicWrite", "目录创建失败，放弃写入（旧文件保持不变）: ${parent.absolutePath}")
            return false
        }
        val tmp = File(parent, "${file.name}.tmp.${seq.incrementAndGet()}")
        try {
            tmp.writeText(text)
        } catch (e: Throwable) {
            logger?.e("AtomicWrite", "临时文件写入失败（旧文件保持不变）: ${tmp.absolutePath} — ${e.message}", e)
            runCatching { tmp.delete() }
            return false
        }
        // 原子替换：优先 File.renameTo（同目录多数原子）；失败 fail-closed，不直写覆盖
        if (tmp.renameTo(file)) return true
        // rename 失败：保留旧目标不动，临时文件留证 <file>.failed，显式报错
        val failed = File(parent, "${file.name}.failed")
        runCatching { failed.delete() }
        val kept = runCatching { tmp.renameTo(failed) }.getOrDefault(false)
        logger?.e(
            "AtomicWrite",
            "原子替换失败，保留旧文件不覆盖（避免半截数据静默清库）: ${file.absolutePath}" +
                (if (kept) "，失败内容留存 ${failed.absolutePath}" else "，临时文件 ${tmp.absolutePath} 未能留存")
        )
        if (!kept) runCatching { tmp.delete() }
        return false
    }
}
