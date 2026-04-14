package com.autotest.config

class ConfigLoader(
    private val global: Map<String, String>,
    private val app: Map<String, String>,
    private val env: Map<String, String>,
    private val cli: Map<String, String>
) {
    fun load(): Map<String, String> =
        global + app + env + cli
}
