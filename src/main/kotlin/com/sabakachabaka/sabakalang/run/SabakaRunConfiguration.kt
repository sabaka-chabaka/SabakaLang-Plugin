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
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import com.sabakachabaka.sabakalang.SabakaFileType
import com.sabakachabaka.sabakalang.psi.SabakaFile
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

class SabakaRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<SabakaRunConfigurationOptions>(project, factory, name) {

    override fun getOptions() = super.getOptions() as SabakaRunConfigurationOptions

    var scriptPath: String
        get()      = options.scriptPath ?: ""
        set(value) { options.scriptPath = value }

    var interpreterPath: String
        get()      = options.interpreterPath ?: "sabaka"
        set(value) { options.interpreterPath = value }

    var programArguments: String
        get()      = options.programArguments ?: ""
        set(value) { options.programArguments = value }

    var workingDirectory: String
        get()      = options.workingDirectory ?: project.basePath ?: ""
        set(value) { options.workingDirectory = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        SabakaRunSettingsEditor(project)

    override fun checkConfiguration() {
        if (scriptPath.isBlank()) throw RuntimeConfigurationError("Script path is not set")
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        SabakaRunState(environment, this)

    override fun readExternal(element: Element) {
        super.readExternal(element)
        scriptPath       = element.getAttributeValue("scriptPath") ?: ""
        interpreterPath  = element.getAttributeValue("interpreterPath") ?: "sabaka"
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

class SabakaRunConfigurationOptions : RunConfigurationOptions() {
    var scriptPath: String?       = null
    var interpreterPath: String?  = "sabaka"
    var programArguments: String? = null
    var workingDirectory: String? = null
}

// ── Run state ─────────────────────────────────────────────────────────────────

class SabakaRunState(
    private val env: ExecutionEnvironment,
    private val cfg: SabakaRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val cmd = GeneralCommandLine()
        cmd.exePath = cfg.interpreterPath.ifBlank { "sabaka" }
        cmd.addParameter(cfg.scriptPath)
        if (cfg.programArguments.isNotBlank())
            cfg.programArguments.split(" ").filter { it.isNotBlank() }.forEach { cmd.addParameter(it) }
        cmd.setWorkDirectory(cfg.workingDirectory.ifBlank { cfg.project.basePath })

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

// ── Console error filter — makes "line 42" clickable ─────────────────────────

class SabakaConsoleFilter(private val project: Project) : Filter {
    private val pattern = Regex("""(?:line\s+|:)(\d+)""")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = pattern.find(line) ?: return null
        val lineNum = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
        val start = entireLength - line.length + match.range.first
        val end   = entireLength - line.length + match.range.last + 1

        // Try to find the active sabaka file
        val vFile = project.basePath?.let {
            LocalFileSystem.getInstance().findFileByPath(it)
        } ?: return null

        val info = OpenFileHyperlinkInfo(project, vFile, lineNum)
        return Filter.Result(start, end, info)
    }
}

// ── Settings editor ───────────────────────────────────────────────────────────

class SabakaRunSettingsEditor(project: Project) : SettingsEditor<SabakaRunConfiguration>() {

    private val scriptField      = TextFieldWithBrowseButton()
    private val interpreterField = TextFieldWithBrowseButton()
    private val argsField        = ExpandableTextField()
    private val workDirField     = TextFieldWithBrowseButton()

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Script path:",       scriptField)
        .addLabeledComponent("Interpreter path:",  interpreterField)
        .addLabeledComponent("Program arguments:", argsField)
        .addLabeledComponent("Working directory:", workDirField)
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

// ── Run configuration producer (auto-detect from .sabaka file) ────────────────

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
        cfg.scriptPath = vFile.path
        cfg.name = vFile.nameWithoutExtension
        cfg.workingDirectory = vFile.parent?.path ?: ""
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
