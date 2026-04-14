package com.autotest.dsl

class Step(
    val name: String,
    private val action: () -> Unit
) {
    fun run() {
        action()
    }
}
