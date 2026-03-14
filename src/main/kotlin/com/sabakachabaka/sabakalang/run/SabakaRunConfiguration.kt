package com.sabakachabaka.sabakalang.run

import com.intellij.execution.*
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import com.sabakachabaka.sabakalang.SabakaFileType
import com.sabakachabaka.sabakalang.psi.SabakaFile
import com.sabakachabaka.sabakalang.settings.SabakaSettings
import org.jdom.Element
import javax.swing.JComponent
import javax.swing.JPanel

// ── Configuration type ────────────────────────────────────────────────────────

class SabakaRunConfigurationType : ConfigurationType {
    override fun getDisplayName()    = "SabakaLang"
    override fun getConfigurationTypeDescription() = "Run a .sabaka script"
    override fun getIcon()           = SabakaFileType.INSTANCE.icon
    override fun getId()             = "SABAKA_RUN_CONFIGURATION"
    override fun getConfigurationFactories() = arrayOf(SabakaConfigurationFactory(this))
}

class SabakaConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId() = "SabakaLang"
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        SabakaRunConfiguration(project, this, "SabakaLang")
}

// ── Run configuration ─────────────────────────────────────────────────────────
//
// Fields are stored directly to avoid RunConfigurationOptions ClassCastException.
// The platform creates a template config before the plugin classloader is fully
// wired — using RunConfigurationBase<RunConfigurationOptions> (not a custom subclass)
// prevents the cast failure entirely.

class SabakaRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var scriptPath: String       = ""
    var programArguments: String = ""

    // interpreterPath falls back to global settings when blank
    var interpreterPath: String  = ""
        get() = field.ifBlank { SabakaSettings.getInstance().interpreterPath }

    // workingDirectory falls back to script dir, then global settings default
    var workingDirectory: String = ""
        get() = field.ifBlank {
            SabakaSettings.getInstance().defaultWorkingDir.ifBlank {
                project.basePath ?: ""
            }
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        SabakaRunSettingsEditor(project)

    override fun checkConfiguration() {
        if (scriptPath.isBlank()) throw RuntimeConfigurationError("Script path is not set")
        if (interpreterPath.isBlank()) throw RuntimeConfigurationError(
            "SabakaLang interpreter path is not set. Configure it in Settings → Tools → SabakaLang"
        )
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        SabakaRunState(environment, this)

    override fun readExternal(element: Element) {
        super.readExternal(element)
        scriptPath       = element.getAttributeValue("scriptPath")       ?: ""
        interpreterPath  = element.getAttributeValue("interpreterPath")  ?: ""
        programArguments = element.getAttributeValue("programArguments") ?: ""
        workingDirectory = element.getAttributeValue("workingDirectory") ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("scriptPath",       scriptPath)
        element.setAttribute("interpreterPath",  interpreterPath)
        element.setAttribute("programArguments", programArguments)
        element.setAttribute("workingDirectory", workingDirectory)
    }
}

// ── Run state ─────────────────────────────────────────────────────────────────

class SabakaRunState(
    private val env: ExecutionEnvironment,
    private val cfg: SabakaRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val cmd = GeneralCommandLine()
        cmd.exePath = cfg.interpreterPath
        cmd.addParameter(cfg.scriptPath)
        if (cfg.programArguments.isNotBlank())
            cfg.programArguments.split(" ").filter { it.isNotBlank() }.forEach { cmd.addParameter(it) }

        // Working dir: prefer explicit config → script's own dir → global default
        val wd = cfg.workingDirectory.ifBlank {
            cfg.scriptPath.substringBeforeLast('/').substringBeforeLast('\\')
        }
        cmd.setWorkDirectory(wd.ifBlank { cfg.project.basePath })

        val handler: ProcessHandler = ColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler)

        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(cfg.project)
            .apply { addFilter(SabakaConsoleFilter(cfg.project)) }
            .console
        console.attachToProcess(handler)

        return DefaultExecutionResult(console, handler)
    }
}

// ── Console filter — makes "line 42" in errors clickable ─────────────────────

class SabakaConsoleFilter(private val project: Project) : Filter {
    private val pattern = Regex("""(?:line\s+|:)(\d+)""")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = pattern.find(line) ?: return null
        val lineNum = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
        val start = entireLength - line.length + match.range.first
        val end   = entireLength - line.length + match.range.last + 1
        val vFile = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return null
        return Filter.Result(start, end, OpenFileHyperlinkInfo(project, vFile, lineNum))
    }
}

// ── Settings editor ───────────────────────────────────────────────────────────

class SabakaRunSettingsEditor(project: Project) : SettingsEditor<SabakaRunConfiguration>() {

    private val scriptField      = TextFieldWithBrowseButton()
    private val interpreterField = TextFieldWithBrowseButton()
    private val argsField        = ExpandableTextField()
    private val workDirField     = TextFieldWithBrowseButton()

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Script path:",                    scriptField)
        .addLabeledComponent("Interpreter path (optional):",   interpreterField)
        .addTooltip("Leave blank to use the path from Settings → Tools → SabakaLang")
        .addLabeledComponent("Program arguments:",             argsField)
        .addLabeledComponent("Working directory (optional):",  workDirField)
        .addTooltip("Leave blank to use the script's directory")
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetEditorFrom(cfg: SabakaRunConfiguration) {
        scriptField.text      = cfg.scriptPath
        interpreterField.text = cfg.interpreterPath
        argsField.text        = cfg.programArguments
        workDirField.text     = cfg.workingDirectory
    }

    override fun applyEditorTo(cfg: SabakaRunConfiguration) {
        cfg.scriptPath       = scriptField.text.trim()
        cfg.interpreterPath  = interpreterField.text.trim()
        cfg.programArguments = argsField.text.trim()
        cfg.workingDirectory = workDirField.text.trim()
    }

    override fun createEditor(): JComponent = panel
}

// ── Run configuration producer — auto-creates config from any .sabaka file ───

class SabakaRunConfigurationProducer :
    RunConfigurationProducer<SabakaRunConfiguration>(
        ConfigurationTypeUtil.findConfigurationType(SabakaRunConfigurationType::class.java)
            .configurationFactories[0]
    ) {

    override fun setupConfigurationFromContext(
        cfg: SabakaRunConfiguration,
        ctx: ConfigurationContext,
        source: com.intellij.openapi.util.Ref<PsiElement>
    ): Boolean {
        val file = ctx.location?.psiElement?.containingFile as? SabakaFile ?: return false
        val vFile = file.virtualFile ?: return false
        cfg.scriptPath       = vFile.path
        cfg.name             = vFile.nameWithoutExtension
        cfg.workingDirectory = vFile.parent?.path ?: ""
        // interpreter comes from global settings by default (field left blank)
        return true
    }

    override fun isConfigurationFromContext(
        cfg: SabakaRunConfiguration,
        ctx: ConfigurationContext
    ): Boolean {
        val file = ctx.location?.psiElement?.containingFile as? SabakaFile ?: return false
        return cfg.scriptPath == (file.virtualFile?.path ?: "")
    }
}
