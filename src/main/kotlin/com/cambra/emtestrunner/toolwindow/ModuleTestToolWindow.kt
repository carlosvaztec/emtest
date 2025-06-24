package com.cambra.emtestrunner.toolwindow

import com.cambra.emtestrunner.settings.ModuleTestRunnerSettings
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ModuleTestToolWindow(private val project: Project) {

    private val contentPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private lateinit var debugCheckBox: JCheckBox
    private lateinit var hotDeployCheckBox: JCheckBox

    init {
        setupUI()
    }
    
    private fun setupUI() {
        // Title panel
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        titlePanel.add(JLabel("Module Test Runner"))

        // Button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val setupButton = JButton("Setup Hot Deploy")
        setupButton.addActionListener {
            executeHotDeploySetup()
        }
        buttonPanel.add(setupButton)

        // Settings panel
        val settingsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val settings = ModuleTestRunnerSettings.getInstance()

        // Debug notifications checkbox
        debugCheckBox = JCheckBox("Enable Debug Notifications", settings.enableDebugNotifications)
        debugCheckBox.addActionListener {
            settings.enableDebugNotifications = debugCheckBox.isSelected
        }
        settingsPanel.add(debugCheckBox)

        // Hot deploy checkbox
        hotDeployCheckBox = JCheckBox("Enable Hot Deploy", settings.enableHotDeploy)
        hotDeployCheckBox.addActionListener {
            settings.enableHotDeploy = hotDeployCheckBox.isSelected
        }
        settingsPanel.add(hotDeployCheckBox)

        // Main panel to hold button and settings panels
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(buttonPanel, BorderLayout.NORTH)
        mainPanel.add(settingsPanel, BorderLayout.CENTER)

        // Add panels to main content
        contentPanel.add(titlePanel, BorderLayout.NORTH)
        contentPanel.add(mainPanel, BorderLayout.CENTER)
    }
    
    private fun executeHotDeploySetup() {
        val settings = ModuleTestRunnerSettings.getInstance()
        val command = settings.hotDeploySetupCommand.replace("{NAMESPACE}", settings.namespace)

        // Show debug notification that setup is starting (only if debug is enabled)
        if (settings.enableDebugNotifications) {
            showNotification(
                "Debug: Hot Deploy Setup",
                "Starting hot deploy setup...",
                NotificationType.INFORMATION
            )
        }

        // Execute command in background
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val processBuilder = ProcessBuilder("sh", "-c", command)
                    .directory(java.io.File(project.basePath ?: "."))
                
                val processHandler = CapturingProcessHandler(processBuilder.start(), null, command)
                val result = processHandler.runProcess(60000) // 60 second timeout
                
                ApplicationManager.getApplication().invokeLater {
                    if (result.exitCode == 0) {
                        // Success
                        showNotification(
                            "Hot Deploy Setup Success",
                            "Hot deploy setup completed successfully.",
                            NotificationType.INFORMATION
                        )
                    } else {
                        // Failure - show command and error details
                        val errorMessage = "Hot deploy setup failed. Exit code: ${result.exitCode}"
                        val errorDetails = if (result.stderr.isNotEmpty()) result.stderr else result.stdout
                        
                        showNotification(
                            "Hot Deploy Setup Failed",
                            "Command: $command\n\n$errorMessage\n\nError details:\n$errorDetails",
                            NotificationType.ERROR
                        )
                    }
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    showNotification(
                        "Hot Deploy Setup Error",
                        "Command: $command\n\nError executing hot deploy setup: ${ex.message}",
                        NotificationType.ERROR
                    )
                }
            }
        }
    }
    
    private fun showNotification(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("PluginDebug")
            .createNotification(title, content, type)
            .notify(project)
    }
    
    fun getContent(): JComponent {
        return contentPanel
    }
}
