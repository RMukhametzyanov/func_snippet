package com.testviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class FileStructurePanel(private val project: Project) {
    private val mainPanel = JPanel(BorderLayout())
    private val structureTree = Tree()
    private val scrollPane = JBScrollPane(structureTree)
    private var codeEditor: EditorEx? = null
    private val codeEditorPanel = JPanel(BorderLayout())
    private val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
    private var currentFileContent: String = ""
    private var currentFile: VirtualFile? = null
    private var searchText: String = ""
    private val addButton = JButton("+")
    
    init {
        setupPanel()
        setupTooltip()
        setupSelectionListener()
        setupDoubleClickHandler()
        showEmptyState()
    }
    
    private fun setupPanel() {
        structureTree.model = DefaultTreeModel(DefaultMutableTreeNode("Выберите файл"))
        structureTree.isRootVisible = false
        structureTree.showsRootHandles = true
        structureTree.cellRenderer = StructureTreeCellRenderer()
        
        // Настройка tooltip
        ToolTipManager.sharedInstance().registerComponent(structureTree)
        structureTree.toolTipText = null // Будем устанавливать динамически
        
        scrollPane.setViewportView(structureTree)
        scrollPane.border = EmptyBorder(5, 5, 5, 5)
        
        // Настройка кнопки "+" внизу списка функций
        addButton.toolTipText = "Сгенерировать Python функцию из fetch запроса"
        addButton.border = EmptyBorder(5, 5, 5, 5)
        addButton.isContentAreaFilled = false
        addButton.addActionListener {
            FetchToPythonDialog(project, currentFile).show()
        }
        
        // Обертка для scrollPane с кнопкой внизу
        val treePanel = JPanel(BorderLayout())
        treePanel.add(scrollPane, BorderLayout.CENTER)
        treePanel.add(addButton, BorderLayout.SOUTH)
        
        // Настройка области кода с редактором IntelliJ Platform
        setupCodeEditor()
        
        codeEditorPanel.border = EmptyBorder(5, 5, 5, 5)
        
        // Настройка split pane
        splitPane.topComponent = treePanel
        splitPane.bottomComponent = codeEditorPanel
        splitPane.orientation = JSplitPane.VERTICAL_SPLIT
        splitPane.dividerLocation = 200
        splitPane.isOneTouchExpandable = true
        splitPane.isContinuousLayout = true
        splitPane.resizeWeight = 0.5
        
        // Скрываем панель кода по умолчанию
        splitPane.bottomComponent = null
        splitPane.dividerSize = 0
        
        mainPanel.add(splitPane, BorderLayout.CENTER)
    }
    
    private fun setupCodeEditor() {
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument("")
        
        // Создаем редактор с Python файловым типом для подсветки синтаксиса
        val pythonFileType = FileTypeManager.getInstance().getFileTypeByExtension("py")
        val editor = editorFactory.createEditor(document, project, pythonFileType, false) as EditorEx
        
        // Настройки редактора
        val settings: EditorSettings = editor.settings
        settings.isLineNumbersShown = true
        settings.isLineMarkerAreaShown = false
        settings.isFoldingOutlineShown = false
        settings.isRightMarginShown = false
        settings.isWhitespacesShown = false
        settings.isLeadingWhitespaceShown = false
        settings.isTrailingWhitespaceShown = false
        settings.isIndentGuidesShown = true
        settings.isVirtualSpace = false
        settings.isCaretRowShown = false
        
        // Делаем редактор только для чтения
        editor.isViewer = true
        
        // Применяем текущую цветовую схему IDE (автоматически применяется через EditorEx)
        // Цветовая схема и размер шрифта берутся из EditorColorsManager
        
        codeEditor = editor
        
        // Добавляем редактор в панель
        codeEditorPanel.removeAll()
        codeEditorPanel.add(editor.component, BorderLayout.CENTER)
        codeEditorPanel.revalidate()
        codeEditorPanel.repaint()
    }
    
    private fun setupSelectionListener() {
        structureTree.selectionModel.addTreeSelectionListener { e ->
            val path = e.newLeadSelectionPath ?: return@addTreeSelectionListener
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val userObject = node.userObject
            
            if (userObject is StructureNode && 
                (userObject.type == StructureNodeType.FUNCTION || userObject.type == StructureNodeType.METHOD) &&
                userObject.startLine != null) {
                showFunctionCode(userObject.startLine!!, userObject.endLine)
            } else {
                hideCodePanel()
            }
        }
    }
    
    private fun setupTooltip() {
        structureTree.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val path = structureTree.getPathForLocation(e.x, e.y)
                if (path != null) {
                    val node = path.lastPathComponent as? DefaultMutableTreeNode
                    val userObject = node?.userObject
                    
                    if (userObject is StructureNode && (userObject.type == StructureNodeType.FUNCTION || userObject.type == StructureNodeType.METHOD)) {
                        val tooltip = buildTooltip(userObject)
                        structureTree.toolTipText = tooltip
                    } else {
                        structureTree.toolTipText = null
                    }
                } else {
                    structureTree.toolTipText = null
                }
            }
        })
    }
    
    private fun setupDoubleClickHandler() {
        structureTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val path = structureTree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val userObject = node.userObject
                    
                    if (userObject is StructureNode && 
                        (userObject.type == StructureNodeType.FUNCTION || userObject.type == StructureNodeType.METHOD)) {
                        insertFunctionIntoEditor(userObject)
                    }
                }
            }
        })
    }
    
    fun showFileStructure(file: File) {
        // Скрываем панель кода при выборе нового файла
        hideCodePanel()
        
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(file)
        if (virtualFile == null || !virtualFile.exists()) {
            showEmptyState()
            return
        }
        
        if (file.extension == "py") {
            showPythonStructure(virtualFile)
        } else {
            showEmptyState("Структура файла не поддерживается для типа: ${file.extension ?: "неизвестный"}")
        }
    }
    
    fun setSearchText(text: String) {
        searchText = text.trim()
        // Если файл уже открыт, обновляем структуру с учетом нового текста поиска
        if (currentFile != null) {
            showPythonStructure(currentFile!!)
        }
    }
    
    private fun showPythonStructure(virtualFile: VirtualFile) {
        // Сохраняем содержимое файла для извлечения кода функций
        try {
            currentFileContent = String(virtualFile.contentsToByteArray())
            currentFile = virtualFile
        } catch (e: Exception) {
            currentFileContent = ""
            currentFile = null
        }
        
        // Используем простой парсинг Python файлов
        parsePythonFileSimple(virtualFile)
    }
    
    private fun parsePythonFileSimple(virtualFile: VirtualFile) {
        try {
            val content = String(virtualFile.contentsToByteArray())
            val rootNode = DefaultMutableTreeNode(StructureNode(virtualFile.name, StructureNodeType.FILE))
            
            val lines = content.lines()
            val classStack = mutableListOf<Pair<DefaultMutableTreeNode, Int>>() // Класс и его уровень отступа
            
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmed = line.trim()
                val currentIndent = line.takeWhile { it == ' ' || it == '\t' }.length
                
                // Класс
                val classMatch = Regex("^class\\s+(\\w+)(?:\\([^)]*\\))?\\s*:").find(trimmed)
                if (classMatch != null) {
                    val className = classMatch.groupValues[1]
                    val classNode = DefaultMutableTreeNode(
                        StructureNode(className, StructureNodeType.CLASS)
                    )
                    
                    classStack.removeAll { it.second >= currentIndent }
                    
                    if (classStack.isEmpty()) {
                        rootNode.add(classNode)
                    } else {
                        classStack.last().first.add(classNode)
                    }
                    
                    classStack.add(Pair(classNode, currentIndent))
                    i++
                    continue
                }
                
                // Функция/метод
                // Исправляем регулярное выражение для правильного захвата типа возврата
                // Ищем весь тип возврата до двоеточия в конце строки
                val functionMatch = Regex("^def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(->\\s*(.+?))?\\s*:").find(trimmed)
                if (functionMatch != null) {
                    val functionName = functionMatch.groupValues[1]
                    val paramsStr = functionMatch.groupValues.getOrNull(2) ?: ""
                    val returnType = functionMatch.groupValues.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }
                    
                    // Парсим параметры
                    var parameters = parseParameters(paramsStr)
                    
                    // Извлекаем docstring
                    val docstring = extractDocstring(lines, i + 1)
                    
                    // Парсим docstring для извлечения описаний параметров и возвращаемого значения
                    var enrichedReturnType = returnType
                    if (docstring != null) {
                        parameters = enrichParametersFromDocstring(parameters, docstring)
                        enrichedReturnType = extractReturnTypeFromDocstring(docstring) ?: returnType
                    }
                    
                    // Формируем сигнатуру
                    val signature = buildSignature(functionName, parameters, enrichedReturnType)
                    
                    // Определяем конец функции (следующая функция/класс с таким же или меньшим отступом)
                    val endLine = findFunctionEnd(lines, i, currentIndent)
                    
                    // Проверяем, соответствует ли функция тексту поиска
                    if (searchText.isNotBlank() && !functionMatchesSearch(functionName, signature, docstring, parameters, enrichedReturnType, lines, i, endLine)) {
                        i++
                        continue
                    }
                    
                    val nodeType = if (classStack.isNotEmpty() && currentIndent > classStack.last().second) {
                        StructureNodeType.METHOD
                    } else {
                        StructureNodeType.FUNCTION
                    }
                    
                    val node = DefaultMutableTreeNode(
                        StructureNode(
                            name = functionName,
                            type = nodeType,
                            signature = signature,
                            docstring = docstring,
                            parameters = parameters,
                            returnType = enrichedReturnType,
                            startLine = i,
                            endLine = endLine
                        )
                    )
                    
                    classStack.removeAll { it.second >= currentIndent }
                    
                    if (classStack.isNotEmpty() && currentIndent > classStack.last().second) {
                        classStack.last().first.add(node)
                    } else {
                        rootNode.add(node)
                        classStack.clear()
                    }
                }
                
                i++
            }
            
            // Если есть текст поиска, удаляем классы без подходящих методов
            if (searchText.isNotBlank()) {
                removeEmptyClasses(rootNode)
            }
            
            if (rootNode.childCount == 0) {
                val message = if (searchText.isNotBlank()) {
                    "Совпадений не найдено"
                } else {
                    "Нет элементов"
                }
                rootNode.add(DefaultMutableTreeNode(StructureNode(message, StructureNodeType.UNKNOWN)))
            }
            
            structureTree.model = DefaultTreeModel(rootNode)
            structureTree.expandPath(TreePath(rootNode.path))
            
        } catch (e: Exception) {
            showEmptyState("Ошибка при парсинге файла: ${e.message}")
        }
    }
    
    private fun functionMatchesSearch(
        functionName: String,
        signature: String,
        docstring: String?,
        parameters: List<ParameterInfo>,
        returnType: String?,
        lines: List<String>,
        startLine: Int,
        endLine: Int?
    ): Boolean {
        if (searchText.isBlank()) {
            return true
        }
        
        val searchLower = searchText.lowercase()
        
        // Проверяем имя функции
        if (functionName.lowercase().contains(searchLower)) {
            return true
        }
        
        // Проверяем сигнатуру
        if (signature.lowercase().contains(searchLower)) {
            return true
        }
        
        // Проверяем docstring
        if (docstring != null && docstring.lowercase().contains(searchLower)) {
            return true
        }
        
        // Проверяем параметры
        if (parameters.any { param ->
            param.name.lowercase().contains(searchLower) ||
            (param.type?.lowercase()?.contains(searchLower) == true) ||
            (param.description?.lowercase()?.contains(searchLower) == true)
        }) {
            return true
        }
        
        // Проверяем тип возврата
        if (returnType != null && returnType.lowercase().contains(searchLower)) {
            return true
        }
        
        // Проверяем код функции
        val actualEndLine = endLine ?: (lines.size - 1)
        if (startLine < lines.size && actualEndLine < lines.size) {
            val functionCode = lines.subList(startLine, minOf(actualEndLine + 1, lines.size))
                .joinToString("\n")
            if (functionCode.lowercase().contains(searchLower)) {
                return true
            }
        }
        
        return false
    }
    
    private fun removeEmptyClasses(node: DefaultMutableTreeNode) {
        val nodesToRemove = mutableListOf<DefaultMutableTreeNode>()
        
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val userObject = child.userObject
            
            if (userObject is StructureNode && userObject.type == StructureNodeType.CLASS) {
                // Рекурсивно удаляем пустые классы внутри
                removeEmptyClasses(child)
                
                // Если класс не имеет детей (методов), удаляем его
                if (child.childCount == 0) {
                    nodesToRemove.add(child)
                }
            }
        }
        
        // Удаляем пустые классы
        nodesToRemove.forEach { node.remove(it) }
    }
    
    private fun parseParameters(paramsStr: String): List<ParameterInfo> {
        if (paramsStr.trim().isEmpty()) return emptyList()
        
        val params = mutableListOf<ParameterInfo>()
        val parts = paramsStr.split(',').map { it.trim() }
        
        for (part in parts) {
            if (part.isEmpty()) continue
            
            // Парсим параметр вида: name: type = default
            val paramMatch = Regex("(\\w+)(?::\\s*([^=]+))?(?:\\s*=\\s*(.+))?").find(part)
            if (paramMatch != null) {
                val name = paramMatch.groupValues[1]
                val type = paramMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }
                val defaultValue = paramMatch.groupValues[3].trim().takeIf { it.isNotEmpty() }
                
                params.add(ParameterInfo(name, type, defaultValue))
            } else {
                // Просто имя параметра
                params.add(ParameterInfo(part))
            }
        }
        
        return params
    }
    
    private fun extractDocstring(lines: List<String>, startIndex: Int): String? {
        if (startIndex >= lines.size) return null
        
        val docstringLines = mutableListOf<String>()
        var i = startIndex
        var inDocstring = false
        var quoteType: String? = null
        
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (!inDocstring) {
                // Ищем начало docstring
                if (line.startsWith("\"\"\"") || line.startsWith("'''")) {
                    inDocstring = true
                    quoteType = if (line.startsWith("\"\"\"")) "\"\"\"" else "'''"
                    
                    val content = line.removePrefix(quoteType).trim()
                    if (content.isNotEmpty() && !content.endsWith(quoteType)) {
                        docstringLines.add(content)
                    } else if (content.endsWith(quoteType)) {
                        // Однострочный docstring
                        docstringLines.add(content.removeSuffix(quoteType).trim())
                        break
                    }
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    // Если встретили непустую строку, не являющуюся комментарием, значит docstring закончился
                    break
                }
            } else {
                // Ищем конец docstring
                if (quoteType != null && line.endsWith(quoteType)) {
                    val content = line.removeSuffix(quoteType).trim()
                    if (content.isNotEmpty()) {
                        docstringLines.add(content)
                    }
                    break
                } else {
                    docstringLines.add(line)
                }
            }
            
            i++
        }
        
        return docstringLines.joinToString("\n").takeIf { it.isNotEmpty() }
    }
    
    private fun buildSignature(name: String, parameters: List<ParameterInfo>, returnType: String?): String {
        val paramsStr = parameters.joinToString(", ") { param ->
            val sb = StringBuilder()
            sb.append(param.name)
            if (param.type != null) {
                sb.append(": ")
                sb.append(param.type)
            }
            if (param.defaultValue != null) {
                sb.append(" = ")
                sb.append(param.defaultValue)
            }
            sb.toString()
        }
        
        val sb = StringBuilder()
        sb.append(name)
        sb.append("(")
        sb.append(paramsStr)
        sb.append(")")
        if (returnType != null) {
            sb.append(" -> ")
            sb.append(returnType)
        }
        return sb.toString()
    }
    
    fun showEmptyState(message: String = "Выберите файл для просмотра структуры") {
        val rootNode = DefaultMutableTreeNode(StructureNode(message, StructureNodeType.UNKNOWN))
        structureTree.model = DefaultTreeModel(rootNode)
    }
    
    data class StructureNode(
        val name: String, 
        val type: StructureNodeType,
        val signature: String? = null,
        val docstring: String? = null,
        val parameters: List<ParameterInfo> = emptyList(),
        val returnType: String? = null,
        val startLine: Int? = null,
        val endLine: Int? = null
    ) {
        override fun toString(): String = name
    }
    
    data class ParameterInfo(
        val name: String,
        val type: String? = null,
        val defaultValue: String? = null,
        val description: String? = null
    )
    
    enum class StructureNodeType {
        FILE, CLASS, FUNCTION, METHOD, UNKNOWN
    }
    
    private class StructureTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {
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
            if (node is StructureNode) {
                icon = when (node.type) {
                    StructureNodeType.FILE -> AllIcons.FileTypes.Any_type
                    StructureNodeType.CLASS -> AllIcons.Nodes.Class
                    StructureNodeType.FUNCTION -> AllIcons.Nodes.Function
                    StructureNodeType.METHOD -> AllIcons.Nodes.Method
                    StructureNodeType.UNKNOWN -> null
                }
                text = node.name
            }
            
            return this
        }
    }
    
    private fun buildTooltip(node: StructureNode): String {
        val html = StringBuilder()
        html.append("<html><body style='font-family: Consolas, monospace; font-size: 12px;'>")
        
        // Сигнатура функции
        if (node.signature != null) {
            html.append("<div style='color: #569CD6; font-weight: bold; margin-bottom: 8px;'>")
            html.append(escapeHtml(node.signature))
            html.append("</div>")
        }
        
        // Docstring
        if (node.docstring != null) {
            html.append("<div style='color: #D4D4D4; margin-bottom: 8px; white-space: pre-wrap;'>")
            html.append(escapeHtml(node.docstring))
            html.append("</div>")
        }
        
        // Параметры
        if (node.parameters.isNotEmpty()) {
            html.append("<div style='margin-top: 8px; border-top: 1px solid #3E3E42; padding-top: 8px;'>")
            html.append("<div style='color: #569CD6; font-weight: bold; margin-bottom: 4px;'>Params:</div>")
            
            node.parameters.forEach { param ->
                html.append("<div style='margin-left: 12px; margin-bottom: 4px;'>")
                html.append("<span style='color: #9CDCFE;'>")
                html.append(escapeHtml(param.name))
                html.append("</span>")
                
                if (param.type != null) {
                    html.append("<span style='color: #4EC9B0;'>: ")
                    html.append(escapeHtml(param.type))
                    html.append("</span>")
                }
                
                if (param.defaultValue != null) {
                    html.append("<span style='color: #CE9178;'> = ")
                    html.append(escapeHtml(param.defaultValue))
                    html.append("</span>")
                }
                
                if (param.description != null) {
                    html.append("<span style='color: #6A9955;'> - ")
                    html.append(escapeHtml(param.description))
                    html.append("</span>")
                }
                
                html.append("</div>")
            }
            html.append("</div>")
        }
        
        // Возвращаемое значение
        if (node.returnType != null) {
            html.append("<div style='margin-top: 8px; border-top: 1px solid #3E3E42; padding-top: 8px;'>")
            html.append("<div style='color: #569CD6; font-weight: bold;'>Returns:</div>")
            html.append("<div style='margin-left: 12px; color: #D4D4D4;'>")
            html.append(escapeHtml(node.returnType))
            html.append("</div>")
            html.append("</div>")
        }
        
        html.append("</body></html>")
        return html.toString()
    }
    
    private fun enrichParametersFromDocstring(parameters: List<ParameterInfo>, docstring: String): List<ParameterInfo> {
        val enrichedParams = mutableListOf<ParameterInfo>()
        
        // Парсим docstring в формате Google/NumPy style
        // Ищем секцию Params: или Args:
        val paramsSectionRegex = Regex("(?:Params?|Args?):\\s*\\n((?:\\s+\\w+.*\\n?)+)", RegexOption.MULTILINE)
        val paramsMatch = paramsSectionRegex.find(docstring)
        
        if (paramsMatch != null) {
            val paramsText = paramsMatch.groupValues[1]
            val paramDescriptions = mutableMapOf<String, String>()
            
            // Парсим каждую строку параметра: param_name: описание
            val paramLineRegex = Regex("\\s+(\\w+)\\s*:?\\s*(.+?)(?=\\n\\s+\\w+|\\n\\s*\\n|$)", RegexOption.MULTILINE)
            paramLineRegex.findAll(paramsText).forEach { match ->
                val paramName = match.groupValues[1]
                val description = match.groupValues[2].trim()
                paramDescriptions[paramName] = description
            }
            
            // Обогащаем параметры описаниями
            parameters.forEach { param ->
                val description = paramDescriptions[param.name]
                enrichedParams.add(param.copy(description = description))
            }
        } else {
            // Если не нашли секцию Params, возвращаем как есть
            enrichedParams.addAll(parameters)
        }
        
        return enrichedParams
    }
    
    private fun extractReturnTypeFromDocstring(docstring: String): String? {
        // Ищем секцию Returns: или Return:
        val returnsSectionRegex = Regex("(?:Returns?):\\s*\\n?\\s*(.+?)(?=\\n\\s*\\n|\\n\\s*\\w+:|$)", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        val returnsMatch = returnsSectionRegex.find(docstring)
        
        return returnsMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    private fun findFunctionEnd(lines: List<String>, startLine: Int, functionIndent: Int): Int {
        // Ищем конец функции - следующую строку с таким же или меньшим отступом, которая не пустая и не комментарий
        // Пропускаем docstring и пустые строки в начале функции
        var foundNonEmpty = false
        
        for (i in (startLine + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val currentIndent = line.takeWhile { it == ' ' || it == '\t' }.length
            
            // Пропускаем пустые строки и комментарии до первого непустого кода
            if (!foundNonEmpty) {
                if (trimmed.isEmpty() || trimmed.startsWith("#") || 
                    trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
                    continue
                }
                foundNonEmpty = true
            }
            
            // Если отступ меньше отступа функции, значит функция закончилась
            if (currentIndent < functionIndent) {
                return i - 1
            }
            
            // Если отступ равен отступу функции и это начало новой функции/класса, функция закончилась
            if (currentIndent == functionIndent && 
                (trimmed.startsWith("def ") || trimmed.startsWith("class "))) {
                return i - 1
            }
        }
        
        // Если дошли до конца файла, функция заканчивается на последней строке
        return lines.size - 1
    }
    
    private fun showFunctionCode(startLine: Int, endLine: Int?) {
        val editor = codeEditor ?: return
        
        if (currentFileContent.isEmpty()) {
            hideCodePanel()
            return
        }
        
        val lines = currentFileContent.lines()
        if (startLine >= lines.size) {
            hideCodePanel()
            return
        }
        
        val actualEndLine = endLine ?: (lines.size - 1)
        val functionLines = lines.subList(startLine, minOf(actualEndLine + 1, lines.size))
        val functionCode = functionLines.joinToString("\n")
        
        // Устанавливаем текст в редактор
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(functionCode)
        }
        
        // Перемещаем каретку в начало
        editor.caretModel.moveToOffset(0)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        
        // Показываем панель кода
        if (splitPane.bottomComponent == null) {
            splitPane.bottomComponent = codeEditorPanel
            splitPane.dividerSize = 5
            splitPane.dividerLocation = 200
            splitPane.validate()
            splitPane.repaint()
        }
    }
    
    private fun hideCodePanel() {
        if (splitPane.bottomComponent != null) {
            splitPane.bottomComponent = null
            splitPane.dividerSize = 0
            splitPane.validate()
            splitPane.repaint()
        }
        
        // Очищаем редактор
        val editor = codeEditor
        if (editor != null) {
            val document = editor.document
            WriteCommandAction.runWriteCommandAction(project) {
                document.setText("")
            }
        }
    }
    
    private fun insertFunctionIntoEditor(functionNode: StructureNode) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedFiles = fileEditorManager.selectedFiles
        
        if (selectedFiles.isEmpty()) {
            // Если нет открытого файла, открываем файл с функцией
            currentFile?.let { file ->
                fileEditorManager.openFile(file, true)
            }
            return
        }
        
        val targetFile = selectedFiles[0]
        if (targetFile.extension != "py") {
            return // Работаем только с Python файлами
        }
        
        val editor = fileEditorManager.getSelectedTextEditor()
        if (editor == null) {
            return
        }
        
        val sourceFile = currentFile ?: return
        val functionName = functionNode.name
        
        // Проверяем настройку использования префикса модуля
        val settings = FolderScannerSettings.getInstance()
        val useModulePrefix = settings.state.useModulePrefix
        
        // Получаем имя модуля (имя файла без расширения)
        val moduleName = if (useModulePrefix) {
            sourceFile.nameWithoutExtension
        } else {
            null
        }
        
        // Формируем путь для импорта
        val importPath = calculateImportPath(sourceFile, targetFile, useModulePrefix, moduleName)
        
        // Формируем вызов функции
        val functionCall = buildFunctionCall(functionNode, moduleName)
        
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val originalCaretOffset = editor.caretModel.offset
            
            // Сначала добавляем импорт в верх файла
            val importInsertedInfo = addImportIfNeededInternal(editor, importPath, functionName, useModulePrefix, moduleName)
            
            // Корректируем позицию курсора, если импорт был вставлен перед курсором
            // importInsertedInfo содержит (offset, length) - где был вставлен импорт и его длина
            val adjustedCaretOffset = if (importInsertedInfo != null && importInsertedInfo.first < originalCaretOffset) {
                // Импорт был вставлен перед курсором, нужно скорректировать позицию
                originalCaretOffset + importInsertedInfo.second
            } else {
                originalCaretOffset
            }
            
            // Находим конец строки, где находится курсор
            val currentLineNumber = document.getLineNumber(adjustedCaretOffset)
            val lineEndOffset = document.getLineEndOffset(currentLineNumber)
            
            // Перемещаем курсор в конец строки
            editor.caretModel.moveToOffset(lineEndOffset)
            
            // Вставляем перевод строки и вызов функции
            val textToInsert = "\n$functionCall\n"
            document.insertString(lineEndOffset, textToInsert)
            
            // Перемещаем курсор после вставленного кода (на новой строке)
            editor.caretModel.moveToOffset(lineEndOffset + textToInsert.length)
        }
    }
    
    private fun calculateImportPath(sourceFile: VirtualFile, targetFile: VirtualFile, useModulePrefix: Boolean, moduleName: String?): String {
        val projectBaseDir = project.baseDir ?: return ""
        
        // Получаем относительные пути от корня проекта
        val sourcePath = VfsUtilCore.getRelativePath(sourceFile, projectBaseDir, '.')
            ?: return ""
        val targetPath = VfsUtilCore.getRelativePath(targetFile, projectBaseDir, '.')
            ?: return ""
        
        // Убираем расширения .py
        val sourceModule = sourcePath.removeSuffix(".py")
        val targetModule = targetPath.removeSuffix(".py")
        
        // Если файлы одинаковые, не добавляем импорт
        if (sourceModule == targetModule) {
            return ""
        }
        
        // Если включен режим префикса модуля, возвращаем путь к директории модуля
        if (useModulePrefix && moduleName != null) {
            // Получаем директорию модуля (путь без имени файла)
            val moduleDir = sourceModule.substringBeforeLast('.', "")
            // Если модуль в корне проекта, возвращаем пустую строку (будет импорт без from)
            return if (moduleDir.isEmpty()) {
                "" // Модуль в корне, импорт будет просто "import moduleName"
            } else {
                moduleDir // Возвращаем путь к директории для "from moduleDir import moduleName"
            }
        }
        
        // Получаем директории файлов
        val sourceDir = sourceModule.substringBeforeLast('.', "")
        val targetDir = targetModule.substringBeforeLast('.', "")
        val sourceFileName = sourceModule.substringAfterLast('.', sourceModule)
        
        return if (sourceDir == targetDir) {
            // Импорт из той же директории - просто имя файла
            sourceFileName
        } else {
            // Импорт из другой директории - полный путь модуля
            sourceModule
        }
    }
    
    private fun buildFunctionCall(functionNode: StructureNode, moduleName: String?): String {
        val functionName = functionNode.name
        val parameters = functionNode.parameters
        
        // Формируем список аргументов, исключая параметр self
        val args = if (parameters.isNotEmpty()) {
            parameters
                .filter { it.name != "self" }  // Игнорируем параметр self
                .joinToString(", ") { param ->
                    "${param.name}=YOUR_ARG"
                }
        } else {
            ""
        }
        
        // Если указан префикс модуля, добавляем его
        val fullFunctionName = if (moduleName != null) {
            "$moduleName.$functionName"
        } else {
            functionName
        }
        
        val functionCall = if (args.isNotEmpty()) {
            "$fullFunctionName($args)"
        } else {
            "$fullFunctionName()"
        }
        
        // Анализируем return statements в коде функции
        val returnAnalysis = analyzeReturnStatements(functionNode)
        
        return when {
            returnAnalysis.count == 0 -> functionCall
            returnAnalysis.count == 1 -> {
                // Если return содержит вызов функции, используем имя функции + "_value"
                val variableName = returnAnalysis.functionCallName?.let { "${it}_value" } ?: "value"
                "$variableName = $functionCall"
            }
            else -> {
                // Генерируем value1, value2, ..., valueN
                // Если return содержит вызов функции, используем имя функции + "_value"
                val baseName = returnAnalysis.functionCallName?.let { "${it}_value" } ?: "value"
                val variables = (1..returnAnalysis.count).joinToString(", ") { "$baseName$it" }
                "$variables = $functionCall"
            }
        }
    }
    
    /**
     * Результат анализа return statements в функции
     */
    private data class ReturnAnalysis(
        val count: Int,  // Количество возвращаемых значений
        val functionCallName: String? = null  // Имя функции в return func(), если есть
    )
    
    /**
     * Анализирует все return statements в функции и определяет максимальное количество возвращаемых значений.
     */
    private fun analyzeReturnStatements(functionNode: StructureNode): ReturnAnalysis {
        val startLine = functionNode.startLine ?: return ReturnAnalysis(1)
        val endLine = functionNode.endLine ?: return ReturnAnalysis(1)
        
        if (currentFileContent.isEmpty()) {
            return ReturnAnalysis(1)
        }
        
        val lines = currentFileContent.lines()
        if (startLine >= lines.size || endLine >= lines.size) {
            return ReturnAnalysis(1)
        }
        
        // Извлекаем код функции
        val functionCode = lines.subList(startLine, minOf(endLine + 1, lines.size))
            .joinToString("\n")
        
        // Ищем все return statements
        val returnStatements = findReturnStatements(functionCode)
        
        if (returnStatements.isEmpty()) {
            // Если нет return, функция ничего не возвращает (или возвращает None)
            return ReturnAnalysis(0)
        }
        
        var maxCount = 0
        var functionCallName: String? = null
        
        for (returnStmt in returnStatements) {
            val analysis = analyzeSingleReturn(returnStmt)
            maxCount = maxOf(maxCount, analysis.count)
            if (analysis.functionCallName != null) {
                functionCallName = analysis.functionCallName
            }
        }
        
        return ReturnAnalysis(maxCount, functionCallName)
    }
    
    /**
     * Находит все return statements в коде функции.
     */
    private fun findReturnStatements(functionCode: String): List<String> {
        val returns = mutableListOf<String>()
        val lines = functionCode.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            // Ищем return в начале строки (после возможных пробелов/табов)
            if (line.startsWith("return ")) {
                // Находим начало return statement
                val returnStart = lines[i].indexOf("return")
                if (returnStart >= 0) {
                    // Извлекаем return statement, учитывая многострочные выражения
                    val returnStmt = extractReturnStatement(lines, i, returnStart)
                    if (returnStmt.isNotEmpty()) {
                        returns.add(returnStmt)
                    }
                    i++
                } else {
                    i++
                }
            } else {
                i++
            }
        }
        
        return returns
    }
    
    /**
     * Извлекает полный return statement, учитывая многострочные выражения.
     */
    private fun extractReturnStatement(lines: List<String>, startLine: Int, returnStart: Int): String {
        val line = lines[startLine]
        val afterReturn = line.substring(returnStart + 6).trimStart() // "return " = 6 символов
        
        // Если return пустой или только return без значения
        if (afterReturn.isEmpty() || afterReturn == ";") {
            return ""
        }
        
        // Удаляем комментарии из строки
        val withoutComments = afterReturn.split('#').first().trim()
        if (withoutComments.isEmpty()) {
            return ""
        }
        
        // Собираем выражение, учитывая многострочность
        val expression = StringBuilder(withoutComments)
        var currentLine = startLine
        var depth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var stringChar: Char? = null
        var escaped = false
        
        // Подсчитываем скобки в первой строке
        for (char in withoutComments) {
            if (escaped) {
                escaped = false
                continue
            }
            
            when (char) {
                '\\' -> escaped = true
                '"', '\'' -> {
                    if (!inString) {
                        inString = true
                        stringChar = char
                    } else if (char == stringChar) {
                        inString = false
                        stringChar = null
                    }
                }
                '(' -> if (!inString) depth++
                ')' -> if (!inString) depth--
                '[' -> if (!inString) bracketDepth++
                ']' -> if (!inString) bracketDepth--
                '{' -> if (!inString) braceDepth++
                '}' -> if (!inString) braceDepth--
            }
        }
        
        // Если выражение не завершено, продолжаем читать следующие строки
        currentLine++
        while (currentLine < lines.size && (depth > 0 || bracketDepth > 0 || braceDepth > 0 || inString)) {
            val nextLine = lines[currentLine].trim()
            if (nextLine.isEmpty() || nextLine.startsWith("#")) {
                currentLine++
                continue
            }
            
            // Удаляем комментарии
            val lineWithoutComments = nextLine.split('#').first().trim()
            if (lineWithoutComments.isNotEmpty()) {
                expression.append(" ").append(lineWithoutComments)
                
                // Обновляем счетчики скобок
                for (char in lineWithoutComments) {
                    if (escaped) {
                        escaped = false
                        continue
                    }
                    
                    when (char) {
                        '\\' -> escaped = true
                        '"', '\'' -> {
                            if (!inString) {
                                inString = true
                                stringChar = char
                            } else if (char == stringChar) {
                                inString = false
                                stringChar = null
                            }
                        }
                        '(' -> if (!inString) depth++
                        ')' -> if (!inString) depth--
                        '[' -> if (!inString) bracketDepth++
                        ']' -> if (!inString) bracketDepth--
                        '{' -> if (!inString) braceDepth++
                        '}' -> if (!inString) braceDepth--
                    }
                }
            }
            
            currentLine++
        }
        
        return expression.toString().trim()
    }
    
    /**
     * Анализирует один return statement и определяет количество возвращаемых значений.
     */
    private fun analyzeSingleReturn(returnStmt: String): ReturnAnalysis {
        val trimmed = returnStmt.trim()
        
        if (trimmed.isEmpty()) {
            return ReturnAnalysis(0)
        }
        
        // Проверяем, является ли это вызовом функции: func() или func(args)
        val functionCallPattern = Regex("""^(\w+)\s*\([^)]*\)\s*$""")
        val functionCallMatch = functionCallPattern.find(trimmed)
        
        if (functionCallMatch != null) {
            val functionName = functionCallMatch.groupValues[1]
            // Это return func() - возвращаем одно значение, но запоминаем имя функции
            return ReturnAnalysis(1, functionName)
        }
        
        // Подсчитываем количество значений в return
        // Если это tuple unpacking: return a, b, c
        // Или просто одно значение: return value
        
        // Считаем запятые на верхнем уровне (не внутри скобок/кавычек)
        var count = 1 // Минимум одно значение
        var depth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var stringChar: Char? = null
        var escaped = false
        
        for (char in trimmed) {
            if (escaped) {
                escaped = false
                continue
            }
            
            when (char) {
                '\\' -> escaped = true
                '"', '\'' -> {
                    if (!inString) {
                        inString = true
                        stringChar = char
                    } else if (char == stringChar) {
                        inString = false
                        stringChar = null
                    }
                }
                '(' -> if (!inString) depth++
                ')' -> if (!inString) depth--
                '[' -> if (!inString) bracketDepth++
                ']' -> if (!inString) bracketDepth--
                '{' -> if (!inString) braceDepth++
                '}' -> if (!inString) braceDepth--
                ',' -> {
                    // Запятая на верхнем уровне разделяет значения
                    if (!inString && depth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        count++
                    }
                }
            }
        }
        
        return ReturnAnalysis(count)
    }
    
    /**
     * Парсит тип возвращаемого значения и определяет количество возвращаемых элементов.
     * Возвращает 0, если функция ничего не возвращает, 1 для одного значения, N для Tuple с N элементами.
     * Анализирует как аннотацию типа, так и docstring в секции Returns.
     */
    private fun parseReturnValueCount(returnType: String?, docstring: String?): Int {
        // Сначала проверяем аннотацию типа
        if (returnType != null && returnType.trim().isNotEmpty()) {
            val trimmed = returnType.trim()
            
            // Проверяем на None
            if (trimmed.lowercase() == "none" || trimmed == "None") {
                return 0
            }
            
            // Парсим Tuple типы: Tuple[int, str], tuple[int, str], (int, str)
            // Ищем паттерн: tuple[...] или Tuple[...] (с квадратными скобками)
            val tupleBracketRegex = Regex("""\b(?:Tuple|tuple)\s*\[""", RegexOption.IGNORE_CASE)
            val tupleBracketMatch = tupleBracketRegex.find(trimmed)
            
            if (tupleBracketMatch != null) {
                // Находим позицию открывающей квадратной скобки
                val matchEnd = tupleBracketMatch.range.last + 1
                
                if (matchEnd > 0 && matchEnd <= trimmed.length) {
                    val content = extractBracketContent(trimmed, matchEnd, '[', ']')
                    if (content != null && content.isNotEmpty()) {
                        val count = countTupleElements(content)
                        if (count > 0) {
                            return count
                        }
                    }
                }
            }
            
            // Пытаемся найти круглые скобки (...) в начале строки
            if (trimmed.startsWith("(")) {
                val content = extractBracketContent(trimmed, 1, '(', ')')
                if (content != null && content.isNotEmpty()) {
                    val count = countTupleElements(content)
                    if (count > 0) {
                        return count
                    }
                }
            }
            
            // Если аннотация типа есть, но это не tuple, проверяем docstring
            // Возможно, в docstring указано несколько возвращаемых значений
            if (docstring != null) {
                val returnsCount = parseReturnValueCountFromDocstring(docstring)
                if (returnsCount > 1) {
                    // Если в docstring указано несколько значений, используем это
                    return returnsCount
                }
            }
            
            // Если docstring не указал несколько значений, значит одно значение
            return 1
        }
        
        // Если аннотации типа нет, анализируем docstring
        if (docstring != null) {
            val returnsCount = parseReturnValueCountFromDocstring(docstring)
            if (returnsCount > 0) {
                return returnsCount
            }
        }
        
        // Если ничего не найдено, по умолчанию считаем одно значение
        return 1
    }
    
    /**
     * Анализирует docstring в секции Returns и определяет количество возвращаемых значений.
     * Ищет паттерны типа:
     * - "value1: описание, value2: описание" (несколько значений через запятую)
     * - Список значений с двоеточиями
     */
    private fun parseReturnValueCountFromDocstring(docstring: String): Int {
        // Ищем секцию Returns:
        val returnsSectionRegex = Regex("""(?:Returns?):\s*\n?\s*(.+?)(?=\n\s*\n|\n\s*\w+:|$)""", 
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        val returnsMatch = returnsSectionRegex.find(docstring)
        
        if (returnsMatch == null) {
            return 0
        }
        
        val returnsContent = returnsMatch.groupValues[1].trim()
        if (returnsContent.isEmpty()) {
            return 0
        }
        
        // Ищем паттерн с несколькими значениями через запятую с двоеточиями
        // Например: "value1: описание, value2: описание" или "value1: описание\nvalue2: описание"
        val multiValuePattern = Regex("""(\w+)\s*:\s*[^,\n]+(?:,\s*(\w+)\s*:\s*[^,\n]+)+""", RegexOption.MULTILINE)
        val multiValueMatch = multiValuePattern.find(returnsContent)
        
        if (multiValueMatch != null) {
            // Подсчитываем количество найденных значений
            val allMatches = multiValuePattern.findAll(returnsContent)
            var maxCount = 0
            for (match in allMatches) {
                // Считаем количество групп с именами переменных
                val variableNames = match.groupValues.filterIndexed { index, _ -> index > 0 && index % 2 == 1 }
                maxCount = maxOf(maxCount, variableNames.size)
            }
            if (maxCount > 1) {
                return maxCount
            }
        }
        
        // Альтернативный паттерн: каждое значение на новой строке с двоеточием
        // Например:
        // value1: описание
        // value2: описание
        val lineByLinePattern = Regex("""^\s*(\w+)\s*:\s*[^\n]+""", RegexOption.MULTILINE)
        val lineMatches = lineByLinePattern.findAll(returnsContent)
        val lineCount = lineMatches.count()
        if (lineCount > 1) {
            return lineCount
        }
        
        // Если найден только один паттерн с двоеточием или нет паттернов, значит одно значение
        return 0
    }
    
    /**
     * Извлекает содержимое из скобок, учитывая вложенные структуры.
     */
    private fun extractBracketContent(text: String, startPos: Int, openBracket: Char, closeBracket: Char): String? {
        if (startPos >= text.length) return null
        
        var depth = 1
        var pos = startPos
        val content = StringBuilder()
        
        while (pos < text.length && depth > 0) {
            val char = text[pos]
            when (char) {
                openBracket -> {
                    depth++
                    // Добавляем открывающую скобку только если она внутри (depth > 1)
                    if (depth > 1) {
                        content.append(char)
                    }
                }
                closeBracket -> {
                    depth--
                    // Добавляем закрывающую скобку только если мы еще внутри (depth > 0)
                    if (depth > 0) {
                        content.append(char)
                    }
                }
                else -> {
                    content.append(char)
                }
            }
            pos++
        }
        
        // Возвращаем содержимое только если все скобки закрыты
        return if (depth == 0) content.toString().trim() else null
    }
    
    /**
     * Подсчитывает количество элементов в Tuple, учитывая вложенные структуры.
     */
    private fun countTupleElements(content: String): Int {
        if (content.trim().isEmpty()) {
            return 0
        }
        
        var count = 0
        var depth = 0
        var bracketDepth = 0
        var currentElement = StringBuilder()
        
        for (char in content) {
            when (char) {
                '(' -> {
                    depth++
                    currentElement.append(char)
                }
                ')' -> {
                    depth--
                    currentElement.append(char)
                }
                '[' -> {
                    bracketDepth++
                    currentElement.append(char)
                }
                ']' -> {
                    bracketDepth--
                    currentElement.append(char)
                }
                ',' -> {
                    if (depth == 0 && bracketDepth == 0) {
                        // Это разделитель элементов верхнего уровня
                        val element = currentElement.toString().trim()
                        if (element.isNotEmpty()) {
                            count++
                        }
                        currentElement.clear()
                    } else {
                        // Это запятая внутри вложенной структуры
                        currentElement.append(char)
                    }
                }
                else -> {
                    currentElement.append(char)
                }
            }
        }
        
        // Добавляем последний элемент
        val lastElement = currentElement.toString().trim()
        if (lastElement.isNotEmpty()) {
            count++
        }
        
        return count
    }
    
    private fun addImportIfNeededInternal(
        editor: com.intellij.openapi.editor.Editor, 
        importPath: String, 
        functionName: String,
        useModulePrefix: Boolean,
        moduleName: String?
    ): Pair<Int, Int>? {
        if (importPath.isEmpty()) {
            return null // Не нужно добавлять импорт
        }
        
        val document = editor.document
        val text = document.text
        
        // Если включен режим префикса модуля, импортируем модуль, а не функцию
        if (useModulePrefix && moduleName != null) {
            // Формируем импорт модуля
            val importStatement = if (importPath.isEmpty()) {
                // Модуль в корне проекта
                "import $moduleName"
            } else {
                // Модуль в поддиректории
                "from $importPath import $moduleName"
            }
            // Проверяем, есть ли уже импорт модуля
            val fullImportRegex = if (importPath.isEmpty()) {
                Regex("^import\\s+$moduleName(?:\\s*,\\s*|\\s*$)", RegexOption.MULTILINE)
            } else {
                Regex("from\\s+$importPath\\s+import\\s+$moduleName(?:\\s*,\\s*|\\s*$)", RegexOption.MULTILINE)
            }
            if (fullImportRegex.find(text) != null) {
                return null // Импорт уже есть
            }
            
            // Проверяем, есть ли уже импорт из этого модуля/директории
            val existingImportRegex = if (importPath.isEmpty()) {
                Regex("^import\\s+([^\\n]+)", RegexOption.MULTILINE)
            } else {
                Regex("from\\s+$importPath\\s+import\\s+([^\\n]+)", RegexOption.MULTILINE)
            }
            val existingImportMatch = existingImportRegex.find(text)
            
            if (existingImportMatch != null) {
                // Добавляем модуль к существующему импорту
                val existingImport = existingImportMatch.value
                val importsList = existingImportMatch.groupValues[1].trim()
                
                // Проверяем, не добавлен ли уже модуль
                val moduleNameRegex = Regex("\\b$moduleName\\b")
                if (moduleNameRegex.find(importsList) != null) {
                    return null // Модуль уже в импорте
                }
                
                // Формируем новый импорт
                val newImportsList = if (importsList.contains(",")) {
                    "$importsList, $moduleName"
                } else {
                    "$importsList, $moduleName"
                }
                
                val newImport = if (importPath.isEmpty()) {
                    "import $newImportsList"
                } else {
                    "from $importPath import $newImportsList"
                }
                val startOffset = existingImportMatch.range.first
                val endOffset = existingImportMatch.range.last + 1
                
                document.replaceString(startOffset, endOffset, newImport)
                // При замене существующего импорта позиция курсора не меняется
                return null
            }
            
            // Находим место для вставки импорта (после всех существующих импортов)
            val importSectionEnd = findImportSectionEnd(text)
            
            // Если файл пустой, просто вставляем импорт
            if (text.isEmpty()) {
                val insertedText = importStatement + "\n"
                document.insertString(0, insertedText)
                return Pair(0, insertedText.length) // offset, length
            }
            
            // Вставляем новый импорт
            val insertText = if (importSectionEnd > 0 && importSectionEnd <= text.length && text[importSectionEnd - 1] != '\n') {
                "\n$importStatement"
            } else {
                importStatement
            }
            
            // Проверяем, нужно ли добавлять перевод строки в конце
            val needsNewline = if (importSectionEnd < text.length) {
                text[importSectionEnd] != '\n'
            } else {
                // Если мы в конце файла, проверяем последний символ
                text.isNotEmpty() && text[text.length - 1] != '\n'
            }
            
            val finalInsertText = if (needsNewline) {
                insertText + "\n"
            } else {
                if (importSectionEnd == 0 || (importSectionEnd > 0 && importSectionEnd <= text.length && text[importSectionEnd - 1] == '\n')) {
                    insertText
                } else {
                    insertText + "\n"
                }
            }
            
            document.insertString(importSectionEnd, finalInsertText)
            
            // Возвращаем (offset, length) - где был вставлен импорт и его длина
            return Pair(importSectionEnd, finalInsertText.length)
        } else {
            // Старая логика: импорт функции
            // Проверяем, есть ли уже такой импорт
            val importStatement = "from $importPath import $functionName"
            val fullImportRegex = Regex("from\\s+$importPath\\s+import\\s+$functionName(?:\\s*,\\s*|\\s*$)")
            if (fullImportRegex.find(text) != null) {
                return null // Импорт уже есть
            }
            
            // Проверяем, есть ли уже импорт из этого модуля
            val existingImportRegex = Regex("from\\s+$importPath\\s+import\\s+([^\\n]+)")
            val existingImportMatch = existingImportRegex.find(text)
            
            if (existingImportMatch != null) {
                // Добавляем функцию к существующему импорту
                val existingImport = existingImportMatch.value
                val importsList = existingImportMatch.groupValues[1].trim()
                
                // Проверяем, не добавлена ли уже функция
                val functionNameRegex = Regex("\\b$functionName\\b")
                if (functionNameRegex.find(importsList) != null) {
                    return null // Функция уже в импорте
                }
                
                // Формируем новый импорт
                val newImportsList = if (importsList.contains(",")) {
                    "$importsList, $functionName"
                } else {
                    "$importsList, $functionName"
                }
                
                val newImport = "from $importPath import $newImportsList"
                val startOffset = existingImportMatch.range.first
                val endOffset = existingImportMatch.range.last + 1
                
                document.replaceString(startOffset, endOffset, newImport)
                // При замене существующего импорта позиция курсора не меняется
                return null
            }
            
            // Находим место для вставки импорта (после всех существующих импортов)
            val importSectionEnd = findImportSectionEnd(text)
            
            // Если файл пустой, просто вставляем импорт
            if (text.isEmpty()) {
                val insertedText = importStatement + "\n"
                document.insertString(0, insertedText)
                return Pair(0, insertedText.length) // offset, length
            }
            
            // Вставляем новый импорт
            val insertText = if (importSectionEnd > 0 && importSectionEnd <= text.length && text[importSectionEnd - 1] != '\n') {
                "\n$importStatement"
            } else {
                importStatement
            }
            
            // Проверяем, нужно ли добавлять перевод строки в конце
            val needsNewline = if (importSectionEnd < text.length) {
                text[importSectionEnd] != '\n'
            } else {
                // Если мы в конце файла, проверяем последний символ
                text.isNotEmpty() && text[text.length - 1] != '\n'
            }
            
            val finalInsertText = if (needsNewline) {
                insertText + "\n"
            } else {
                if (importSectionEnd == 0 || (importSectionEnd > 0 && importSectionEnd <= text.length && text[importSectionEnd - 1] == '\n')) {
                    insertText
                } else {
                    insertText + "\n"
                }
            }
            
            document.insertString(importSectionEnd, finalInsertText)
            
            // Возвращаем (offset, length) - где был вставлен импорт и его длина
            return Pair(importSectionEnd, finalInsertText.length)
        }
    }
    
    private fun findImportSectionEnd(text: String): Int {
        var offset = 0
        val lines = text.lines()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || 
                trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
                offset += line.length + 1 // +1 для символа новой строки
            } else {
                break
            }
        }
        
        return offset
    }
    
    fun dispose() {
        // Освобождаем ресурсы редактора
        codeEditor?.let { editor ->
            EditorFactory.getInstance().releaseEditor(editor)
            codeEditor = null
        }
    }
    
    fun component(): JComponent {
        return mainPanel
    }
}

