package com.testviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class FolderTreePanel(private val project: Project) {
    private val mainPanel = JPanel(BorderLayout())
    private val tree = Tree()
    private val scrollPane = JBScrollPane(tree)
    private val fileStructurePanel = FileStructurePanel(project)
    private val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
    private var searchText: String = ""
    private val searchTextField = JTextField()
    
    init {
        setupPanel()
        refreshTree()
    }
    
    private fun setupPanel() {
        // Настройка дерева
        tree.model = DefaultTreeModel(DefaultMutableTreeNode("Выберите папки в настройках"))
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.cellRenderer = FileTreeCellRenderer()
        
        // Обработчик выбора файла для отображения структуры
        tree.selectionModel.addTreeSelectionListener { e ->
            val path = e.newLeadSelectionPath ?: return@addTreeSelectionListener
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val userObject = node.userObject
            
            if (userObject is FileNode) {
                val file = userObject.file
                if (file.exists() && file.isFile) {
                    // Передаем текущий текст поиска перед показом структуры
                    fileStructurePanel.setSearchText(searchText)
                    fileStructurePanel.showFileStructure(file)
                } else {
                    fileStructurePanel.showEmptyState()
                }
            }
        }
        
        // Обработчик двойного клика для открытия файлов
        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val userObject = node.userObject
                    
                    if (userObject is FileNode) {
                        val file = userObject.file
                        if (file.exists() && file.isFile) {
                            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
                            virtualFile?.let {
                                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(it, true)
                            }
                        }
                    }
                }
            }
        })
        
        scrollPane.setViewportView(tree)
        scrollPane.border = EmptyBorder(5, 5, 5, 5)
        
        // Настройка split pane
        splitPane.leftComponent = scrollPane
        splitPane.rightComponent = fileStructurePanel.component()
        splitPane.dividerLocation = 300
        splitPane.isOneTouchExpandable = true
        splitPane.isContinuousLayout = true
        
        // Панель с поиском и кнопками
        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.border = EmptyBorder(5, 5, 5, 5)
        
        // Панель поиска
        val searchPanel = JPanel(BorderLayout(5, 0))
        val searchLabel = JLabel("Поиск:")
        searchTextField.toolTipText = "Введите текст для поиска в файлах"
        searchTextField.columns = 20
        
        // Обработчик изменений текста поиска
        searchTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                onSearchTextChanged()
            }
            
            override fun removeUpdate(e: DocumentEvent) {
                onSearchTextChanged()
            }
            
            override fun changedUpdate(e: DocumentEvent) {
                onSearchTextChanged()
            }
        })
        
        searchPanel.add(searchLabel, BorderLayout.WEST)
        searchPanel.add(searchTextField, BorderLayout.CENTER)
        
        // Панель с кнопками
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        
        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = "Обновить дерево"
        refreshButton.border = EmptyBorder(2, 2, 2, 2)
        refreshButton.isContentAreaFilled = false
        refreshButton.addActionListener {
            refreshTree()
        }
        
        val settingsButton = JButton(AllIcons.General.Settings)
        settingsButton.toolTipText = "Открыть настройки плагина"
        settingsButton.border = EmptyBorder(2, 2, 2, 2)
        settingsButton.isContentAreaFilled = false
        settingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                FolderScannerConfigurable::class.java,
                null
            )
            // Обновляем дерево после закрытия настроек
            refreshTree()
        }
        
        buttonsPanel.add(refreshButton)
        buttonsPanel.add(settingsButton)
        
        toolbarPanel.add(searchPanel, BorderLayout.CENTER)
        toolbarPanel.add(buttonsPanel, BorderLayout.EAST)
        
        mainPanel.add(toolbarPanel, BorderLayout.NORTH)
        mainPanel.add(splitPane, BorderLayout.CENTER)
    }
    
    fun refreshTree() {
        val settings = FolderScannerSettings.getInstance()
        val folderPaths = settings.state.folderPaths
        
        if (folderPaths.isEmpty()) {
            val rootNode = DefaultMutableTreeNode("Выберите папки в настройках")
            tree.model = DefaultTreeModel(rootNode)
            return
        }
        
        // Создаем корневой узел для объединенного дерева
        val rootNode = DefaultMutableTreeNode("Папки")
        
        // Обрабатываем каждую папку
        for (folderPath in folderPaths) {
            if (folderPath.isBlank()) continue
            
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                val errorNode = DefaultMutableTreeNode("Папка не найдена: $folderPath")
                rootNode.add(errorNode)
                continue
            }
            
            // Строим дерево для каждой папки с учетом поиска
            val folderNode = if (searchText.isNotBlank()) {
                buildFilteredTree(folder, folder) ?: continue
            } else {
                buildTree(folder)
            }
            
            rootNode.add(folderNode)
        }
        
        if (rootNode.childCount == 0) {
            val emptyNode = if (searchText.isNotBlank()) {
                DefaultMutableTreeNode("Совпадений не найдено")
            } else {
                DefaultMutableTreeNode("Нет доступных папок")
            }
            rootNode.add(emptyNode)
        }
        
        tree.model = DefaultTreeModel(rootNode)
        
        // Разворачиваем корневой узел и все видимые узлы
        expandAllNodes(rootNode)
    }
    
    private fun onSearchTextChanged() {
        searchText = searchTextField.text.trim()
        refreshTree()
        // Обновляем фильтрацию функций в панели структуры
        fileStructurePanel.setSearchText(searchText)
    }
    
    private fun buildTree(file: File): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(FileNode(file))
        
        if (file.isDirectory) {
            try {
                val children = file.listFiles()
                    ?.filter { child ->
                        // Фильтруем папки __pycache__
                        if (child.isDirectory && child.name == "__pycache__") {
                            return@filter false
                        }
                        true
                    }
                    ?.sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
                
                children?.forEach { child ->
                    node.add(buildTree(child))
                }
            } catch (e: Exception) {
                // Игнорируем ошибки доступа
            }
        }
        
        return node
    }
    
    private fun buildFilteredTree(file: File, rootFolder: File): DefaultMutableTreeNode? {
        val node = DefaultMutableTreeNode(FileNode(file))
        var hasMatchingChildren = false
        
        if (file.isDirectory) {
            try {
                val children = file.listFiles()
                    ?.filter { child ->
                        // Фильтруем папки __pycache__
                        if (child.isDirectory && child.name == "__pycache__") {
                            return@filter false
                        }
                        true
                    }
                    ?.sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
                
                children?.forEach { child ->
                    if (child.isFile) {
                        // Для файлов проверяем наличие текста
                        if (fileContainsText(child, searchText)) {
                            node.add(DefaultMutableTreeNode(FileNode(child)))
                            hasMatchingChildren = true
                        }
                    } else if (child.isDirectory) {
                        // Для папок рекурсивно строим дерево
                        val childNode = buildFilteredTree(child, rootFolder)
                        if (childNode != null) {
                            node.add(childNode)
                            hasMatchingChildren = true
                        }
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки доступа
            }
        }
        
        // Возвращаем узел только если есть совпадения или это корневая папка
        val isRoot = try {
            file.canonicalPath == rootFolder.canonicalPath
        } catch (e: Exception) {
            file.absolutePath == rootFolder.absolutePath
        }
        
        return if (hasMatchingChildren || isRoot) {
            node
        } else {
            null
        }
    }
    
    private fun fileContainsText(file: File, text: String): Boolean {
        if (!file.isFile || text.isBlank()) {
            return false
        }
        
        return try {
            file.readText(Charsets.UTF_8).contains(text, ignoreCase = true)
        } catch (e: Exception) {
            // Если не удалось прочитать файл (например, бинарный), возвращаем false
            false
        }
    }
    
    private fun expandAllNodes(node: DefaultMutableTreeNode) {
        val path = TreePath(node.path)
        tree.expandPath(path)
        
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode
            child?.let { expandAllNodes(it) }
        }
    }
    
    data class FileNode(val file: File) {
        override fun toString(): String = file.name
    }
    
    private class FileTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {
        init {
            icon = null
        }
        
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): java.awt.Component {
            // Вызываем родительский метод с правильными параметрами выделения
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            
            // Убираем серый фон для невыбранных элементов
            // Для выбранных элементов оставляем стандартное синее выделение
            if (!selected) {
                // Для невыбранных элементов убираем фон полностью (прозрачный/черный)
                background = null
                isOpaque = false
                // Используем правильный цвет текста из дерева
                foreground = tree?.foreground
            } else {
                // Для выбранных элементов оставляем стандартное синее выделение
                // Родительский метод уже установил правильный фон для выделения
                isOpaque = true
                // Цвет текста для выбранных элементов уже установлен родительским методом
            }
            
            val node = (value as? DefaultMutableTreeNode)?.userObject
            when {
                node is FileNode -> {
                    val file = node.file
                    if (file.isDirectory) {
                        icon = AllIcons.Nodes.Folder
                    } else {
                        icon = AllIcons.FileTypes.Any_type
                    }
                    text = file.name
                }
                node is String -> {
                    // Для корневого узла и других строковых узлов
                    if (node == "Папки") {
                        icon = AllIcons.Nodes.Folder
                    } else {
                        icon = null
                    }
                    text = node
                }
            }
            
            return this
        }
    }
    
    fun component(): JComponent {
        return mainPanel
    }
    
    fun dispose() {
        // Освобождаем ресурсы панели структуры файла
        fileStructurePanel.dispose()
    }
}

