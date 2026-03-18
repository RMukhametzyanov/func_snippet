package com.testviewer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

class FetchToPythonDialog(private val project: Project, private val targetFile: VirtualFile? = null) : DialogWrapper(project) {
    private val inputTextArea = JTextArea()
    private val outputTextArea = JTextArea()
    private val LOG = Logger.getInstance(FetchToPythonDialog::class.java)
    private var generatedCode: String = ""
    private var addToFileButton: JButton? = null
    
    init {
        title = "Генерация Python функции из fetch запроса"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(800, 600)
        
        // Входной текст
        val inputLabel = JLabel("Вставьте fetch запрос из браузерной консоли:")
        inputTextArea.font = java.awt.Font("Consolas", java.awt.Font.PLAIN, 12)
        inputTextArea.lineWrap = true
        inputTextArea.wrapStyleWord = true
        val inputScrollPane = JBScrollPane(inputTextArea)
        inputScrollPane.preferredSize = Dimension(800, 250)
        
        // Кнопка генерации
        val generateButton = JButton("Сгенерировать Python функцию")
        generateButton.addActionListener { e ->
            LOG.info("Кнопка 'Сгенерировать Python функцию' нажата")
            generatePythonFunction()
        }
        
        // Выходной текст
        val outputLabel = JLabel("Сгенерированная Python функция:")
        outputTextArea.font = java.awt.Font("Consolas", java.awt.Font.PLAIN, 12)
        outputTextArea.isEditable = false
        outputTextArea.lineWrap = true
        outputTextArea.wrapStyleWord = true
        val outputScrollPane = JBScrollPane(outputTextArea)
        outputScrollPane.preferredSize = Dimension(800, 250)
        
        // Кнопка копирования
        val copyButton = JButton("Копировать")
        copyButton.addActionListener {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(outputTextArea.text)
            clipboard.setContents(selection, null)
        }
        
        // Кнопка добавления в конец файла
        if (targetFile != null) {
            addToFileButton = JButton("Добавить в конец файла")
            addToFileButton!!.isEnabled = false // Включается только после генерации
            addToFileButton!!.addActionListener {
                addToEndOfFile()
            }
        }
        
        // Панель для кнопок
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        buttonsPanel.add(generateButton)
        buttonsPanel.add(copyButton)
        addToFileButton?.let { buttonsPanel.add(it) }
        buttonsPanel.border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        
        // Верхняя панель
        val topPanel = JPanel(BorderLayout(5, 5))
        topPanel.add(inputLabel, BorderLayout.NORTH)
        topPanel.add(inputScrollPane, BorderLayout.CENTER)
        topPanel.add(buttonsPanel, BorderLayout.SOUTH)
        
        // Нижняя панель
        val bottomPanel = JPanel(BorderLayout(5, 5))
        bottomPanel.add(outputLabel, BorderLayout.NORTH)
        bottomPanel.add(outputScrollPane, BorderLayout.CENTER)
        
        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(bottomPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun generatePythonFunction() {
        try {
            val fetchText = inputTextArea.text.trim()
            LOG.info("Начало генерации. Длина текста: ${fetchText.length}")
            
            if (fetchText.isEmpty()) {
                outputTextArea.text = "Ошибка: Введите fetch запрос"
                LOG.warn("Текст fetch запроса пуст")
                return
            }
            
            LOG.debug("Текст fetch запроса (первые 500 символов):\n${fetchText.take(500)}")
            
            val generator = FetchToPythonGenerator()
            val pythonCode = generator.generate(fetchText)
            
            LOG.info("Генерация завершена успешно, длина кода: ${pythonCode.length}")
            generatedCode = pythonCode
            outputTextArea.text = pythonCode
            
            // Включаем кнопку добавления в файл, если файл выбран
            if (addToFileButton != null && targetFile != null) {
                addToFileButton!!.isEnabled = true
            }
            
            // Прокручиваем к началу
            outputTextArea.caretPosition = 0
        } catch (e: Exception) {
            LOG.error("Ошибка при генерации Python функции", e)
            val errorMessage = buildString {
                append("Ошибка при генерации: ${e.message}\n\n")
                append("Тип ошибки: ${e.javaClass.simpleName}\n\n")
                append("Детали:\n")
                e.stackTrace.take(10).forEach { 
                    append("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})\n")
                }
                if (e.cause != null) {
                    append("\nПричина: ${e.cause?.message}\n")
                }
            }
            outputTextArea.text = errorMessage
            outputTextArea.caretPosition = 0
        }
    }
    
    private fun addToEndOfFile() {
        if (targetFile == null || generatedCode.isEmpty()) {
            return
        }
        
        try {
            // Копируем сгенерированный код в буфер обмена
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(generatedCode)
            clipboard.setContents(selection, null)
            
            // Открываем файл
            val fileEditorManager = FileEditorManager.getInstance(project)
            val editor = fileEditorManager.openTextEditor(
                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, targetFile!!),
                true
            ) ?: run {
                LOG.error("Не удалось открыть редактор для файла: ${targetFile!!.name}")
                outputTextArea.text = "Ошибка: Не удалось открыть файл\n\n${outputTextArea.text}"
                return
            }
            
            // Выполняем вставку в WriteCommandAction
            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                
                // Перемещаем курсор в самый конец файла
                val endOffset = document.textLength
                editor.caretModel.moveToOffset(endOffset)
                
                // Добавляем 2 переноса строки перед вставкой кода
                val currentText = document.text
                val newlines = "\n\n" // Всегда используем \n для Python файлов
                
                val textToAdd = when {
                    currentText.isEmpty() -> newlines
                    !currentText.endsWith("\n") && !currentText.endsWith("\r\n") && !currentText.endsWith("\r") -> 
                        newlines
                    else -> "\n" // Если уже есть перенос, добавляем еще один
                }
                
                document.insertString(endOffset, textToAdd)
                editor.caretModel.moveToOffset(endOffset + textToAdd.length)
            }
            
            // Выполняем вставку из буфера обмена через стандартное действие Paste
            val pasteActionHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_PASTE)
            if (pasteActionHandler != null) {
                WriteCommandAction.runWriteCommandAction(project) {
                    pasteActionHandler.execute(editor, null, com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT)
                }
            } else {
                // Если не удалось получить обработчик, используем прямой метод вставки из буфера
                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    val endOffset = document.textLength
                    editor.caretModel.moveToOffset(endOffset)
                    val clipboardContent = clipboard.getContents(null)
                    if (clipboardContent != null && clipboardContent.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                        val text = clipboardContent.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
                        document.insertString(endOffset, text)
                        editor.caretModel.moveToOffset(endOffset + text.length)
                    }
                }
            }
            
            // Прокручиваем к курсору
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            
            // Закрываем диалог
            close(OK_EXIT_CODE)
        } catch (e: Exception) {
            LOG.error("Ошибка при добавлении функции в файл", e)
            outputTextArea.text = "Ошибка при добавлении в файл: ${e.message}\n\n${outputTextArea.text}"
        }
    }
}

