package com.cambra.emtestrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
// Scala imports - these will only work if Scala plugin is available
// import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
// import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalView
import com.cambra.emtestrunner.icons.PluginIcons
import com.cambra.emtestrunner.settings.ModuleTestRunnerSettings

open class RunModuleTestAction : AnAction() {

    companion object {
        private const val RUN_MODULE_TEST_FORMAT = "Module test '%s'"
    }

    protected open fun getDisplayFormat(): String = RUN_MODULE_TEST_FORMAT
    protected open fun getTerminalTabName(): String = "Module Test Runner"
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val element = e.getData(LangDataKeys.PSI_ELEMENT)
        val psiFile = e.getData(LangDataKeys.PSI_FILE)

        // Handle different PSI elements
        when {
            element is PsiMethod -> {
                if (!isJavaTestMethod(element)) return
                handleMethod(project, element)
            }

            element != null && isScalaFunction(element) -> {
                if (!isScalaTestMethod(element)) return
                handleScalaFunction(project, element)
            }

            element is PsiClass -> {
                handleClass(project, element)
            }

            element != null && isScalaClass(element) -> {
                handleScalaClass(project, element)
            }

            else -> {
                // Check if we're inside a test method body
                val containingTestMethod = findContainingTestMethod(element, psiFile)
                if (containingTestMethod != null) {
                    when {
                        containingTestMethod is PsiMethod -> handleMethod(project, containingTestMethod)
                        isScalaFunction(containingTestMethod) -> handleScalaFunction(project, containingTestMethod)
                    }
                } else {
                    // If no specific element is selected, check if file contains tests
                    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
                    val fileToCheck = psiFile ?: getFileFromVirtualFile(virtualFile, project)
                    if (fileToCheck != null && fileContainsTests(fileToCheck)) {
                        val classInFile = findClassInFile(fileToCheck, element) ?: findClassInFileByName(fileToCheck)
                        if (classInFile != null) {
                            when {
                                classInFile is PsiClass -> handleClass(project, classInFile)
                                isScalaClass(classInFile) -> handleScalaClass(project, classInFile)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val element = e.getData(LangDataKeys.PSI_ELEMENT)
        val psiFile = e.getData(LangDataKeys.PSI_FILE)

        // FIRST: Check if we're inside a test method body (highest priority)
        val containingTestMethod = findContainingTestMethod(element, psiFile)
        if (containingTestMethod != null) {
            e.presentation.isEnabledAndVisible = true
            val methodName = getTestMethodName(containingTestMethod)
            e.presentation.text = getDisplayFormat().format(methodName)
            e.presentation.icon = PluginIcons.RUN_ICON
            return
        }

        // Enable for test methods or any Java/Scala class
        when {
            element is PsiMethod -> {
                if (isJavaTestMethod(element)) {
                    e.presentation.isEnabledAndVisible = true
                    e.presentation.text = getDisplayFormat().format(element.name)
                    e.presentation.icon = PluginIcons.RUN_ICON
                } else {
                    e.presentation.isEnabledAndVisible = false
                }
            }
            element != null && isScalaFunction(element) -> {
                if (isScalaTestMethod(element)) {
                    e.presentation.isEnabledAndVisible = true
                    e.presentation.text = getDisplayFormat().format(getElementName(element))
                    e.presentation.icon = PluginIcons.RUN_ICON
                } else {
                    e.presentation.isEnabledAndVisible = false
                }
            }
            element is PsiClass -> {
                e.presentation.isEnabledAndVisible = true
                e.presentation.text = getDisplayFormat().format(element.name)
                e.presentation.icon = PluginIcons.RUN_ICON
            }
            element != null && isScalaClass(element) -> {
                e.presentation.isEnabledAndVisible = true
                e.presentation.text = getDisplayFormat().format(getElementName(element))
                e.presentation.icon = PluginIcons.RUN_ICON
            }
            else -> {
                // Check if we're in a file that contains any test methods
                // This handles both editor context and project view context
                val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
                val project = e.project
                val fileToCheck = psiFile ?: getFileFromVirtualFile(virtualFile, project)

                if (fileToCheck != null && fileContainsTests(fileToCheck)) {
                    e.presentation.isEnabledAndVisible = true
                    val fileName = fileToCheck.name.substringBeforeLast('.')
                    e.presentation.text = getDisplayFormat().format(fileName)
                    e.presentation.icon = PluginIcons.RUN_ICON
                } else {
                    e.presentation.isEnabledAndVisible = false
                }
            }
        }
    }
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT // Run update on background thread
    }

    // Helper method to find a class in the current file
    protected fun findClassInFile(psiFile: PsiFile?, currentElement: PsiElement?): PsiElement? {
        if (psiFile == null) return null

        // First, try to find the class that contains the current element
        if (currentElement != null) {
            val containingClass = PsiTreeUtil.getParentOfType(currentElement, PsiClass::class.java)
            if (containingClass != null) return containingClass

            // Try to find Scala class containing the current element
            val scalaClass = findScalaClassParent(currentElement)
            if (scalaClass != null) return scalaClass
        }

        // Look for any top-level class in the file (most common case)
        val javaClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
        if (javaClasses.isNotEmpty()) {
            // Return the first top-level class (not inner classes)
            return javaClasses.find { clazz ->
                clazz.parent == psiFile || clazz.parent?.parent == psiFile
            } ?: javaClasses.first()
        }

        // Look for Scala classes in the file
        val scalaClasses = findScalaClassesInFile(psiFile)
        if (scalaClasses.isNotEmpty()) {
            return scalaClasses.first()
        }

        return null
    }

    protected fun findScalaClassParent(element: PsiElement): PsiElement? {
        var current = element.parent
        while (current != null) {
            if (isScalaClass(current)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    protected fun findScalaClassesInFile(psiFile: PsiFile): List<PsiElement> {
        val scalaClasses = mutableListOf<PsiElement>()

        fun visitElement(element: PsiElement) {
            if (isScalaClass(element)) {
                scalaClasses.add(element)
            }
            for (child in element.children) {
                visitElement(child)
            }
        }

        visitElement(psiFile)
        return scalaClasses
    }

    // Additional method to find class by file name convention
    protected fun findClassInFileByName(psiFile: PsiFile?): PsiElement? {
        if (psiFile == null) return null

        // Get the file name without extension
        val fileName = psiFile.name.substringBeforeLast('.')

        // Look for Java classes that match the file name
        val javaClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
        val matchingJavaClass = javaClasses.find { it.name == fileName }
        if (matchingJavaClass != null) return matchingJavaClass

        // Look for Scala classes that match the file name
        val scalaClasses = findScalaClassesInFile(psiFile)
        val matchingScalaClass = scalaClasses.find { getElementName(it) == fileName }
        if (matchingScalaClass != null) return matchingScalaClass

        // If no exact match, return the first class found
        return javaClasses.firstOrNull() ?: scalaClasses.firstOrNull()
    }

    // Method to check if a file contains any test methods
    protected fun fileContainsTests(psiFile: PsiFile): Boolean {
        // Check for Java test methods
        val javaMethods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
        val hasJavaTests = javaMethods.any { isJavaTestMethod(it) }
        if (hasJavaTests) return true

        // Check for Scala test methods
        val scalaFunctions = findScalaFunctionsInFile(psiFile)
        val hasScalaTests = scalaFunctions.any { isScalaTestMethod(it) }
        if (hasScalaTests) return true

        return false
    }

    protected fun findScalaFunctionsInFile(psiFile: PsiFile): List<PsiElement> {
        val scalaFunctions = mutableListOf<PsiElement>()

        fun visitElement(element: PsiElement) {
            if (isScalaFunction(element)) {
                scalaFunctions.add(element)
            }
            for (child in element.children) {
                visitElement(child)
            }
        }

        visitElement(psiFile)
        return scalaFunctions
    }

    // Helper method to get PsiFile from VirtualFile (for project view context)
    protected fun getFileFromVirtualFile(virtualFile: VirtualFile?, project: com.intellij.openapi.project.Project?): PsiFile? {
        if (virtualFile == null || project == null) return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }

    // Helper method to find containing test method when cursor is inside method body
    protected fun findContainingTestMethod(element: PsiElement?, psiFile: PsiFile?): PsiElement? {
        if (element == null) return null

        // Try multiple approaches to find the containing test method

        // Approach 1: Use PsiTreeUtil.getParentOfType (most reliable for Java)
        val containingJavaMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (containingJavaMethod != null && isJavaTestMethod(containingJavaMethod)) {
            return containingJavaMethod
        }

        // Approach 2: Manual traversal starting from the element itself
        var current: PsiElement? = element
        while (current != null && current != psiFile) {
            if (current is PsiMethod && isJavaTestMethod(current)) {
                return current
            }
            if (isScalaFunction(current) && isScalaTestMethod(current)) {
                return current
            }
            current = current.parent
        }

        // Approach 3: Handle specific cases like method calls (assertTrue, etc.)
        // Check if we're inside a method call expression and find its containing method
        val methodCallExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false)
        if (methodCallExpression != null) {
            val containingMethodFromCall = PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethod::class.java, false)
            if (containingMethodFromCall != null && isJavaTestMethod(containingMethodFromCall)) {
                return containingMethodFromCall
            }
        }

        // Approach 4: For method references/calls, check if we're inside a method call expression
        // This handles cases like assertTrue(...) where element might be the method reference
        var parent = element.parent
        var depth = 0
        while (parent != null && parent != psiFile && depth < 20) { // Limit depth to avoid infinite loops
            if (parent is PsiMethod && isJavaTestMethod(parent)) {
                return parent
            }
            if (isScalaFunction(parent) && isScalaTestMethod(parent)) {
                return parent
            }
            parent = parent.parent
            depth++
        }

        return null
    }

    // Helper method to get test method name for display
    protected fun getTestMethodName(testMethod: PsiElement): String {
        return when {
            testMethod is PsiMethod -> testMethod.name
            isScalaFunction(testMethod) -> getElementName(testMethod) ?: "unknownMethod"
            else -> "unknownMethod"
        }
    }

    // Protected methods for command building - can be overridden by subclasses
    protected open fun buildMethodCommand(fullMethodName: String): String {
        val settings = ModuleTestRunnerSettings.getInstance()
        return settings.buildMethodCommand(fullMethodName)
    }

    protected open fun buildClassCommand(className: String): String {
        val settings = ModuleTestRunnerSettings.getInstance()
        return settings.buildClassCommand(className)
    }

    // Helper methods for handling different element types
    protected fun handleMethod(project: com.intellij.openapi.project.Project, method: PsiMethod) {
        val containingClass = method.containingClass
        val qualifiedName = containingClass?.qualifiedName ?: "UnknownClass"
        val packageName = qualifiedName.substringBeforeLast('.', "")
        val className = containingClass?.name ?: "UnknownClass"
        val methodName = method.name

        val fullMethodName = if (packageName.isEmpty()) {
            "$className#$methodName"
        } else {
            "$packageName.$className#$methodName"
        }

        val command = buildMethodCommand(fullMethodName)
        runCommandInTerminal(project, command)
    }

    protected fun handleClass(project: com.intellij.openapi.project.Project, clazz: PsiClass) {
        val qualifiedName = clazz.qualifiedName ?: "UnknownClass"
        val command = buildClassCommand(qualifiedName)
        runCommandInTerminal(project, command)
    }

    protected fun handleScalaFunction(project: com.intellij.openapi.project.Project, element: PsiElement) {
        try {
            val containingClass = getScalaContainingClass(element)
            val qualifiedName = getScalaQualifiedName(containingClass) ?: "UnknownClass"
            val packageName = qualifiedName.substringBeforeLast('.', "")
            val className = getElementName(containingClass) ?: "UnknownClass"
            val methodName = getElementName(element) ?: "unknownMethod"

            val fullMethodName = if (packageName.isEmpty()) {
                "$className#$methodName"
            } else {
                "$packageName.$className#$methodName"
            }

            val command = buildMethodCommand(fullMethodName)
            runCommandInTerminal(project, command)
        } catch (e: Exception) {
            // Fallback if reflection fails
            Messages.showErrorDialog(project, "Error handling Scala function: ${e.message}", "Scala Support Error")
        }
    }

    protected fun handleScalaClass(project: com.intellij.openapi.project.Project, element: PsiElement) {
        try {
            val qualifiedName = getScalaQualifiedName(element) ?: "UnknownClass"
            val command = buildClassCommand(qualifiedName)
            runCommandInTerminal(project, command)
        } catch (e: Exception) {
            // Fallback if reflection fails
            Messages.showErrorDialog(project, "Error handling Scala class: ${e.message}", "Scala Support Error")
        }
    }

    // Reflection-based helpers for Scala support
    protected fun isScalaFunction(element: PsiElement?): Boolean {
        return element?.javaClass?.name == "org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction"
    }

    protected fun isScalaClass(element: PsiElement?): Boolean {
        return element?.javaClass?.name == "org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass"
    }

    protected fun getElementName(element: PsiElement?): String? {
        return try {
            val nameMethod = element?.javaClass?.getMethod("name")
            nameMethod?.invoke(element) as? String
        } catch (e: Exception) {
            null
        }
    }

    protected fun getScalaContainingClass(element: PsiElement?): PsiElement? {
        return try {
            val containingClassMethod = element?.javaClass?.getMethod("containingClass")
            containingClassMethod?.invoke(element) as? PsiElement
        } catch (e: Exception) {
            null
        }
    }

    protected fun getScalaQualifiedName(element: PsiElement?): String? {
        return try {
            val qualifiedNameMethod = element?.javaClass?.getMethod("qualifiedName")
            qualifiedNameMethod?.invoke(element) as? String
        } catch (e: Exception) {
            null
        }
    }

    protected fun isJavaTestMethod(method: PsiMethod): Boolean {
        return method.modifierList.annotations.any { annotation: PsiAnnotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "org.junit.Test" || // JUnit 4
                    qualifiedName == "org.junit.jupiter.api.Test" || // JUnit 5
                    qualifiedName == "org.testng.annotations.Test" // TestNG
        }
    }

    protected fun isScalaTestMethod(element: PsiElement?): Boolean {
        if (!isScalaFunction(element)) return false

        try {
            // Check method name for ScalaTest conventions
            val methodName = getElementName(element) ?: return false
            if (methodName.startsWith("test")) return true

            // Check if containing class extends ScalaTest base classes
            val containingClass = getScalaContainingClass(element)
            val qualifiedName = getScalaQualifiedName(containingClass) ?: return false

            return qualifiedName.contains("scalatest") ||
                   qualifiedName.contains("FunSuite") ||
                   qualifiedName.contains("FlatSpec") ||
                   qualifiedName.contains("WordSpec") ||
                   qualifiedName.contains("FeatureSpec")
        } catch (e: Exception) {
            return false
        }
    }

    // Helper method to run a command in terminal
    protected fun runCommandInTerminal(project: com.intellij.openapi.project.Project, command: String) {
        try {
            // Get terminal view
            val terminalView = TerminalView.getInstance(project)
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalWindow = toolWindowManager.getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

            // Make sure terminal window is visible
            terminalWindow?.show {
                // Create a new terminal tab
                val terminal = terminalView.createLocalShellWidget(project.basePath, getTerminalTabName())

                // Execute the command
                if (terminal is ShellTerminalWidget) {
                    terminal.executeCommand(command)
                }
            }
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Error executing in terminal: ${ex.message}",
                "Terminal Execution Error"
            )
        }
    }
}
