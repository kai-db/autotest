package com.autotest.engine

/**
 * 从 Markdown 格式的 TEST_CASES.md 中解析测试用例定义。
 *
 * 支持的格式：
 * ```markdown
 * ## P0 — 分组名
 *
 * ### TC-001 用例名称
 * - 步骤：描述步骤内容
 * - 验证：描述验证标准
 *
 * ### TC-002 另一个用例
 * - 步骤：...
 * - 验证：...
 * ```
 */
object TestCaseParser {

    // Q4：正则提到 object 级一次编译（原先在 per-line 循环内每行重建）
    private val PRIORITY_REGEX = Regex("""^##\s+(P[012])\s*[—\-]""")
    private val CASE_REGEX = Regex("""^###\s+(TC-\d+)\s+(.+)""")
    private val STEP_REGEX = Regex("""^-\s*步骤[：:]\s*(.+)""")
    private val VERIFY_REGEX = Regex("""^-\s*验证[：:]\s*(.+)""")

    data class ParsedTestCase(
        val id: String,
        val name: String,
        val priority: String,
        val steps: List<String>,
        val verifications: List<String>,
        /** 用例体内以 - 或 * 开头但未匹配「步骤/验证」模式的行数——格式漂移信号，>0 时加载方应告警 */
        val unmatchedBullets: Int = 0
    )

    fun parse(markdown: String): List<ParsedTestCase> {
        val cases = mutableListOf<ParsedTestCase>()
        val lines = markdown.lines()

        var currentPriority = "P0"
        var currentId = ""
        var currentName = ""
        var currentSteps = mutableListOf<String>()
        var currentVerifications = mutableListOf<String>()
        var currentUnmatched = 0
        var inCase = false

        for (line in lines) {
            val trimmed = line.trim()

            // 匹配优先级分组: ## P0 — xxx 或 ## P0 - xxx
            val priorityMatch = PRIORITY_REGEX.find(trimmed)
            if (priorityMatch != null) {
                if (inCase) {
                    cases.add(buildCase(currentId, currentName, currentPriority, currentSteps, currentVerifications, currentUnmatched))
                    inCase = false
                }
                currentPriority = priorityMatch.groupValues[1]
                continue
            }

            // 匹配用例标题: ### TC-001 用例名称
            val caseMatch = CASE_REGEX.find(trimmed)
            if (caseMatch != null) {
                if (inCase) {
                    cases.add(buildCase(currentId, currentName, currentPriority, currentSteps, currentVerifications, currentUnmatched))
                }
                currentId = caseMatch.groupValues[1]
                currentName = caseMatch.groupValues[2].trim()
                currentSteps = mutableListOf()
                currentVerifications = mutableListOf()
                currentUnmatched = 0
                inCase = true
                continue
            }

            if (!inCase) continue

            // 匹配步骤: - 步骤：xxx 或 - 步骤: xxx
            val stepMatch = STEP_REGEX.find(trimmed)
            if (stepMatch != null) {
                currentSteps.add(stepMatch.groupValues[1].trim())
                continue
            }

            // 匹配验证: - 验证：xxx 或 - 验证: xxx
            val verifyMatch = VERIFY_REGEX.find(trimmed)
            if (verifyMatch != null) {
                currentVerifications.add(verifyMatch.groupValues[1].trim())
                continue
            }

            // 用例体内的 bullet 行没匹配上任何模式：计数留作格式漂移信号（如 "- 操作：" / "* 步骤："）
            if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                currentUnmatched++
            }
        }

        // 最后一个用例
        if (inCase) {
            cases.add(buildCase(currentId, currentName, currentPriority, currentSteps, currentVerifications, currentUnmatched))
        }

        return cases
    }

    /**
     * 从文件路径解析。
     */
    fun parseFile(filePath: String): List<ParsedTestCase> {
        val content = java.io.File(filePath).readText()
        return parse(content)
    }

    private fun buildCase(
        id: String,
        name: String,
        priority: String,
        steps: List<String>,
        verifications: List<String>,
        unmatchedBullets: Int
    ): ParsedTestCase {
        return ParsedTestCase(
            id = id,
            name = name,
            priority = priority,
            steps = steps.toList(),
            verifications = verifications.toList(),
            unmatchedBullets = unmatchedBullets
        )
    }
}
