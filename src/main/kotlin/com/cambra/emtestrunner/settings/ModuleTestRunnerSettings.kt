package com.cambra.emtestrunner.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
    name = "ModuleTestRunnerSettings",
    storages = [Storage("ModuleTestRunnerSettings.xml")]
)
class ModuleTestRunnerSettings : PersistentStateComponent<ModuleTestRunnerSettings> {
    
    var buildRunMethodCommand: String = "echo \"Running test for: {METHOD_NAME}\"; echo \"This will take 5-10 minutes...\"; echo {METHOD_NAME} -p namespace={NAMESPACE}"
    var buildRunClassCommand: String = "echo \"Running tests for class: {CLASS_NAME}\"; echo \"This will take 5-10 minutes...\"; echo {CLASS_NAME} -p namespace={NAMESPACE}"
    var runMethodCommand: String = "echo \"Running method: {METHOD_NAME}\""
    var runClassCommand: String = "echo \"Running class: {CLASS_NAME}\""
    var copyCommand: String = "echo \"Copying {COMPILED_CLASS_PATH} to execution host\""
    var hotDeploySetupCommand: String = "echo \"Setting up hot deploy environment\""
    var removePackagePrefix: String = ""
    var namespace: String = ""
    var testModuleName: String = ""
    var enableAutoCopy: Boolean = true
    var enableDebugNotifications: Boolean = false

    override fun getState(): ModuleTestRunnerSettings = this
    
    override fun loadState(state: ModuleTestRunnerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    companion object {
        fun getInstance(): ModuleTestRunnerSettings {
            return ApplicationManager.getApplication().getService(ModuleTestRunnerSettings::class.java)
        }
    }
    
    /**
     * Replace placeholders in the command template with actual values
     */
    fun buildMethodCommand(fullMethodName: String): String {
        val processedMethodName = removePrefix(fullMethodName)
        return buildRunMethodCommand
            .replace("{METHOD_NAME}", processedMethodName)
            .replace("{NAMESPACE}", namespace)
    }

    fun buildClassCommand(fullClassName: String): String {
        val processedClassName = removePrefix(fullClassName)
        return buildRunClassCommand
            .replace("{CLASS_NAME}", processedClassName)
            .replace("{NAMESPACE}", namespace)
    }

    fun buildFastRunMethodCommand(fullMethodName: String): String {
        return runMethodCommand
            .replace("{METHOD_NAME}", fullMethodName)
            .replace("{NAMESPACE}", namespace)
    }

    fun buildFastRunClassCommand(fullClassName: String): String {
        return runClassCommand
            .replace("{CLASS_NAME}", fullClassName)
            .replace("{NAMESPACE}", namespace)
    }

    /**
     * Remove the configured package prefix from the given name
     */
    private fun removePrefix(name: String): String {
        return if (removePackagePrefix.isNotEmpty() && name.startsWith(removePackagePrefix)) {
            name.removePrefix(removePackagePrefix).removePrefix(".")
        } else {
            name
        }
    }
}
