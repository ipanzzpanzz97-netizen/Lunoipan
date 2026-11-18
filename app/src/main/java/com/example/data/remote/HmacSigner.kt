package com.example.data.remote

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SignedAuthHeaders(
  val authorizationBasic: String,
  val timestampMs: String,
  val hmacSha256Signature: String,
)

object HmacSigner {

  /**
   * Generates Luno Basic Auth header and deterministic HMAC-SHA256 request signature header
   * over (timestamp + HTTP method + request path) without ever logging raw secrets.
   */
  fun signRequest(
    apiKeyId: String,
    apiKeySecret: String,
    method: String,
    requestPath: String,
    timestampMs: Long = System.currentTimeMillis(),
  ): SignedAuthHeaders {
    val rawBasic = "$apiKeyId:$apiKeySecret"
    val basicToken =
      Base64.encodeToString(rawBasic.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    val ts = timestampMs.toString()
    val payload = "$ts${method.uppercase()}$requestPath"
    val mac = Mac.getInstance("HmacSHA256")
    val keySpec = SecretKeySpec(apiKeySecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    mac.init(keySpec)
    val signatureBytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
    val signatureHex = signatureBytes.joinToString(separator = "") { "%02x".format(it) }
    return SignedAuthHeaders(
      authorizationBasic = "Basic $basicToken",
      timestampMs = ts,
      hmacSha256Signature = signatureHex,
    )
  }
}
