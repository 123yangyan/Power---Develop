package com.timedrecorder.core.network

import com.timedrecorder.core.network.dto.ApiResponse
import com.timedrecorder.core.network.dto.AudioResultDto
import com.timedrecorder.core.network.dto.BatchResultRequest
import com.timedrecorder.core.network.dto.BatchResultResponseData
import com.timedrecorder.core.network.dto.UploadResponseData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * 音频相关 REST API，对应 PRD §14.1–14.3。
 */
interface AudioApiService {
    /** 上传录音切片 */
    @Multipart
    @POST("api/audio/upload")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("fileName") fileName: RequestBody,
        @Part("format") format: RequestBody,
        @Part("startTime") startTime: RequestBody,
        @Part("endTime") endTime: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part("deviceId") deviceId: RequestBody,
        @Part("localId") localId: RequestBody,
    ): ApiResponse<UploadResponseData>

    /** 查询单个文件处理结果 */
    @GET("api/audio/result")
    suspend fun getResult(
        @Query("fileId") fileId: String,
    ): ApiResponse<AudioResultDto>

    /** 批量查询处理结果 */
    @POST("api/audio/result/batch")
    suspend fun batchResult(
        @retrofit2.http.Body request: BatchResultRequest,
    ): ApiResponse<BatchResultResponseData>
}
