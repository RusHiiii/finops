package com.finops.app.data.remote

import com.finops.app.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

class HttpResponse(val code: Int, val body: String)

/** Client HTTP minimal, abstrait pour pouvoir tester les sources de données sans réseau. */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResponse
}

class UrlConnectionHttpClient @Inject constructor(
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : HttpClient {

    override suspend fun get(url: String, headers: Map<String, String>) = request("GET", url, headers, null)

    override suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>): HttpResponse {
        val body = form.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return request("POST", url, headers + ("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8"), body)
    }

    private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())

    private suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) =
        withContext(io) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                conn.disconnect()
            }
        }
}
