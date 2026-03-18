package com.testviewer

import java.util.regex.Pattern

class FetchToPythonGenerator {
    private val indent = "    "
    
    fun generate(fetchText: String): String {
        // Парсим URL
        val url = extractUrl(fetchText)
        
        // Парсим метод
        val method = extractMethod(fetchText) ?: "GET"
        
        // Парсим headers
        val headers = extractHeaders(fetchText)
        
        // Парсим body
        val body = extractBody(fetchText)
        
        // Извлекаем путь из URL
        val path = extractPath(url)
        
        // Извлекаем query параметры
        val queryParams = extractQueryParams(url)
        
        // Извлекаем параметры из body
        val bodyParams = extractParams(body)
        
        // Парсим multipart body если есть
        val multipartFields = if (body != null && body.contains("Content-Disposition: form-data")) {
            parseMultipartBody(body)
        } else {
            null
        }
        
        // Генерируем имя функции
        val functionName = generateFunctionName(method, path, multipartFields)
        
        // Определяем тип возвращаемого значения
        val returnType = inferReturnType()
        
        // Генерируем Python код
        return buildPythonFunction(functionName, method, path, queryParams, bodyParams, headers, body, multipartFields, returnType)
    }
    
    private fun extractUrl(text: String): String {
        // Ищем fetch("url" или fetch('url' или просто URL в начале
        // Пробуем несколько вариантов
        var pattern = Pattern.compile("fetch\\s*\\(\\s*[\"']([^\"']+)[\"']")
        var matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        
        // Пробуем найти URL напрямую (https:// или http://)
        pattern = Pattern.compile("(https?://[^\\s\"']+)")
        matcher = pattern.matcher(text)
        if (matcher.find()) {
            val url = matcher.group(1)
            // Убираем возможные завершающие символы
            return url.trimEnd(',', ';', ')', '}')
        }
        
        throw IllegalArgumentException("Не удалось найти URL в fetch запросе. Убедитесь, что текст содержит fetch запрос с URL.")
    }
    
    private fun extractMethod(text: String): String? {
        val pattern = Pattern.compile("\"method\"\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1).uppercase()
        } else {
            null
        }
    }
    
    private fun extractHeaders(text: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        
        // Ищем блок headers - используем более сложный паттерн для обработки вложенных объектов
        val headersStart = text.indexOf("\"headers\"")
        if (headersStart == -1) return headers
        
        var braceCount = 0
        var startPos = -1
        var endPos = -1
        
        // Находим начало объекта headers
        for (i in headersStart until text.length) {
            if (text[i] == '{') {
                if (startPos == -1) startPos = i
                braceCount++
            } else if (text[i] == '}') {
                braceCount--
                if (braceCount == 0 && startPos != -1) {
                    endPos = i
                    break
                }
            }
        }
        
        if (startPos == -1 || endPos == -1) return headers
        
        val headersBlock = text.substring(startPos + 1, endPos)
        
        // Парсим каждое поле заголовка - используем более безопасный подход
        // Ищем пары "key": "value" без catastrophic backtracking
        var i = 0
        while (i < headersBlock.length) {
            // Ищем начало ключа
            val keyStart = headersBlock.indexOf('"', i)
            if (keyStart == -1) break
            
            val keyEnd = headersBlock.indexOf('"', keyStart + 1)
            if (keyEnd == -1) break
            
            val key = headersBlock.substring(keyStart + 1, keyEnd)
            
            // Ищем двоеточие
            val colonPos = headersBlock.indexOf(':', keyEnd)
            if (colonPos == -1) {
                i = keyEnd + 1
                continue
            }
            
            // Ищем начало значения
            var valueStart = colonPos + 1
            while (valueStart < headersBlock.length && headersBlock[valueStart].isWhitespace()) {
                valueStart++
            }
            
            if (valueStart >= headersBlock.length || headersBlock[valueStart] != '"') {
                i = keyEnd + 1
                continue
            }
            
            // Парсим значение с учетом экранированных символов
            val value = StringBuilder()
            var j = valueStart + 1
            var escaped = false
            
            while (j < headersBlock.length) {
                val char = headersBlock[j]
                
                if (escaped) {
                    when (char) {
                        'n' -> value.append('\n')
                        'r' -> value.append('\r')
                        't' -> value.append('\t')
                        '"' -> value.append('"')
                        '\\' -> value.append('\\')
                        else -> value.append('\\').append(char)
                    }
                    escaped = false
                } else {
                    when (char) {
                        '\\' -> escaped = true
                        '"' -> break // Конец значения
                        else -> value.append(char)
                    }
                }
                j++
            }
            
            headers[key] = value.toString()
            i = j + 1
        }
        
        return headers
    }
    
    private fun extractBody(text: String): String? {
        // Ищем body - используем более безопасный подход без catastrophic backtracking
        val bodyStart = text.indexOf("\"body\"")
        if (bodyStart == -1) return null
        
        // Ищем начало значения body (после :)
        var valueStart = -1
        for (i in bodyStart until text.length) {
            if (text[i] == ':' && valueStart == -1) {
                // Пропускаем пробелы после :
                var j = i + 1
                while (j < text.length && text[j].isWhitespace()) j++
                if (j < text.length && text[j] == '"') {
                    valueStart = j + 1
                    break
                }
            }
        }
        
        if (valueStart == -1) return null
        
        // Парсим строку с учетом экранированных символов
        val body = StringBuilder()
        var i = valueStart
        var escaped = false
        
        while (i < text.length) {
            val char = text[i]
            
            if (escaped) {
                when (char) {
                    'n' -> body.append('\n')
                    'r' -> body.append('\r')
                    't' -> body.append('\t')
                    '"' -> body.append('"')
                    '\\' -> body.append('\\')
                    else -> body.append('\\').append(char)
                }
                escaped = false
            } else {
                when (char) {
                    '\\' -> escaped = true
                    '"' -> break // Конец строки
                    else -> body.append(char)
                }
            }
            i++
        }
        
        val result = body.toString()
        return if (result.isNotEmpty()) result else null
    }
    
    private fun extractPath(url: String): String {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.path
        } catch (e: Exception) {
            val match = Pattern.compile("https?://[^/]+(/[^?]*)").matcher(url)
            if (match.find()) {
                match.group(1) ?: url.split("?")[0]
            } else {
                url.split("?")[0]
            }
        }
    }
    
    private fun extractQueryParams(url: String): Map<String, String>? {
        return try {
            val urlObj = java.net.URL(url)
            val query = urlObj.query
            if (query.isNullOrBlank()) return null
            
            val params = mutableMapOf<String, String>()
            val pairs = query.split("&")
            pairs.forEach { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.isNotEmpty()) {
                    val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                    val value = if (parts.size > 1) {
                        java.net.URLDecoder.decode(parts[1], "UTF-8")
                    } else {
                        ""
                    }
                    params[key] = value
                }
            }
            
            if (params.isEmpty()) null else params
        } catch (e: Exception) {
            val match = Pattern.compile("\\?([^#]+)").matcher(url)
            if (!match.find()) return null
            
            val params = mutableMapOf<String, String>()
            val pairs = match.group(1).split("&")
            pairs.forEach { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.isNotEmpty()) {
                    val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                    val value = if (parts.size > 1) {
                        java.net.URLDecoder.decode(parts[1], "UTF-8")
                    } else {
                        ""
                    }
                    params[key] = value
                }
            }
            
            if (params.isEmpty()) null else params
        }
    }
    
    private fun extractParams(body: String?): Map<String, Any>? {
        if (body.isNullOrBlank()) return null
        
        // Проверяем, является ли body multipart/form-data
        if (body.contains("Content-Disposition: form-data")) {
            // Для multipart возвращаем null, так как это обрабатывается отдельно
            return null
        }
        
        // Пытаемся распарсить как JSON
        try {
            // Используем простой парсер JSON для объектов
            val trimmed = body.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return parseJsonObject(trimmed)
            }
        } catch (e: Exception) {
            // Если не JSON, возвращаем null
        }
        
        return null
    }
    
    private fun parseJsonObject(json: String): Map<String, Any>? {
        // Простой парсер JSON объектов (без вложенных объектов и массивов)
        val result = mutableMapOf<String, Any>()
        val pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"?([^,\"}\\s]+)\"?")
        val matcher = pattern.matcher(json)
        
        while (matcher.find()) {
            val key = matcher.group(1)
            val value = matcher.group(2)
            result[key] = value
        }
        
        return if (result.isEmpty()) null else result
    }
    
    private data class MultipartField(
        val name: String,
        val value: String?,
        val isFile: Boolean,
        val filename: String?,
        val contentType: String?
    )
    
    private fun parseMultipartBody(body: String): List<MultipartField> {
        val fields = mutableListOf<MultipartField>()
        
        // Извлекаем boundary из body
        val boundaryPattern = Pattern.compile("------([A-Za-z0-9]+)")
        val boundaryMatcher = boundaryPattern.matcher(body)
        val boundary = if (boundaryMatcher.find()) {
            boundaryMatcher.group(1)
        } else {
            "WebKitFormBoundary"
        }
        
        // Разбиваем на части по boundary
        val parts = body.split("------$boundary")
        
        for (part in parts) {
            if (part.trim().isEmpty() || part.trim() == "--") continue
            
            // Ищем Content-Disposition
            val dispositionPattern = Pattern.compile("Content-Disposition: form-data; name=\"([^\"]+)\"(?:; filename=\"([^\"]+)\")?", Pattern.CASE_INSENSITIVE)
            val dispositionMatcher = dispositionPattern.matcher(part)
            
            if (dispositionMatcher.find()) {
                val name = dispositionMatcher.group(1)
                val filename = dispositionMatcher.group(2)
                
                // Ищем Content-Type (если есть)
                val contentTypePattern = Pattern.compile("Content-Type: ([^\\r\\n]+)", Pattern.CASE_INSENSITIVE)
                val contentTypeMatcher = contentTypePattern.matcher(part)
                val contentType = if (contentTypeMatcher.find()) contentTypeMatcher.group(1).trim() else null
                
                // Извлекаем значение (после двух переносов строк)
                val valuePattern = Pattern.compile("\\r?\\n\\r?\\n(.*?)(?=\\r?\\n------|$)", Pattern.DOTALL)
                val valueMatcher = valuePattern.matcher(part)
                val value = if (valueMatcher.find()) {
                    valueMatcher.group(1).trim()
                } else {
                    null
                }
                
                val isFile = filename != null
                fields.add(MultipartField(name, value, isFile, filename, contentType))
            }
        }
        
        return fields
    }
    
    private fun generateFunctionName(method: String, path: String, multipartFields: List<MultipartField>? = null): String {
        val methodLower = method.lowercase()
        
        // Если есть multipart с файлом, используем более понятное имя
        if (multipartFields != null) {
            val hasFile = multipartFields.any { it.isFile }
            if (hasFile) {
                // Ищем ключевые слова в пути для определения типа операции
                val pathLower = path.lowercase()
                when {
                    pathLower.contains("upload") -> return "${methodLower}_upload_file"
                    pathLower.contains("import") -> return "${methodLower}_import_file"
                    pathLower.contains("file") -> return "${methodLower}_file"
                }
            }
        }
        
        val pathParts = path.split("/").filter { it.isNotBlank() && !it.contains("?") && !it.matches(Regex("^\\d+$")) }
        val meaningfulParts = pathParts.takeLast(3)
        
        if (meaningfulParts.isEmpty()) {
            return "${methodLower}_request"
        }
        
        val name = meaningfulParts
            .map { it.replace(Regex("[^a-zA-Z0-9]"), "_").replace(Regex("_+"), "_") }
            .filter { it.isNotBlank() && it != "_" }
            .joinToString("_")
            .lowercase()
        
        return "${methodLower}_$name"
    }
    
    private fun inferReturnType(): String {
        // По умолчанию возвращаем dict
        return "dict"
    }
    
    private fun buildPythonFunction(
        functionName: String,
        method: String,
        path: String,
        queryParams: Map<String, String>?,
        bodyParams: Map<String, Any>?,
        headers: Map<String, String>,
        body: String?,
        multipartFields: List<MultipartField>?,
        returnType: String
    ): String {
        val sb = StringBuilder()
        val methodLower = method.lowercase()
        val hasBodyParams = bodyParams != null && bodyParams.isNotEmpty()
        
        // Определяем content-type из headers
        val contentType = headers["content-type"]?.lowercase() ?: headers["Content-Type"]?.lowercase() ?: ""
        val isMultipart = contentType.contains("multipart/form-data")
        val isJson = contentType.contains("application/json")
        
        // Генерируем параметры функции
        val functionParams = buildFunctionParameters(multipartFields, hasBodyParams, methodLower, isJson)
        
        // Генерируем сигнатуру функции
        if (multipartFields != null && multipartFields.isNotEmpty()) {
            // Для multipart используем многострочный формат параметров
            sb.append("def $functionName(\n")
            sb.append("$functionParams\n")
            sb.append(") -> $returnType:\n")
        } else {
            sb.append("def $functionName($functionParams) -> $returnType:\n")
        }
        sb.append("$indent\"\"\":TODO: Заполнить описание функции, поменять параметры\n")
        sb.append("\n")
        
        // Генерируем docstring с параметрами
        sb.append("$indent:param client: клиент")
        if (multipartFields != null && multipartFields.isNotEmpty()) {
            multipartFields.forEach { field ->
                when {
                    field.isFile -> {
                        sb.append("\n$indent:param file_name: имя файла для загрузки")
                        sb.append("\n$indent:param file_path: абсолютный путь к файлу для загрузки")
                    }
                    field.name.lowercase().contains("organization") || field.name.lowercase().contains("org") -> {
                        sb.append("\n$indent:param org_id: идентификатор организации")
                    }
                    field.name.lowercase().contains("comment") -> {
                        sb.append("\n$indent:param comment: комментарий")
                    }
                    else -> {
                        val paramName = field.name.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
                        sb.append("\n$indent:param $paramName: ${field.name}")
                    }
                }
            }
        } else if (hasBodyParams && methodLower != "get" && !isJson) {
            sb.append("\n$indent:param payload: данные для запроса")
        }
        
        // Добавляем информацию о multipart/form-data в docstring
        if (isMultipart && body != null) {
            sb.append("\n")
            sb.append("\n$indent")
            sb.append("Обработайте Multipart/form-data = ")
            // Ограничиваем длину body в docstring (первые 500 символов)
            val bodyPreview = if (body.length > 500) {
                body.take(500) + "..."
            } else {
                body
            }
            // Для docstring просто добавляем body как есть (Python docstring поддерживает многострочный текст)
            sb.append(bodyPreview)
        }
        
        sb.append("\n")
        sb.append("\n")
        sb.append("$indent:return: ответ сервера\"\"\"\n")
        
        // Генерируем тело функции
        val functionBody = generateFunctionBody(methodLower, path, queryParams, bodyParams, headers, body, isJson, multipartFields)
        sb.append(functionBody)
        
        return sb.toString()
    }
    
    private fun buildFunctionParameters(
        multipartFields: List<MultipartField>?,
        hasBodyParams: Boolean,
        methodLower: String,
        isJson: Boolean
    ): String {
        if (multipartFields != null && multipartFields.isNotEmpty()) {
            val params = mutableListOf<String>("    client: Client")
            
            // Добавляем параметры на основе multipart полей
            // Сначала файл, потом остальные поля
            var hasFile = false
            val otherFields = mutableListOf<String>()
            
            multipartFields.forEach { field ->
                when {
                    field.isFile -> {
                        if (!hasFile) {
                            params.add("    file_name: str")
                            params.add("    file_path: str")
                            hasFile = true
                        }
                    }
                    field.name.lowercase().contains("organization") || field.name.lowercase().contains("org") -> {
                        otherFields.add("    org_id: int")
                    }
                    field.name.lowercase().contains("comment") -> {
                        otherFields.add("    comment: str")
                    }
                    else -> {
                        val paramName = field.name.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
                        otherFields.add("    $paramName: str")
                    }
                }
            }
            
            // Добавляем остальные поля после файла
            params.addAll(otherFields)
            
            return params.joinToString(",\n")
        }
        
        // Для POST с JSON не добавляем payload в параметры, так как он будет определен в теле функции
        if (hasBodyParams && methodLower != "get" && !isJson) {
            return "client: Client, payload: dict = None"
        }
        
        return "client: Client"
    }
    
    private fun generateFunctionBody(
        method: String,
        path: String,
        queryParams: Map<String, String>?,
        bodyParams: Map<String, Any>?,
        headers: Map<String, String>,
        body: String?,
        isJson: Boolean,
        multipartFields: List<MultipartField>?
    ): String {
        val sb = StringBuilder()
        val hasQueryParams = queryParams != null && queryParams.isNotEmpty()
        val hasBodyParams = bodyParams != null && bodyParams.isNotEmpty()
        val hasJsonBody = isJson && body != null && body.isNotBlank()
        
        // Обработка multipart/form-data
        if (multipartFields != null && multipartFields.isNotEmpty()) {
            // Генерируем boundary
            sb.append("\n")
            sb.append("${indent}boundary = \"--------------------------171850036673128317576542\"\n")
            
            // Генерируем upload_headers
            sb.append("${indent}upload_headers = {\n")
            // Извлекаем authorization из исходных headers
            val authHeader = headers["authorization"] ?: headers["Authorization"]
            if (authHeader != null) {
                sb.append("$indent$indent\"Authorization\": client.session.headers[\"Authorization\"],\n")
            }
            sb.append("$indent$indent\"Content-Type\": f\"multipart/form-data; boundary={boundary}\",\n")
            sb.append("$indent$indent\"Accept-Encoding\": \"gzip, deflate, br\",\n")
            sb.append("$indent$indent\"Accept\": \"*/*\",\n")
            sb.append("$indent}\n")
            sb.append("\n")
            
            sb.append("${indent}suffix_url = \"$path\"\n")
            sb.append("\n")
            
            // Генерируем params с files и data
            sb.append("${indent}params = {\n")
            multipartFields.forEachIndexed { index, field ->
                val isLast = index == multipartFields.size - 1
                when {
                    field.isFile -> {
                        // Для файла используем кортеж (filename, open(file_path, "rb"), content_type)
                        val contentType = field.contentType ?: "text/csv"
                        sb.append("$indent$indent\"${field.name}\": (file_name, open(file_path, \"rb\"), \"$contentType\"),\n")
                    }
                    field.name.lowercase().contains("organization") || field.name.lowercase().contains("org") -> {
                        // Для organizationId используем org_id
                        sb.append("$indent$indent\"${field.name}\": (None, str(org_id)),\n")
                    }
                    field.name.lowercase().contains("comment") -> {
                        // Для comment используем comment
                        sb.append("$indent$indent\"${field.name}\": (None, comment),\n")
                    }
                    else -> {
                        val paramName = field.name.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
                        sb.append("$indent$indent\"${field.name}\": (None, $paramName),\n")
                    }
                }
            }
            sb.append("$indent}\n")
            sb.append("\n")
            
            // Генерируем вызов client.post с files
            sb.append("${indent}return client.post(\n")
            sb.append("$indent$indent url=suffix_url, headers=upload_headers, files=params\n")
            sb.append("$indent)\n")
            
            return sb.toString()
        }
        
        sb.append("${indent}suffix_url = \"$path\"\n")
        
        // Если POST с application/json, создаем переменную payload с содержимым body
        if (hasJsonBody && method == "post") {
            // Используем тройные кавычки для многострочных строк
            // Экранируем только обратные слеши и кавычки внутри
            val escapedBody = body.replace("\\", "\\\\")
                .replace("\"\"\"", "\\\"\\\"\\\"")
            sb.append("${indent}payload = \"\"\"$escapedBody\"\"\"\n")
        }
        
        when (method) {
            "get" -> {
                if (hasQueryParams) {
                    sb.append(generateParamsVariable(queryParams, "params"))
                    sb.append("${indent}response = client.get(url=suffix_url, params=params)\n")
                } else {
                    sb.append("${indent}response = client.get(url=suffix_url)\n")
                }
                sb.append("${indent}return response")
            }
            "post", "put", "patch" -> {
                if (hasQueryParams) {
                    sb.append(generateParamsVariable(queryParams, "params"))
                }
                
                if (hasBodyParams || hasJsonBody) {
                    if (hasQueryParams) {
                        if (hasJsonBody) {
                            // Для JSON используем data вместо json, так как payload это строка
                            sb.append("${indent}response = client.$method(url=suffix_url, params=params, data=payload, headers={\"Content-Type\": \"application/json\"})\n")
                        } else {
                            sb.append("${indent}response = client.$method(url=suffix_url, params=params, json=payload or {})\n")
                        }
                    } else {
                        if (hasJsonBody) {
                            // Для JSON используем data вместо json, так как payload это строка
                            sb.append("${indent}response = client.$method(url=suffix_url, data=payload, headers={\"Content-Type\": \"application/json\"})\n")
                        } else {
                            sb.append("${indent}response = client.$method(url=suffix_url, json=payload or {})\n")
                        }
                    }
                } else {
                    if (hasQueryParams) {
                        sb.append("${indent}response = client.$method(url=suffix_url, params=params)\n")
                    } else {
                        sb.append("${indent}response = client.$method(url=suffix_url)\n")
                    }
                }
                sb.append("${indent}return response")
            }
            "delete" -> {
                if (hasQueryParams) {
                    sb.append(generateParamsVariable(queryParams, "params"))
                    sb.append("${indent}response = client.delete(url=suffix_url, params=params)\n")
                } else {
                    sb.append("${indent}response = client.delete(url=suffix_url)\n")
                }
                sb.append("${indent}return response")
            }
            else -> {
                if (hasQueryParams) {
                    sb.append(generateParamsVariable(queryParams, "params"))
                    sb.append("${indent}response = client.request(method=\"${method.uppercase()}\", url=suffix_url, params=params")
                    if (hasJsonBody) {
                        sb.append(", data=payload, headers={\"Content-Type\": \"application/json\"}")
                    } else if (hasBodyParams) {
                        sb.append(", json=payload or {}")
                    }
                    sb.append(")\n")
                } else {
                    sb.append("${indent}response = client.request(method=\"${method.uppercase()}\", url=suffix_url")
                    if (hasJsonBody) {
                        sb.append(", data=payload, headers={\"Content-Type\": \"application/json\"}")
                    } else if (hasBodyParams) {
                        sb.append(", json=payload or {}")
                    }
                    sb.append(")\n")
                }
                sb.append("${indent}return response")
            }
        }
        
        return sb.toString()
    }
    
    private fun generateParamsVariable(params: Map<String, String>, varName: String): String {
        val sb = StringBuilder()
        sb.append("$indent$varName = {\n")
        val entries = params.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            val isLast = index == entries.size - 1
            val valueStr = "\"$value\""
            sb.append("$indent$indent\"$key\": $valueStr${if (isLast) "" else ","}\n")
        }
        sb.append("$indent}\n")
        return sb.toString()
    }
}
