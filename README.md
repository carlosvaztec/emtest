# Module Test Runner Plugin

*Experimenting with AI Agent, created in 18h using Augment agent.*

A configurable IntelliJ IDEA plugin that allows you to run custom commands for Java and Scala test methods and classes. 
Perfect for Maven, Gradle, SBT, or custom test execution workflows.

## Features

### Core Functionality
- **Right-click test execution**: Run custom commands on test methods and classes via context menu
- **Dual language support**: Works with both Java and Scala test frameworks
- **Configurable commands**: Fully customizable command templates with placeholder support
- **Package prefix removal**: Clean up command names by removing specified package prefixes
- **Terminal integration**: Commands executed in IntelliJ's integrated terminal
- **Automatic test class tracking**: Tracks the current class being edited for auto-copy features

### Supported Test Frameworks
- **Java**: JUnit 4, JUnit 5, TestNG
- **Scala**: ScalaTest (FunSuite, FlatSpec, WordSpec, FeatureSpec), JUnit

### Command Types
- **Build & Run Commands**: Full build and test execution (slower but comprehensive)
- **Fast Run Commands**: Quick execution without full build (faster for iterative testing)
- **Auto-Copy**: Automatically copy compiled test classes after successful compilation

## Installation

### From Source
1. Clone this repository
2. Build the plugin using Gradle:
   ```bash
   ./gradlew buildPlugin
   ```
3. Install the generated plugin file from `build/distributions/` in IntelliJ IDEA

### Development Setup
1. Clone the repository
2. Open in IntelliJ IDEA
3. Run the plugin in a development instance:
   ```bash
   ./gradlew runIde
   ```

## Configuration

### Settings Location
Go to **Settings → Tools → Module Test Runner** to configure the plugin.

### Available Settings

#### Command Templates
- **Build Run Method Command**: Command executed for individual test methods (with build)
- **Build Run Class Command**: Command executed for test classes (with build)
- **Run Method Command**: Fast command for individual test methods (without build)
- **Run Class Command**: Fast command for test classes (without build)
- **Copy Command**: Command to copy compiled classes to execution host

#### Configuration Options
- **Remove Package Prefix**: Remove specified content from class/method names
- **Namespace**: Custom namespace value for command placeholders
- **Enable Auto Copy**: Automatically copy compiled classes after successful compilation

#### Placeholder Variables
Use these placeholders in your command templates:
- `{METHOD_NAME}`: Full method name (format: `package.className#methodName`)
- `{CLASS_NAME}`: Full class name (format: `package.className`)
- `{NAMESPACE}`: Custom namespace value from settings
- `{COMPILED_CLASS_PATH}`: Path to compiled class file (for copy command)
- `{PACKAGE_PATH}`: Package name with dots replaced by slashes (e.g., `a/b/c/d` for package `a.b.c.d`)


## Usage

### Running Tests

#### Context Menu Options
1. **Right-click on a test method**: Shows "Run module test 'methodName'"
2. **Right-click on a test class**: Shows "Run module test 'className'"  
3. **Right-click on a file with tests**: Shows "Run fileName module tests"

#### Available Actions
- **Run module test**: Execute build & run command (Ctrl+Shift+F10)
- **Run module test (fast)**: Execute fast run command (Ctrl+Alt+Shift+F10)

#### Auto-Copy Feature
When enabled, the plugin automatically:
1. Tracks the currently edited test class
2. Monitors compilation status
3. Copies compiled class files after successful compilation
4. Shows notifications for success/failure

### Keyboard Shortcuts
- **Ctrl+Shift+F10**: Run module test (build & run)
- **Ctrl+Alt+Shift+F10**: Run module test (fast)

## Building and Testing

### Build the Plugin
```bash
./gradlew buildPlugin
```

### Run in Development Mode
```bash
./gradlew runIde
```

### Run Tests
```bash
./gradlew test
```

### Verify Plugin Compatibility
```bash
./gradlew verifyPlugin
```

### Build Distribution
```bash
./gradlew buildPlugin
```
The plugin ZIP file will be created in `build/distributions/`.

## Development

### Project Structure
```
src/main/kotlin/com/cambra/emtestrunner/
├── RunModuleTestAction.kt          # Main action for running tests
├── FastRunModuleTestAction.kt      # Fast run action
├── AutoCopyCompiledTestService.kt  # Auto-copy and tracking service
├── PluginStartupActivity.kt        # Plugin initialization
├── settings/
│   ├── ModuleTestRunnerSettings.kt # Settings persistence
│   ├── ModuleTestRunnerConfigurable.kt # Settings UI
│   └── TestRunnerSettingsComponent.kt # Settings form
└── icons/
    └── PluginIcons.kt             # Plugin icons
```

### Key Components
- **TestClassTracker**: Service that tracks current test class and handles auto-copy
- **ModuleTestRunnerSettings**: Persistent settings storage
- **RunModuleTestAction**: Main action for executing test commands
- **PluginStartupActivity**: Initializes services when project opens

### Requirements
- **IntelliJ IDEA**: 2023.1+ (build 231+)
- **Java**: JDK 21+
- **Kotlin**: 2.1.0+
- **Dependencies**: Java plugin, Terminal plugin, Scala plugin (optional)

## Troubleshooting

### Common Issues

#### Auto-Copy Not Working
- Ensure "Enable Auto Copy" is checked in settings
- Verify the TestClassTracker service is initialized (check notifications)
- Make sure you're editing a test class file

#### Commands Not Executing
- Check terminal output for error messages
- Verify command templates are correctly configured
- Ensure placeholders are properly formatted

#### Menu Items Not Appearing
- Verify you're right-clicking on test methods/classes
- Check that the file contains recognized test annotations
- Ensure the plugin is enabled in Settings → Plugins

### Debug Information
The plugin shows notifications for:
- Service initialization status
- Auto-copy success/failure
- Command execution results

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with `./gradlew runIde`
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
