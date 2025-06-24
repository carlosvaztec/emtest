package com.cambra.emtestrunner

import com.cambra.emtestrunner.settings.ModuleTestRunnerSettings
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.messages.MessageBusConnection
import java.io.File
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class TestClassTracker(private val project: Project) : Disposable {
    // Change from public var to private var to avoid generating public setter
    private var currentTestClass: PsiClass? = null
    var currentTestFile: PsiFile? = null
    var currentScalaElement: PsiElement? = null
    var isScalaClass: Boolean = false
    private var messageBusConnection: MessageBusConnection? = null

    init {
        // Register compilation listener using MessageBus (modern approach)
        messageBusConnection = project.messageBus.connect()
        messageBusConnection?.subscribe(CompilerTopics.COMPILATION_STATUS, object : CompilationStatusListener {
            override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext) {
                val settings = com.cambra.emtestrunner.settings.ModuleTestRunnerSettings.getInstance()
                if (settings.enableAutoCopy && !aborted && errors == 0 && (currentTestClass != null || currentScalaElement != null)) {
                    // Compilation successful, copy the compiled class
                    copyCompiledClass()
                }
            }
        })

        // Register file editor listener to automatically track current class being edited
        messageBusConnection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                // Always try to update, let the method check settings internally
                updateCurrentTestClassFromEditor()
            }

            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                // Also update when a file is opened
                updateCurrentTestClassFromEditor()
            }
        })

        // Set initial test class from currently active editor
        updateCurrentTestClassFromEditor()
    }

    override fun dispose() {
        messageBusConnection?.disconnect()
        messageBusConnection = null
    }

    // Add a getter for currentTestClass if needed
    fun getCurrentTestClass(): PsiClass? = currentTestClass

    fun setCurrentTestClass(testClass: PsiClass) {
        currentTestClass = testClass
        currentTestFile = testClass.containingFile
        isScalaClass = false
        currentScalaElement = null
    }

    fun setCurrentScalaClass(element: PsiElement) {
        currentScalaElement = element
        currentTestFile = element.containingFile
        isScalaClass = true
        currentTestClass = null
    }

    /**
     * Automatically updates the current test class based on the currently active editor
     */
    private fun updateCurrentTestClassFromEditor() {
        // Check if auto-copy is enabled first
        val settings = com.cambra.emtestrunner.settings.ModuleTestRunnerSettings.getInstance()
        if (!settings.enableAutoCopy) {
            clearCurrentTestClass()
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ReadAction.run<RuntimeException> {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val selectedFiles = fileEditorManager.selectedFiles



                    if (selectedFiles.isNotEmpty()) {
                        val activeFile = selectedFiles[0]
                        val psiFile = PsiManager.getInstance(project).findFile(activeFile)



                        if (psiFile != null) {
                            // Use proper test detection logic that checks for actual test methods
                            val isTest = fileContainsTests(psiFile)

                            if (isTest) {
                                // Find the primary class in the file
                                val fileName = psiFile.name.substringBeforeLast('.')

                                when {
                                    psiFile is PsiJavaFile -> {
                                        val primaryClass = psiFile.classes.find { it.name == fileName }
                                        if (primaryClass != null) {
                                            setCurrentTestClass(primaryClass)
                                            ApplicationManager.getApplication().invokeLater {
                                                showNotification(
                                                    project,
                                                    "Auto-Track Success",
                                                    "Now tracking Java test class: ${primaryClass.name}",
                                                    NotificationType.INFORMATION
                                                )
                                            }
                                        }
                                    }
                                    psiFile.name.endsWith(".scala") -> {
                                        // For Scala, try to find any class-like element
                                        val scalaClass = findScalaClassByName(psiFile, fileName)

                                        if (scalaClass != null) {
                                            setCurrentScalaClass(scalaClass)
                                            ApplicationManager.getApplication().invokeLater {
                                                showNotification(
                                                    project,
                                                    "Auto-Track Success",
                                                    "Now tracking Scala test class: ${getElementName(scalaClass)}",
                                                    NotificationType.INFORMATION
                                                )
                                            }
                                        } else {
                                            // Show error message about Scala plugin requirement
                                            ApplicationManager.getApplication().invokeLater {
                                                showNotification(
                                                    project,
                                                    "Scala Plugin Required",
                                                    "Cannot track Scala test class '$fileName'. The Scala plugin must be installed and enabled for Scala file support.",
                                                    NotificationType.ERROR
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                clearCurrentTestClass()
                            }
                        } else {
                            clearCurrentTestClass()
                        }
                    } else {
                        clearCurrentTestClass()
                    }
                }
            } catch (e: Exception) {
                // Silently handle errors to avoid disrupting the user experience
            }
        }
    }

    /**
     * Clears the current test class tracking
     */
    private fun clearCurrentTestClass() {
        currentTestClass = null
        currentScalaElement = null
        currentTestFile = null
        isScalaClass = false
    }











    /**
     * Check if a file contains any test methods (Java or Scala)
     */
    private fun fileContainsTests(psiFile: PsiFile): Boolean {
        // Check for Java test methods
        val javaMethods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
        val hasJavaTests = javaMethods.any { isJavaTestMethod(it) }

        if (hasJavaTests) return true

        // For Scala files, check if Scala plugin is working properly
        if (psiFile.name.endsWith(".scala")) {
            // Check if this is a proper Scala PSI file
            val isProperScalaFile = psiFile.javaClass.name.contains("scala", ignoreCase = true)

            if (!isProperScalaFile) {
                // Scala plugin is not working properly
                ApplicationManager.getApplication().invokeLater {
                    showNotification(
                        project,
                        "Scala Plugin Required",
                        "Cannot detect Scala tests in ${psiFile.name}. The Scala plugin must be installed and enabled for Scala file support.",
                        NotificationType.ERROR
                    )
                }
                return false
            }

            // Check for Scala test methods using PSI
            val scalaFunctions = findScalaFunctionsInFile(psiFile)
            val hasScalaTests = scalaFunctions.any { isScalaTestMethod(it) }

            return hasScalaTests
        }

        return false
    }


    /**
     * Find all Scala functions in a file
     */
    private fun findScalaFunctionsInFile(psiFile: PsiFile): List<PsiElement> {
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

    /**
     * Check if a Java method is a test method
     */
    private fun isJavaTestMethod(method: PsiMethod): Boolean {
        return method.modifierList.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "org.junit.Test" || // JUnit 4
                    qualifiedName == "org.junit.jupiter.api.Test" || // JUnit 5
                    qualifiedName == "org.testng.annotations.Test" // TestNG
        }
    }

    /**
     * Check if a Scala element is a test method
     */
    private fun isScalaTestMethod(element: PsiElement?): Boolean {
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

    /**
     * Check if an element is a Scala function
     */
    private fun isScalaFunction(element: PsiElement?): Boolean {
        if (element == null) return false
        val className = element.javaClass.name
        return className == "org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition" ||
                className == "org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction"
    }

    /**
     * Get the containing Scala class for an element
     */
    private fun getScalaContainingClass(element: PsiElement?): PsiElement? {
        var current = element?.parent
        while (current != null) {
            if (isScalaClass(current)) {
                return current
            }
            current = current.parent
        }
        return null
    }



    /**
     * Finds a Scala class by name in a file
     */
    private fun findScalaClassByName(psiFile: PsiFile, className: String): PsiElement? {
        try {
            // Search recursively through the PSI tree
            fun findScalaClassRecursively(element: PsiElement): PsiElement? {
                // Check if current element is the class we're looking for
                if (isScalaClass(element) && getElementName(element) == className) {
                    return element
                }

                // Recursively search children
                for (child in element.children) {
                    val found = findScalaClassRecursively(child)
                    if (found != null) return found
                }

                return null
            }

            return findScalaClassRecursively(psiFile)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Checks if an element is a Scala class/object/trait
     */
    private fun isScalaClass(element: PsiElement?): Boolean {
        if (element == null) return false
        val className = element.javaClass.name

        // Check for both API interfaces and implementation classes
        return className == "org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass" ||
                className == "org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject" ||
                className == "org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait" ||
                className == "org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl" ||
                className == "org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl" ||
                className == "org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTraitImpl"
    }

    fun copyCompiledClass() {
        // Move everything to background thread to avoid EDT violations
        ApplicationManager.getApplication().executeOnPooledThread {
            var className = "Unknown"
            try {
                // First, collect PSI information in read action (no file system operations)
                val psiInfo = ReadAction.compute<PsiInfo, Exception> {
                    if (isScalaClass) {
                        getScalaPsiInfo(currentScalaElement!!)
                    } else {
                        getJavaPsiInfo(currentTestClass!!)
                    }
                }

                className = psiInfo.className

                // Now perform file system operations outside of PSI access
                val compiledFilePath = buildCompiledClassPath(psiInfo)

                if (compiledFilePath == null) {
                    // Show error notification if compiled class not found
                    ApplicationManager.getApplication().invokeLater {
                        showNotification(
                            project,
                            "Copy Failed",
                            "Compiled class file not found for '$className'. Make sure the project is compiled and check both target/classes and target/test-classes directories.",
                            NotificationType.ERROR
                        )
                    }
                    return@executeOnPooledThread
                }

                // Get the settings
                val settings = com.intellij.openapi.application.ApplicationManager.getApplication()
                    .getService(com.cambra.emtestrunner.settings.ModuleTestRunnerSettings::class.java)

                // Replace placeholders in the copy command
                val packagePath = psiInfo.packageName.replace('.', '/')
                val copyCmd = settings.copyCommand
                    .replace("{COMPILED_CLASS_PATH}", compiledFilePath)
                    .replace("{PACKAGE_PATH}", packagePath)
                    .replace("{NAMESPACE}", settings.namespace)

                // Show debug notification with placeholder values
                ApplicationManager.getApplication().invokeLater {
                    showNotification(
                        project,
                        "Debug: Copy Command Placeholders",
                        "Package: ${psiInfo.packageName}\nPACKAGE_PATH: $packagePath\nCOMPILED_CLASS_PATH: $compiledFilePath\nClass: $className",
                        NotificationType.INFORMATION
                    )
                }

                // Show debug notification with final copy command
                ApplicationManager.getApplication().invokeLater {
                    showNotification(
                        project,
                        "Debug: Final Copy Command",
                        "Command after replacements:\n$copyCmd",
                        NotificationType.INFORMATION
                    )
                }

                // Run the command in background
                runCommandInBackground(project, copyCmd, compiledFilePath, className)
            } catch (e: Exception) {
                // Show error on EDT
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Error copying compiled class '$className': ${e.message}",
                        "Copy Error"
                    )

                    // Show error notification
                    showNotification(
                        project,
                        "Copy Failed",
                        "Failed to copy compiled class '$className': ${e.message}",
                        NotificationType.ERROR
                    )
                }
            }
        }
    }

    // Data class to hold PSI information without file system dependencies
    private data class PsiInfo(
        val packageName: String,
        val className: String,
        val isScala: Boolean,
        val projectBasePath: String?
    )

    // Extract PSI information for Java classes
    private fun getJavaPsiInfo(psiClass: PsiClass): PsiInfo {
        val containingFile = psiClass.containingFile
        val packageName = when {
            containingFile is PsiJavaFile -> containingFile.packageName
            else -> try {
                val packageNameMethod = containingFile.javaClass.getMethod("packageName")
                packageNameMethod.invoke(containingFile) as? String ?: ""
            } catch (e: Exception) {
                val qualifiedName = psiClass.qualifiedName ?: ""
                qualifiedName.substringBeforeLast(".", "")
            }
        }

        return PsiInfo(
            packageName = packageName,
            className = psiClass.name ?: "UnknownClass",
            isScala = containingFile.javaClass.name.contains("scala", ignoreCase = true),
            projectBasePath = psiClass.project.basePath
        )
    }

    // Extract PSI information for Scala classes
    private fun getScalaPsiInfo(element: PsiElement): PsiInfo {
        val qualifiedName = getScalaQualifiedName(element) ?: "Unknown"
        val packageName = qualifiedName.substringBeforeLast('.', "")
        val className = getElementName(element) ?: "UnknownClass"

        return PsiInfo(
            packageName = packageName,
            className = className,
            isScala = true,
            projectBasePath = element.project.basePath
        )
    }

    // Build compiled class path using PSI info and file system operations
    private fun buildCompiledClassPath(psiInfo: PsiInfo): String? {
        var packagePath = psiInfo.packageName.replace('.', java.io.File.separatorChar)
        if(packagePath.endsWith(java.io.File.separatorChar)) {
            packagePath = packagePath.substring(0, packagePath.length - 1)
        }

        // Check both test-classes and classes directories
        val settings = ModuleTestRunnerSettings.getInstance()
        val possibleDirs = listOf(
            findBuildOutputDir(psiInfo.projectBasePath, "target/test-classes", settings.testModuleName),
            findBuildOutputDir(psiInfo.projectBasePath, "target/classes", settings.testModuleName)
        ).filterNotNull()



        if (possibleDirs.isEmpty()) {
            return null // Will trigger error notification
        }

        for (outputDir in possibleDirs) {
            // For Scala classes, check both normal and $ versions
            if (psiInfo.isScala) {
                val classPath = "$outputDir${java.io.File.separator}$packagePath${java.io.File.separator}${psiInfo.className}.class"
                val objectPath = "$outputDir${java.io.File.separator}$packagePath${java.io.File.separator}${psiInfo.className}$$.class"



                if (java.io.File(objectPath).exists()) return objectPath
                if (java.io.File(classPath).exists()) return classPath
            } else {
                // For Java classes
                val classPath = "$outputDir${java.io.File.separator}$packagePath${java.io.File.separator}${psiInfo.className}.class"



                if (java.io.File(classPath).exists()) return classPath
            }
        }



        return null // No compiled class found
    }

    private fun getCompiledPathForClass(psiClass: PsiClass): String {
        val project = psiClass.project
        val containingFile = psiClass.containingFile

        // Handle both Java and Scala files
        val virtualFile = containingFile.virtualFile ?: return "No virtual file"
        val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return "No module found"

        // For Maven, we only want to check the target/test-classes directory
        val settings = ModuleTestRunnerSettings.getInstance()
        val mavenTestOutputDir = findBuildOutputDir(project.basePath, "target/test-classes", settings.testModuleName)

        // If Maven test directory doesn't exist, return a message
        if (mavenTestOutputDir == null) {
            return "Maven test-classes directory not found. Make sure this is a Maven project with compiled tests."
        }

        // Get the package name - handle both Java and Scala files
        val packageName = when {
            containingFile is PsiJavaFile -> containingFile.packageName
            // For Scala files, try to get package name using reflection
            else -> try {
                val packageNameMethod = containingFile.javaClass.getMethod("packageName")
                packageNameMethod.invoke(containingFile) as? String ?: ""
            } catch (e: Exception) {
                // If reflection fails, try to extract from qualified name
                val qualifiedName = psiClass.qualifiedName ?: ""
                qualifiedName.substringBeforeLast(".", "")
            }
        }

        val packagePath = packageName.replace('.', File.separatorChar)
        val className = psiClass.name ?: "UnknownClass"

        // For Scala classes, check both normal and $ versions
        if (containingFile.javaClass.name.contains("scala", ignoreCase = true)) {
            val classPath = "$mavenTestOutputDir${File.separator}$packagePath${File.separator}$className.class"
            val objectPath = "$mavenTestOutputDir${File.separator}$packagePath${File.separator}$className$$.class"

            // Return the path that exists, or the class path as default
            // Note: File existence check moved to background thread to avoid EDT violations
            return if (java.io.File(objectPath).exists()) objectPath else classPath
        }

        // For Java classes, just return the normal path
        return "$mavenTestOutputDir${File.separator}$packagePath${File.separator}$className.class"
    }

    private fun getCompiledPathForScalaClass(element: PsiElement): String {
        try {
            val project = element.project
            val containingFile = element.containingFile
            val virtualFile = containingFile?.virtualFile ?: return "No virtual file"
            val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return "No module found"

            // For Maven, we only want to check the target/test-classes directory for test classes
            val settings = ModuleTestRunnerSettings.getInstance()
            val mavenTestOutputDir = findBuildOutputDir(project.basePath, "target/test-classes", settings.testModuleName)

            // If Maven test directory doesn't exist, return a message
            if (mavenTestOutputDir == null) {
                return "Maven test-classes directory not found. Make sure this is a Maven project with compiled tests."
            }

            // Try to get qualified name using reflection
            val qualifiedName = getScalaQualifiedName(element) ?: return "Unknown Scala class"
            val packageName = qualifiedName.substringBeforeLast('.', "")
            val className = getElementName(element) ?: "UnknownClass"

            // Build the path to the compiled .class file
            val packagePath = packageName.replace('.', File.separatorChar)

            // Scala classes often have $ in their compiled names
            // For objects: Object$.class
            // For classes with companion objects: Class$.class and Class.class
            // For case classes: CaseClass$.class and CaseClass.class

            // Try both possibilities
            val classPath = "$mavenTestOutputDir${File.separator}$packagePath${File.separator}$className.class"
            val objectPath = "$mavenTestOutputDir${File.separator}$packagePath${File.separator}$className$$.class"

            // Return the path that exists, or the class path as default
            // Note: File existence check moved to background thread to avoid EDT violations
            return if (java.io.File(objectPath).exists()) objectPath else classPath
        } catch (e: Exception) {
            return "Error getting Scala class path: ${e.message}"
        }
    }

    // Helper method to find build output directory for Maven modules
    private fun findBuildOutputDir(basePath: String?, relativePath: String, testModuleName: String = ""): String? {
        if (basePath == null) return null

        // If testModuleName is specified, try that specific module first
        if (testModuleName.isNotEmpty()) {
            val moduleSpecificPath = Paths.get(basePath, testModuleName, relativePath).toString()
            if (java.io.File(moduleSpecificPath).exists()) return moduleSpecificPath
        }

        // First, try the direct path (for single module projects)
        val directPath = Paths.get(basePath, relativePath).toString()
        if (java.io.File(directPath).exists()) return directPath

        // If direct path doesn't exist, try common Maven multi-module patterns
        val baseDir = java.io.File(basePath)

        // Look for subdirectories that might contain the target directory
        baseDir.listFiles()?.forEach { subDir ->
            if (subDir.isDirectory) {
                val modulePath = Paths.get(subDir.absolutePath, relativePath).toString()
                if (java.io.File(modulePath).exists()) {
                    return modulePath
                }
            }
        }

        return null
    }

    // Helper method to run a command in background
    private fun runCommandInBackground(project: Project, command: String, compiledFilePath: String, className: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Execute the command using ProcessBuilder
                val processBuilder = java.lang.ProcessBuilder("sh", "-c", command)
                    .directory(java.io.File(project.basePath ?: "."))

                val processHandler = CapturingProcessHandler(processBuilder.start(), null, command)
                val result = processHandler.runProcess(30000) // 30 second timeout

                ApplicationManager.getApplication().invokeLater {
                    if (result.exitCode == 0) {
                        // Success - show notification
                        showNotification(
                            project,
                            "Class Copied",
                            "Successfully copied compiled class '$className' to execution host:\n${
                                getClassNameFromPath(
                                    compiledFilePath
                                )
                            }",
                            NotificationType.INFORMATION
                        )
                    } else {
                        // Failure - show error notification and add to Problems Tool Window
                        val errorMessage = "Failed to copy compiled class '$className'. Exit code: ${result.exitCode}"
                        val errorDetails = if (result.stderr.isNotEmpty()) result.stderr else result.stdout

                        showNotification(
                            project,
                            "Copy Failed",
                            errorMessage + "\n${
                                errorDetails
                            }",
                            NotificationType.ERROR
                        )
                    }
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    val errorMessage = "Error executing copy command for class '$className': ${ex.message}"
                    showNotification(
                        project,
                        "Copy Failed",
                        errorMessage + "\n${ex.stackTraceToString()}",
                        NotificationType.ERROR
                    )
                }
            }
        }
    }

    // Reflection-based helpers for Scala support
    private fun getElementName(element: PsiElement?): String? {
        return try {
            val nameMethod = element?.javaClass?.getMethod("name")
            nameMethod?.invoke(element) as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun getScalaQualifiedName(element: PsiElement?): String? {
        return try {
            val qualifiedNameMethod = element?.javaClass?.getMethod("qualifiedName")
            qualifiedNameMethod?.invoke(element) as? String
        } catch (e: Exception) {
            try {
                // Alternative approach for some Scala elements
                val nameMethod = element?.javaClass?.getMethod("name")
                val name = nameMethod?.invoke(element) as? String

                val containingFile = element?.containingFile
                val packageName = getScalaFilePackageName(containingFile)

                if (packageName.isNotEmpty() && name != null) {
                    "$packageName.$name"
                } else {
                    name
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getScalaFilePackageName(file: PsiElement?): String {
        return try {
            // Try to get package name from Scala file
            val packageNameMethod = file?.javaClass?.getMethod("packageName")
            packageNameMethod?.invoke(file) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

// Helper method to show notifications
private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("PluginDebug")
        .createNotification(title, content, type)
        .notify(project)
}

// Helper method to extract class name from path
private fun getClassNameFromPath(path: String): String {
    val fileName = path.substringAfterLast(File.separator)
    return fileName.substringBeforeLast(".class")
}
