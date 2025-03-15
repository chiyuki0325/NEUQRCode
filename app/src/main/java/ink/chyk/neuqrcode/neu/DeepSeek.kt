package ink.chyk.neuqrcode.neu

import com.tencent.mmkv.MMKV
import ink.chyk.neuqrcode.RequestFailedException
import ink.chyk.neuqrcode.TicketExpiredException
import ink.chyk.neuqrcode.TicketFailedException
import ink.chyk.neuqrcode.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

class DeepSeek(
  private val neu: NEUPass,
  private val mmkv: MMKV
) {
  // 2025.3.12 东北大学校内上线 DeepSeek 智能助手
  // 此文件为其 API，支持可选的深度思考和内部搜索


  // ============================
  private val cookieVjuidName = "cookie_vjuid_login"
  private val composeId = 2  // 暂未知用途

  private val apiHost = "https://aia.neu.edu.cn"
  private val api = object {
    val getNewSessionId = "/site/voom/get_new_session_id"
    val sessionUpdate = "/site/voom/session_update"
    val sessionList = "/site/voom/session_list"
    val chatHistoryInfo = "/site/voom/chat_history_info"
    val composeChat = "/site/ai/compose_chat"  // chat completion
    val newChatRecord = "/common/voom/new_chat_record"
  }

  // 统一身份认证 callback URL
  private val callbackUrl = "https://aia.neu.edu.cn/common/actionCasLogin?redirect_url="
  // ============================

  // 登录状态
  private var ticket: String? = null // st
  private var vjuid: String? = null // cookie_vjuid_login


  // copied from BasicViewModel
  private suspend fun getPortalTicket(reLogin: Boolean = false): String {
    val studentId = mmkv.decodeString("student_id")!!
    val password = mmkv.decodeString("password")!!
    var portalTicket: String? = mmkv.decodeString("portal_ticket")
    if (portalTicket == null || reLogin) {
      portalTicket = neu.loginPortalTicket(studentId, password)
      mmkv.encode("portal_ticket", portalTicket)
    }
    return portalTicket
  }

  suspend fun loginDeepSeek() = withContext(Dispatchers.IO) {
    // 登录 DeepSeek

    var portalTicket = getPortalTicket()

    try {
      ticket = neu.loginNEUAppTicket(portalTicket, callbackUrl)
    } catch (e: Exception) {
      when (e) {
        is TicketFailedException -> {
          portalTicket = getPortalTicket(true)  // 过期了 重新登录
          ticket = neu.loginNEUAppTicket(portalTicket, callbackUrl)
        }

        else -> throw e
      }
    }

    //Log.d("DeepSeek", "DeepSeek Ticket: $ticket")

    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .build()

    val req1 = Request.Builder()
      .url("$callbackUrl?ticket=$ticket")
      .header("User-Agent", neu.userAgent ?: "NEUQRCode")
      .build()
    val res1 = Utils.executeRequest(client, req1, "登录 DeepSeek 失败")

    if (res1.code != 302) {
      throw RequestFailedException("登录 DeepSeek 失败: ${res1.code}")
    }

    // 获取 cookie_vjuid_login
    val cookie = res1.headers["Set-Cookie"]
    if (cookie != null && cookie.contains(cookieVjuidName)) {
      vjuid = cookie.split(";")[0].split("=")[1]
    } else {
      throw TicketFailedException("登录 DeepSeek 失败: cookie_vjuid_login 未找到")
    }
  }

  private suspend fun <T> deepSeekRequest(
    endpoint: String,
    queryParams: Map<String, String> = mapOf(),
    formParams: Map<String, String> = mapOf()
  ): T {
    // 发送 DeepSeek 请求

    if (ticket == null || vjuid == null) {
      throw TicketFailedException("DeepSeek 未登录")
    }

    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .build()

    val url = if (queryParams.isNotEmpty()) {
      val query = queryParams.map { (k, v) -> "$k=$v" }.joinToString("&")
      "$apiHost$endpoint?$query"
    } else {
      "$apiHost$endpoint"
    }

    val req: Request

    if (formParams.isNotEmpty()) {
      // build multipart
      val boundary = "----NEUQRCodeFormBoundary${System.currentTimeMillis()}"
      val multipart = MultipartBody.Builder(boundary)
        .setType(MultipartBody.FORM)
      formParams.forEach { (k, v) ->
        multipart.addFormDataPart(k, v)
      }
      req = Request.Builder()
        .url(url)
        .header("User-Agent", neu.userAgent ?: "NEUQRCode")
        .header("Cookie", "$cookieVjuidName=$vjuid; st=$ticket")
        .post(multipart.build())
        .build()
    } else {
      req = Request.Builder()
        .url(url)
        .header("User-Agent", neu.userAgent ?: "NEUQRCode")
        .header("Cookie", "$cookieVjuidName=$vjuid; st=$ticket")
        .build()
    }


    val res = Utils.executeRequest(client, req, "DeepSeek 请求失败")

    if (res.code != 200) {
      throw RequestFailedException("DeepSeek 请求失败: ${res.code}")
    }

    val body = res.body?.string()
    val responseJson = Json.decodeFromString<DeepSeekResponse<T>>(body!!)
    return responseJson.d
  }

  suspend fun getNewSessionId(): String {
    return deepSeekRequest(api.getNewSessionId)
  }

}