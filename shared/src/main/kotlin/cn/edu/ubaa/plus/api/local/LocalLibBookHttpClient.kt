package cn.edu.ubaa.plus.api.local

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// booking.lib.buaa.edu.cn currently presents a chain that is not trusted by Android;
// keep the relaxed TLS handling scoped to the library booking client.
internal fun getLibBookHttpClientEngine(): HttpClientEngine {
  val trustManager =
      object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
      }
  val sslContext =
      SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
      }
  return OkHttp.create {
    config {
      sslSocketFactory(sslContext.socketFactory, trustManager)
      hostnameVerifier { _, _ -> true }
    }
  }
}
