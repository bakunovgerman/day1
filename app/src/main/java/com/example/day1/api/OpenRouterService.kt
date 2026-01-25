package com.example.day1.api

import android.util.Log
import com.example.day1.data.AIModel
import com.example.day1.data.MessageContent
import com.example.day1.data.ModelResponse
import com.example.day1.data.OpenRouterRequest
import com.example.day1.data.OpenRouterResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

class OpenRouterService {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("KtorClient", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    // Список доступных моделей с ценами (можно расширять)
    private val availableModels = listOf(
        AIModel(
            id = "openai/gpt-4o-mini",
            displayName = "GPT-4o-mini",
            costPerMillionPromptTokens = 0.15,
            costPerMillionCompletionTokens = 0.6
        ),
    )

    fun getAvailableModels(): List<AIModel> = availableModels

    suspend fun sendMessage(
        messages: List<MessageContent>,
        apiKey: String,
        temperature: Double = 1.0
    ): Result<String> {
        return try {
            val response: OpenRouterResponse = client.post("https://openrouter.ai/api/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $apiKey")
                    append("HTTP-Referer", "com.example.day1")
                    append("X-Title", "Day1 Chat App")
                }
                setBody(
                    OpenRouterRequest(
                        model = "deepseek/deepseek-v3.2",
                        messages = messages,
                        temperature = temperature
                    )
                )
            }.body()

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else if (response.choices.isNullOrEmpty()) {
                Result.failure(Exception("Пустой ответ от сервера"))
            } else {
                Result.success(response.choices[0].message.content)
            }
        } catch (e: Exception) {
            Log.e("OpenRouterService", "Error sending message", e)
            Result.failure(e)
        }
    }

    // Отправка запроса к нескольким моделям одновременно
    suspend fun sendMessageToMultipleModels(
        messages: List<MessageContent>,
        apiKey: String,
        models: List<AIModel>,
        temperature: Double = 1.0
    ): List<Result<ModelResponse>> = coroutineScope {
        models.map { model ->
            async {
                sendMessageToModel(messages, apiKey, model, temperature)
            }
        }.awaitAll()
    }

    // Отправка запроса к одной модели с метриками
    private suspend fun sendMessageToModel(
        messages: List<MessageContent>,
        apiKey: String,
        model: AIModel,
        temperature: Double
    ): Result<ModelResponse> {
        return try {
            var response: OpenRouterResponse? = null
            val responseTime = measureTimeMillis {
                response = client.post("https://openrouter.ai/api/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append("Authorization", "Bearer $apiKey")
                        append("HTTP-Referer", "com.example.day1")
                        append("X-Title", "Day1 Chat App")
                    }
                    setBody(
                        OpenRouterRequest(
                            model = model.id,
                            messages = messages,
                            temperature = temperature
                        )
                    )
                }.body()
            }

            val resp = response!!
            
            if (resp.error != null) {
                Result.failure(Exception(resp.error.message))
            } else if (resp.choices.isNullOrEmpty()) {
                Result.failure(Exception("Пустой ответ от сервера"))
            } else {
                val usage = resp.usage
                val promptTokens = usage?.prompt_tokens ?: 0
                val completionTokens = usage?.completion_tokens ?: 0
                val totalTokens = usage?.total_tokens ?: (promptTokens + completionTokens)
                val cost = usage?.cost ?: 0.0

                // Логирование статистики токенов
                Log.d("OpenRouterService", """
                    Модель: ${model.displayName}
                    Время ответа: ${responseTime}ms
                    Токены запроса (prompt): $promptTokens
                    Токены ответа (completion): $completionTokens
                    Всего токенов: $totalTokens
                    Стоимость: $$cost
                """.trimIndent())

                Result.success(
                    ModelResponse(
                        modelName = model.displayName,
                        content = resp.choices[0].message.content,
                        responseTimeMs = responseTime,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        cost = cost
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("OpenRouterService", "Error sending message to ${model.displayName}", e)
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
