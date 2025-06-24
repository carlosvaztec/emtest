package com.cambra.emtestrunner.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class TestRunnerConfigurable : Configurable {
    
    private var settingsComponent: TestRunnerSettingsComponent? = null
    
    override fun getDisplayName(): String = "Module Test Runner"
    
    override fun getPreferredFocusedComponent(): JComponent? {
        return settingsComponent?.getPreferredFocusedComponent()
    }
    
    override fun createComponent(): JComponent? {
        settingsComponent = TestRunnerSettingsComponent()
        return settingsComponent?.panel
    }
    
    override fun isModified(): Boolean {
        val settings = ModuleTestRunnerSettings.getInstance()
        return settingsComponent?.methodCommandText != settings.buildRunMethodCommand ||
               settingsComponent?.classCommandText != settings.buildRunClassCommand ||
               settingsComponent?.runMethodCommandText != settings.runMethodCommand ||
               settingsComponent?.runClassCommandText != settings.runClassCommand ||
               settingsComponent?.copyCommandText != settings.copyCommand ||
               settingsComponent?.hotDeploySetupCommandText != settings.hotDeploySetupCommand ||
               settingsComponent?.removePackagePrefixText != settings.removePackagePrefix ||
               settingsComponent?.namespaceText != settings.namespace ||
               settingsComponent?.testModuleNameText != settings.testModuleName ||
               settingsComponent?.enableHotDeployChecked != settings.enableHotDeploy ||
               settingsComponent?.enableDebugNotificationsChecked != settings.enableDebugNotifications
    }
    
    override fun apply() {
        val settings = ModuleTestRunnerSettings.getInstance()
        settingsComponent?.let {
            settings.buildRunMethodCommand = it.methodCommandText
            settings.buildRunClassCommand = it.classCommandText
            settings.runMethodCommand = it.runMethodCommandText
            settings.runClassCommand = it.runClassCommandText
            settings.copyCommand = it.copyCommandText
            settings.hotDeploySetupCommand = it.hotDeploySetupCommandText
            settings.removePackagePrefix = it.removePackagePrefixText
            settings.namespace = it.namespaceText
            settings.testModuleName = it.testModuleNameText
            settings.enableHotDeploy = it.enableHotDeployChecked
            settings.enableDebugNotifications = it.enableDebugNotificationsChecked
        }
    }
    
    override fun reset() {
        val settings = ModuleTestRunnerSettings.getInstance()
        settingsComponent?.let {
            it.methodCommandText = settings.buildRunMethodCommand
            it.classCommandText = settings.buildRunClassCommand
            it.runMethodCommandText = settings.runMethodCommand
            it.runClassCommandText = settings.runClassCommand
            it.copyCommandText = settings.copyCommand
            it.hotDeploySetupCommandText = settings.hotDeploySetupCommand
            it.removePackagePrefixText = settings.removePackagePrefix
            it.namespaceText = settings.namespace
            it.testModuleNameText = settings.testModuleName
            it.enableHotDeployChecked = settings.enableHotDeploy
            it.enableDebugNotificationsChecked = settings.enableDebugNotifications
        }
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
}

class TestRunnerSettingsComponent {
    val panel: DialogPanel
    private var methodCommandField: Cell<com.intellij.ui.components.JBTextArea>? = null
    private var classCommandField: Cell<com.intellij.ui.components.JBTextArea>? = null
    private var runMethodCommandField: Cell<com.intellij.ui.components.JBTextArea>? = null
    private var runClassCommandField: Cell<com.intellij.ui.components.JBTextArea>? = null
    private var copyCommandField: Cell<com.intellij.ui.components.JBTextArea>? = null
    private var hotDeploySetupCommandField: Cell<com.intellij.ui.components.JBTextArea>? = null
    private var removePackagePrefixField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var namespaceField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var testModuleNameField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var enableHotDeployField: Cell<com.intellij.ui.components.JBCheckBox>? = null
    private var enableDebugNotificationsField: Cell<com.intellij.ui.components.JBCheckBox>? = null

    var methodCommandText: String
        get() = methodCommandField?.component?.text ?: ""
        set(value) { methodCommandField?.component?.text = value }

    var classCommandText: String
        get() = classCommandField?.component?.text ?: ""
        set(value) { classCommandField?.component?.text = value }

    var runMethodCommandText: String
        get() = runMethodCommandField?.component?.text ?: ""
        set(value) { runMethodCommandField?.component?.text = value }

    var runClassCommandText: String
        get() = runClassCommandField?.component?.text ?: ""
        set(value) { runClassCommandField?.component?.text = value }

    var copyCommandText: String
        get() = copyCommandField?.component?.text ?: ""
        set(value) { copyCommandField?.component?.text = value }

    var hotDeploySetupCommandText: String
        get() = hotDeploySetupCommandField?.component?.text ?: ""
        set(value) { hotDeploySetupCommandField?.component?.text = value }

    var removePackagePrefixText: String
        get() = removePackagePrefixField?.component?.text ?: ""
        set(value) { removePackagePrefixField?.component?.text = value }

    var namespaceText: String
        get() = namespaceField?.component?.text ?: ""
        set(value) { namespaceField?.component?.text = value }

    var testModuleNameText: String
        get() = testModuleNameField?.component?.text ?: ""
        set(value) { testModuleNameField?.component?.text = value }

    var enableHotDeployChecked: Boolean
        get() = enableHotDeployField?.component?.isSelected ?: true
        set(value) { enableHotDeployField?.component?.isSelected = value }

    var enableDebugNotificationsChecked: Boolean
        get() = enableDebugNotificationsField?.component?.isSelected ?: false
        set(value) { enableDebugNotificationsField?.component?.isSelected = value }

    init {
        panel = panel {
            group("Command Templates") {
                row {
                    label("Configure the commands that will be executed when running Java and Scala tests.")
                        .comment("Use {METHOD_NAME}, {CLASS_NAME}, and {NAMESPACE} as placeholders.")
                }
                
                row("Build Run Method Command:") {
                    methodCommandField = textArea()
                        .rows(3)
                        .comment("Command to build and run individual test methods. Use {METHOD_NAME} placeholder.")
                        .align(AlignX.FILL)
                }

                row("Build Run Class Command:") {
                    classCommandField = textArea()
                        .rows(3)
                        .comment("Command to build and run test classes. Use {CLASS_NAME} placeholder.")
                        .align(AlignX.FILL)
                }

                row("Run Method Command:") {
                    runMethodCommandField = textArea()
                        .rows(3)
                        .comment("Command to run for individual methods (run mode). Use {METHOD_NAME} placeholder.")
                        .align(AlignX.FILL)
                }

                row("Run Class Command:") {
                    runClassCommandField = textArea()
                        .rows(3)
                        .comment("Command to run for classes (run mode). Use {CLASS_NAME} placeholder.")
                        .align(AlignX.FILL)
                }

                row("Copy Command:") {
                    copyCommandField = textArea()
                        .rows(3)
                        .comment("Command to copy compiled class files. Use {COMPILED_CLASS_PATH} and {PACKAGE_PATH} placeholders.")
                        .align(AlignX.FILL)
                }

                row("Hot Deploy Setup Command:") {
                    hotDeploySetupCommandField = textArea()
                        .rows(3)
                        .comment("Command to set up hot deploy environment. Use {NAMESPACE} placeholder.")
                        .align(AlignX.FILL)
                }

                row("Remove Package Prefix:") {
                    removePackagePrefixField = textField()
                        .comment("Package prefix to remove from class/method names (e.g., 'com.example')")
                        .align(AlignX.FILL)
                }

                row("Namespace:") {
                    namespaceField = textField()
                        .comment("Namespace value to use in commands (e.g., 'dev', 'staging', 'prod')")
                        .align(AlignX.FILL)
                }

                row("Test Module Name:") {
                    testModuleNameField = textField()
                        .comment("Module name containing test classes (e.g., 'my-test-module'). Leave empty to use project root.")
                        .align(AlignX.FILL)
                }

                row {
                    enableHotDeployField = checkBox("Enable Hot Deploy")
                        .comment("Automatically track the current class being edited and copy compiled classes after successful compilation")
                }

                row {
                    enableDebugNotificationsField = checkBox("Enable Debug Notifications")
                        .comment("Show debug notifications for copy command placeholders and execution details")
                }
                
                row {
                    label("Available placeholders:")
                        .bold()
                }
                row {
                    label("• {METHOD_NAME} - Full qualified method name (e.g., com.example.TestClass#testMethod)")
                }
                row {
                    label("• {CLASS_NAME} - Full qualified class name (e.g., com.example.TestClass)")
                }
                row {
                    label("• {NAMESPACE} - Namespace value for environment-specific commands")
                }
                row {
                    label("• {COMPILED_CLASS_PATH} - Full path to the compiled .class file")
                }
                row {
                    label("• {PACKAGE_PATH} - Package name with dots replaced by slashes (e.g., com/example/package)")
                }

                row {
                    label("Examples:")
                        .bold()
                }
                row {
                    label("• mvn test -Dtest={METHOD_NAME} -p namespace={NAMESPACE}")
                }
                row {
                    label("• gradle test --tests {CLASS_NAME} -p namespace={NAMESPACE}")
                }
                row {
                    label("• sbt \"testOnly {CLASS_NAME}\" -p namespace={NAMESPACE}")
                }
                row {
                    label("• ./run-test.sh {METHOD_NAME} -p namespace={NAMESPACE}")
                }

                row {
                    label("Package prefix removal:")
                        .bold()
                }
                row {
                    label("If 'Remove Package Prefix' is set to 'com.example', then:")
                }
                row {
                    label("• 'com.example.service.TestClass#testMethod' becomes 'service.TestClass#testMethod'")
                }
                row {
                    label("• 'com.example.TestClass' becomes 'TestClass'")
                }
            }
        }
    }
    
    fun getPreferredFocusedComponent(): JComponent? {
        return methodCommandField?.component
    }
}
