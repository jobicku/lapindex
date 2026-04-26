package ru.jobick.lapindex.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import ru.jobick.lapindex.index.LapindexJsonIndex
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class LapindexSettingsConfigurable(private val project: Project) : Configurable {

    private var pathsModel = DefaultListModel<String>()
    private var list = JBList(pathsModel)

    override fun getDisplayName(): String = "Lapindex"

    override fun createComponent(): JComponent {
        pathsModel = DefaultListModel()
        list = JBList(pathsModel)
        LapindexSettings.getInstance(project).jsonFilePaths.forEach { pathsModel.addElement(it) }

        val toolbar = ToolbarDecorator.createDecorator(list)
            .setAddAction {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
                val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return@setAddAction
                val basePath = project.basePath
                val path = if (basePath != null && chosen.path.startsWith(basePath)) {
                    chosen.path.removePrefix("$basePath/")
                } else {
                    chosen.path
                }
                pathsModel.addElement(path)
            }
            .setRemoveAction {
                list.selectedIndices.reversed().forEach { pathsModel.remove(it) }
            }
            .createPanel()

        return JPanel(BorderLayout()).apply {
            add(JLabel("JSON file paths:"), BorderLayout.NORTH)
            add(toolbar, BorderLayout.CENTER)
        }
    }

    override fun isModified(): Boolean {
        val current = LapindexSettings.getInstance(project).jsonFilePaths
        val ui = (0 until pathsModel.size).map { pathsModel.getElementAt(it) }
        return current != ui
    }

    override fun apply() {
        val ui = (0 until pathsModel.size).map { pathsModel.getElementAt(it) }
        LapindexSettings.getInstance(project).jsonFilePaths = ui.toMutableList()
        LapindexJsonIndex.getInstance(project).refresh()
    }

    override fun reset() {
        pathsModel.clear()
        LapindexSettings.getInstance(project).jsonFilePaths.forEach { pathsModel.addElement(it) }
    }
}
