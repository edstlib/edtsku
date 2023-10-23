package id.co.edtslib.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.MalformedJsonException
import id.co.edtslib.data.source.remote.network.AuthInterceptor
import id.co.edtslib.data.source.remote.response.ApiContentResponse
import id.co.edtslib.data.source.remote.response.ApiResponse
import id.co.edtslib.util.ErrorMessage
import okio.BufferedSource
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset

/**
 * Abstract Base Data source class with error handling
 */
abstract class BaseDataSource {

    protected suspend fun <T> getResult(call: suspend () -> Response<T>): Result<T> {
        try {
            val response = call()
            val code = response.code()
            if (response.isSuccessful) {
                val body = response.body()
                return if (body != null) {
                    if (body is ApiResponse<*>) {
                        if (body.isSuccess()) {
                            Result.success(body)
                        }
                        else  if (body is ApiContentResponse<*>) {
                            if (body.isSuccess()) {
                                Result.success(body)
                            } else {
                                Result.error(body.status, body.message, body.data?.content as T?,
                                    response.raw().request.url.toString())
                            }
                        }
                        else {
                            Result.error(body.status, body.message, body.data as T?,
                                response.raw().request.url.toString())
                        }
                    } else {
                        Result.success(body)
                    }
                } else {
                    Result.error("BODYNULL", ErrorMessage().connection(), null,
                        response.raw().request.url.toString())
                }
            }
            else {
                if (code == 401) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    (return if (response.errorBody() != null) {
                        val bufferedSource: BufferedSource = response.errorBody()!!.source()
                        bufferedSource.request(Long.MAX_VALUE) // Buffer the entire body.

                        val json =
                            bufferedSource.buffer.clone().readString(Charset.forName("UTF8"))

                        try {
                            val badResponse = Gson().fromJson<ApiResponse<Any>?>(
                                json,
                                object : TypeToken<ApiResponse<Any>?>() {}.type
                            )
                            if (badResponse.data != null) {
                                Result.unauthorized(badResponse.message,
                                    response.raw().request.url.toString())
                            }
                            else {
                                Result.unauthorized(badResponse.message,
                                    response.raw().request.url.toString())
                            }
                        }
                        catch (e: Exception) {
                            Result.unauthorized(json,
                                response.raw().request.url.toString())
                        }
                    } else {
                        Result.unauthorized(null,
                            response.raw().request.url.toString())
                    })
                } else
                    if (code == 400 || code == 500) {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        if (response.errorBody() != null) {
                            val bufferedSource: BufferedSource = response.errorBody()!!.source()
                            bufferedSource.request(Long.MAX_VALUE) // Buffer the entire body.

                            val json = bufferedSource.buffer.clone().readString(Charset.forName("UTF8"))

                            val badResponse = Gson().fromJson<ApiResponse<Any>?>(
                                json,
                                object : TypeToken<ApiResponse<Any>?>() {}.type
                            )
                            return if (code == 500) {
                                Result.error("SystemError", badResponse.message, null,
                                    response.raw().request.url.toString())

                            } else {
                                Result.error(badResponse.status, badResponse.message, null,
                                    response.raw().request.url.toString())
                            }
                        }
                    }
                    else if (code == 503) {
                        return Result.error("503", ErrorMessage().http503(), null,
                            response.raw().request.url.toString())
                    }
            }
            return Result.error(code.toString(), response.message(), null,
                response.raw().request.url.toString())
        } catch (e: Exception) {
            return if (e is ConnectException || e is UnknownHostException ||
                e is MalformedJsonException || e is SocketTimeoutException) {
                Result.error("ConnectionError", ErrorMessage().connection(), null, AuthInterceptor.url)
            } else {
                Result.error("999", ErrorMessage().system(e.message), null, AuthInterceptor.url)
            }
        }
    }


}