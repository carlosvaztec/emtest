package com.cambra.emtestrunner

import com.cambra.emtestrunner.settings.ModuleTestRunnerSettings

class FastRunModuleTestAction : RunModuleTestAction() {

    companion object {
        private const val MODULE_TEST_FAST_FORMAT = "Module test '%s' (fast)"
    }

    override fun getDisplayFormat(): String = MODULE_TEST_FAST_FORMAT
    override fun getTerminalTabName(): String = "Module Test Runner (Fast)"

    override fun buildMethodCommand(fullMethodName: String): String {
        val settings = ModuleTestRunnerSettings.getInstance()
        return settings.buildFastRunMethodCommand(fullMethodName)
    }

    override fun buildClassCommand(className: String): String {
        val settings = ModuleTestRunnerSettings.getInstance()
        return settings.buildFastRunClassCommand(className)
    }
}
