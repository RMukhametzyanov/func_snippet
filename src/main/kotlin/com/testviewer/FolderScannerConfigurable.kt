package com.testviewer

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.*

class FolderScannerConfigurable : Configurable {
    private var panel: JPanel? = null
    private val folderListModel = DefaultListModel<String>()
    private val folderList = JList(folderListModel)

    override fun getDisplayName(): String = "func snippet"

    override fun createComponent(): JComponent? {
        val infoLabel = JLabel("<html><p>Укажите пути к папкам, которые нужно просканировать.<br>" +
                "Плагин отобразит дерево всех файлов и папок из всех указанных директорий в одном дереве.</p></html>")
        infoLabel.foreground = java.awt.Color.GRAY
        
        // Настройка списка папок
        folderList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        folderList.visibleRowCount = 8
        val scrollPane = JBScrollPane(folderList)
        scrollPane.preferredSize = java.awt.Dimension(500, 150)
        
        // Панель с кнопками
        val buttonsPanel = createButtonsPanel()
        
        panel = FormBuilder.createFormBuilder()
            .addComponent(infoLabel, 1)
            .addLabeledComponent(JBLabel("Папки для сканирования:"), scrollPane, 1, false)
            .addComponent(buttonsPanel, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        loadSettings()
        return panel
    }

    private fun createButtonsPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        
        val addButton = JButton("Добавить...")
        addButton.addActionListener {
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
                .withTitle("Выберите папку для сканирования")
                .withDescription("Выберите папку, содержимое которой нужно отобразить в дереве")
            
            val project = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
            val files = FileChooser.chooseFiles(descriptor, project, null)
            
            if (files.isNotEmpty()) {
                val selectedPath = files[0].path
                // Проверяем, что папка еще не добавлена
                if (!folderListModel.contains(selectedPath)) {
                    folderListModel.addElement(selectedPath)
                } else {
                    JOptionPane.showMessageDialog(
                        panel,
                        "Эта папка уже добавлена",
                        "Информация",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }
        
        val removeButton = JButton("Удалить")
        removeButton.addActionListener {
            val selectedIndex = folderList.selectedIndex
            if (selectedIndex >= 0) {
                folderListModel.remove(selectedIndex)
            }
        }
        
        val clearButton = JButton("Очистить все")
        clearButton.addActionListener {
            folderListModel.clear()
        }
        
        panel.add(addButton)
        panel.add(removeButton)
        panel.add(clearButton)
        
        return panel
    }

    override fun isModified(): Boolean {
        val settings = FolderScannerSettings.getInstance()
        val currentPaths = settings.state.folderPaths.toList()
        val uiPaths = (0 until folderListModel.size).map { folderListModel.getElementAt(it) }
        
        return currentPaths != uiPaths
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val settings = FolderScannerSettings.getInstance()
        val newPaths = mutableListOf<String>()
        
        // Собираем все пути из списка
        for (i in 0 until folderListModel.size) {
            val path = folderListModel.getElementAt(i).trim()
            if (path.isNotEmpty()) {
                val folder = File(path)
                if (!folder.exists() || !folder.isDirectory) {
                    throw ConfigurationException("Папка не найдена: $path")
                }
                newPaths.add(path)
            }
        }
        
        settings.state.folderPaths = newPaths
    }

    override fun reset() {
        loadSettings()
    }

    private fun loadSettings() {
        val settings = FolderScannerSettings.getInstance()
        folderListModel.clear()
        settings.state.folderPaths.forEach { path ->
            folderListModel.addElement(path)
        }
    }
}

