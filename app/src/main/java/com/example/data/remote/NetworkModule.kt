package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {

  private const val LUNO_BASE_URL = "https://api.luno.com/"
  private const val COINGECKO_BASE_URL = "https://api.coingecko.com/"

  private val moshi: Moshi by lazy {
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }

  private val retryInterceptor = Interceptor { chain ->
    val request = chain.request()
    var attempt = 0
    var lastException: IOException? = null
    while (attempt < 3) {
      try {
        val response: Response = chain.proceed(request)
        if (response.isSuccessful || response.code in 400..499) {
          return@Interceptor response
        }
        response.close()
      } catch (e: IOException) {
        lastException = e
      }
      attempt++
      if (attempt < 3) {
        try {
          Thread.sleep(250L * attempt)
        } catch (_: InterruptedException) {
          break
        }
      }
    }
    throw lastException ?: IOException("Network request failed after 3 attempts")
  }

  private val loggingInterceptor: HttpLoggingInterceptor by lazy {
    HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
      redactHeader("Authorization")
      redactHeader("X-HMAC-SHA256-Signature")
    }
  }

  private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .addInterceptor(retryInterceptor)
      .addInterceptor(loggingInterceptor)
      .build()
  }

  val lunoApiService: LunoApiService by lazy {
    Retrofit.Builder()
      .baseUrl(LUNO_BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(LunoApiService::class.java)
  }

  val coinGeckoApiService: CoinGeckoApiService by lazy {
    Retrofit.Builder()
      .baseUrl(COINGECKO_BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(CoinGeckoApiService::class.java)
  }
}
