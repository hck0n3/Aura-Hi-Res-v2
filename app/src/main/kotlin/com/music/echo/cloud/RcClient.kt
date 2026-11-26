package iad1tya.echo.music.cloud

import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Registry row 888 — talks to the rclone daemon on 127.0.0.1 with the random basic-auth pair it was started with.
 * Blocking: call it off the main thread. [RcAllowList] is enforced here, before any request is built.
 * Never logs: request and error bodies can name paths, remotes and (in an rc error) echo the whole input.
 */
class RcClient(private val port: Int, user: String, pass: String) {
    private val authorization = Credentials.basic(user, pass)

    // NO_PROXY: a proxy the user set for the catalog (row 864) must never see loopback traffic or its credentials.
    private val http = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun call(call: RcCall): JSONObject {
        RcAllowList.require(call.method)
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/${call.method}")
            .header("Authorization", authorization)
            .post(call.body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw RcException(response.code, RcResponses.errorMessage(text))
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/**
 * An rc call rclone answered with an error. The exception message carries only the HTTP status; [rcError] (rclone's own
 * text, which may quote a path or a provider message) is for classification only and is never logged.
 */
class RcException(val status: Int, val rcError: String) : IOException("rc error $status")
