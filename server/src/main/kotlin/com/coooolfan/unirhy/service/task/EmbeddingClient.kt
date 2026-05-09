package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.AiModelConfig
import com.coooolfan.unirhy.model.AiRequestFormat
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class EmbeddingClient(
    private val objectMapper: ObjectMapper,
) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun embed(config: AiModelConfig, texts: List<String>): List<FloatArray> {
        val requestBody = when (config.requestFormat) {
            AiRequestFormat.JINA -> jinaRequestBody(config.model, texts)
            AiRequestFormat.OPENAI -> openAiRequestBody(config.model, texts)
            else -> error("Unsupported request format for embedding: ${config.requestFormat}. Only JINA and OPENAI are supported.")
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${config.key}")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(120))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            error("Embedding API error (${response.statusCode()}): ${response.body().take(500)}")
        }

        return parseDataEmbeddings(response.body())
    }

    fun embedOne(config: AiModelConfig, text: String): FloatArray {
        return embed(config, listOf(text)).firstOrNull()
            ?: error("Embedding API response did not contain any embeddings")
    }

    private fun jinaRequestBody(modelName: String, texts: List<String>): String {
        return objectMapper.writeValueAsString(
            mapOf(
                "model" to modelName,
                "task" to "retrieval.query",
                "normalized" to true,
                "input" to texts,
            )
        )
    }

    private fun openAiRequestBody(modelName: String, texts: List<String>): String {
        return objectMapper.writeValueAsString(
            mapOf(
                "model" to modelName,
                "input" to texts,
                "encoding_format" to "float",
            )
        )
    }

    private fun parseDataEmbeddings(responseBody: String): List<FloatArray> {
        val responseJson = objectMapper.readTree(responseBody)
        val dataArray = responseJson["data"]
            ?: error("Embedding API response missing 'data' field")

        return (0 until dataArray.size())
            .map { i -> dataArray[i] }
            .sortedBy { it["index"]?.intValue() ?: 0 }
            .map { item ->
                val embArr = item["embedding"]
                    ?: error("Embedding API response missing 'embedding' in data entry")
                FloatArray(embArr.size()) { i -> embArr[i].floatValue() }
            }
    }
}
