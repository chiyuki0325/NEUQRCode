package ink.chyk.neuqrcode

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Utils {
  // 此类存放一些可复用的工具方法

  companion object {
    fun isDebug(context: Context): Boolean {
      // 判断是否是 Debug 构建
      val packageInfo =
        context.packageManager.getPackageInfo(context.packageName, 0)
      val versionName = packageInfo.versionName

      return isDebug(versionName)
    }

    fun isDebug(versionName: String?): Boolean {
      return (versionName?.substringAfterLast("-")?.length == 8) or (versionName?.contains("beta") == true)
    }

    suspend fun executeRequest(
      client: OkHttpClient,
      request: Request,
      errorMessage: String
    ): Response {
      // 执行请求并处理通用逻辑
      return client.newCall(request).execute().also { response ->
        if (response.code !in 200..399) {
          throw RequestFailedException("$errorMessage: ${response.code}")
        }
      }
    }
  }
}