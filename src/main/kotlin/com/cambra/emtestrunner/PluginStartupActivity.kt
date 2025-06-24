package com.cambra.emtestrunner

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Startup activity that initializes the TestClassTracker service when a project is opened
 */
class PluginStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // Initialize the TestClassTracker service to enable automatic tracking
        project.getService(TestClassTracker::class.java)
    }
}
