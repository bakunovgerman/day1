package com.example.day1.api

import android.util.Log
import com.example.day1.data.MessageContent
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
import kotlinx.serialization.json.Json

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

    suspend fun sendMessage(
        messages: List<MessageContent>,
        apiKey: String
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
                        messages = messages
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

    fun close() {
        client.close()
    }
}
