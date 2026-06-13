package com.timedrecorder.core.network

import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.network.interceptor.AuthInterceptor
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态 Base URL 的 API 提供者。
 * 每次调用时读取最新 Base URL，确保设置变更后生效。
 */
@Singleton
class AudioApiProvider @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val authInterceptor: AuthInterceptor,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
        if (BuildConfig.ALLOW_INSECURE_SSL) {
            InsecureSslHelper.apply(builder)
        }
        builder.build()
    }

    /** 获取当前配置下的 API 服务实例 */
    suspend fun getApiService(): AudioApiService {
        val prefs = preferencesDataSource.userPreferences.first()
        val baseUrl = normalizeBaseUrl(prefs.baseUrl)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AudioApiService::class.java)
    }

    private fun normalizeBaseUrl(url: String): String = when {
        url.isBlank() -> "https://placeholder.invalid/"
        url.endsWith("/") -> url
        else -> "$url/"
    }
}
