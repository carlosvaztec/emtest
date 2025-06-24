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

        // Debug panel
        val debugPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val settings = ModuleTestRunnerSettings.getInstance()
        debugCheckBox = JCheckBox("Enable Debug Notifications", settings.enableDebugNotifications)
        debugCheckBox.addActionListener {
            settings.enableDebugNotifications = debugCheckBox.isSelected
        }
        debugPanel.add(debugCheckBox)

        // Main panel to hold button and debug panels
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(buttonPanel, BorderLayout.NORTH)
        mainPanel.add(debugPanel, BorderLayout.CENTER)

        // Add panels to main content
        contentPanel.add(titlePanel, BorderLayout.NORTH)
        contentPanel.add(mainPanel, BorderLayout.CENTER)
    }
    
    private fun executeHotDeploySetup() {
        val settings = ModuleTestRunnerSettings.getInstance()
        val command = settings.hotDeploySetupCommand.replace("{NAMESPACE}", settings.namespace)
        
        // Show notification that setup is starting
        showNotification(
            "Hot Deploy Setup",
            "Starting hot deploy setup...",
            NotificationType.INFORMATION
        )
        
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
