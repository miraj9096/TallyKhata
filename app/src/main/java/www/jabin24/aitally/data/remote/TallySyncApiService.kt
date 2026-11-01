package www.jabin24.aitally.data.remote

import www.jabin24.aitally.data.remote.models.ApiResponse
import www.jabin24.aitally.data.remote.models.LoginRequest
import www.jabin24.aitally.data.remote.models.RegisterRequest
import www.jabin24.aitally.data.remote.models.SyncPushRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TallySyncApiService {

    @POST("api/tally_sync.php")
    suspend fun registerUser(
        @Body request: RegisterRequest
    ): Response<ApiResponse>

    @POST("api/tally_sync.php")
    suspend fun loginUser(
        @Body request: LoginRequest
    ): Response<ApiResponse>

    @POST("api/tally_sync.php")
    suspend fun pushSync(
        @Body request: SyncPushRequest
    ): Response<ApiResponse>

    @GET("api/tally_sync.php")
    suspend fun pullSync(
        @Query("action") action: String = "pull",
        @Query("user_uid") userUid: String
    ): Response<ApiResponse>

    @POST("api/tally_sync.php")
    suspend fun sendVerificationCode(
        @Body request: www.jabin24.aitally.data.remote.models.SendCodeRequest
    ): Response<ApiResponse>

    @POST("api/tally_sync.php")
    suspend fun verifyEmailCode(
        @Body request: www.jabin24.aitally.data.remote.models.VerifyCodeRequest
    ): Response<ApiResponse>

    @GET("api/tally_sync.php")
    suspend fun healthCheck(): Response<ApiResponse>
}
