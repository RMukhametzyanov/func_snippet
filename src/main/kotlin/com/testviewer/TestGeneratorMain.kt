package com.testviewer

import java.io.File

fun main(args: Array<String>) {
    // Пробуем разные пути к файлу
    val possiblePaths = listOf(
        "example/fetch_examples.txt",
        "../example/fetch_examples.txt",
        "../../example/fetch_examples.txt",
        File(".").absolutePath + "/example/fetch_examples.txt"
    )
    
    var examplesFile: File? = null
    for (path in possiblePaths) {
        val file = File(path)
        if (file.exists()) {
            examplesFile = file
            break
        }
    }
    
    if (examplesFile == null) {
        println("Файл fetch_examples.txt не найден!")
        println("Текущая директория: ${File(".").absolutePath}")
        possiblePaths.forEach { println("  Проверен: $it") }
        return
    }
    
    val outputFile = File(examplesFile.parentFile, "generated_functions.py")
    println("Используется файл: ${examplesFile.absolutePath}")
    
    val examplesText = examplesFile.readText()
    
    // Разделяем на отдельные fetch запросы
    val fetchRequests = mutableListOf<String>()
    val lines = examplesText.lines()
    var currentRequest = StringBuilder()
    
    for (line in lines) {
        currentRequest.append(line).append("\n")
        // Если строка заканчивается на });, это конец запроса
        if (line.trim().endsWith("});")) {
            val request = currentRequest.toString().trim()
            if (request.isNotEmpty() && request.startsWith("fetch")) {
                fetchRequests.add(request)
            }
            currentRequest = StringBuilder()
        }
    }
    
    // Если остался незавершенный запрос
    if (currentRequest.toString().trim().isNotEmpty()) {
        val request = currentRequest.toString().trim()
        if (request.startsWith("fetch")) {
            fetchRequests.add(request)
        }
    }
    
    println("Найдено ${fetchRequests.size} fetch запросов")
    
    // Создаем генератор
    val generator = FetchToPythonGenerator()
    
    val output = StringBuilder()
    output.append("# Сгенерированные Python функции из fetch запросов\n")
    output.append("# Автоматически сгенерировано\n\n")
    
    fetchRequests.forEachIndexed { index, fetchRequest ->
        try {
            output.append("# ============================================\n")
            output.append("# Пример ${index + 1}\n")
            output.append("# ============================================\n\n")
            
            val pythonCode = generator.generate(fetchRequest)
            output.append(pythonCode)
            output.append("\n\n")
            
            println("✓ Пример ${index + 1} обработан успешно")
        } catch (e: Exception) {
            output.append("# ОШИБКА при обработке примера ${index + 1}:\n")
            output.append("# ${e.message}\n")
            output.append("# ${e.stackTraceToString()}\n\n")
            println("✗ Ошибка в примере ${index + 1}: ${e.message}")
            e.printStackTrace()
        }
    }
    
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(output.toString())
    println("\nРезультаты сохранены в ${outputFile.absolutePath}")
}

