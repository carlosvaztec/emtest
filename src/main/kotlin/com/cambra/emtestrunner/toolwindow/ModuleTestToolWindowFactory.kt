package com.cambra.emtestrunner.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ModuleTestToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val moduleTestToolWindow = ModuleTestToolWindow(project)
        val content = ContentFactory.getInstance().createContent(moduleTestToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}
