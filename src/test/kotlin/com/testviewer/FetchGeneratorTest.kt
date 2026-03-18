package com.testviewer

import org.junit.Test
import java.io.File

class FetchGeneratorTest {
    
    @Test
    fun testGenerateFromExamples() {
        // Пробуем разные пути к файлу
        val possiblePaths = listOf(
            "example/fetch_examples.txt",
            "../example/fetch_examples.txt",
            "../../example/fetch_examples.txt"
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
    
    @Test
    fun testGenerateFromExampleAndExpected() {
        val exampleFile = File("example/example_and_expected")
        if (!exampleFile.exists()) {
            println("Файл example/example_and_expected не найден!")
            return
        }
        
        val exampleText = exampleFile.readText()
        
        // Извлекаем fetch запрос (до первого пустого блока)
        val fetchRequest = exampleText.split("\n\n").firstOrNull { it.startsWith("fetch") }
        
        if (fetchRequest == null) {
            println("Не найден fetch запрос в файле")
            return
        }
        
        println("Найден fetch запрос, длина: ${fetchRequest.length}")
        
        val generator = FetchToPythonGenerator()
        
        try {
            val pythonCode = generator.generate(fetchRequest)
            
            val outputFile = File(exampleFile.parentFile, "generated_from_example_and_expected.py")
            outputFile.writeText(pythonCode)
            println("Результат сохранен в ${outputFile.absolutePath}")
            println("\nСгенерированный код:\n$pythonCode")
        } catch (e: Exception) {
            println("Ошибка при генерации: ${e.message}")
            e.printStackTrace()
        }
    }
}
