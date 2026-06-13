package com.timedrecorder.core.network.interceptor

import com.timedrecorder.core.datastore.PreferencesDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态注入 Authorization Bearer Token。
 * 未配置 API Key 时不添加该请求头。
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = runBlocking {
            preferencesDataSource.userPreferences.first().apiKey
        }
        val request = if (apiKey.isNotBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
