package ink.chyk.neuqrcode.neu

import android.util.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64


class CampusRun(
  private val neu: NEUPass,
  private val mmkv: MMKV
) {
  // 步道乐跑 API
  private val apiHost = "https://tybzhtypt.neu.edu.cn/v3/api.php"

  private val api = object {
    // API 端点
    val index = "/WpIndex/Index"
    val beforeRun = "/Run2/beforeRunV260"

    // val getTimestamp = "/Run/getTimestampV278"
    // val getTermList = "/WpRun/getTermList"
    val getTermRunRecord = "/WpRun/getTermRunRecord"
  }

  // 统一身份认证 callback URL
  private val callbackUrl =
    "https://tybzhtypt.neu.edu.cn/bdlp_h5_fitness_test/public/index.php/index/login/neuLogin"

  // Referer
  private val referer = "https://tybzhtypt.neu.edu.cn/bdlp_h5_fitness_test/view/db/"

  // 开始跑步的 url
  private val startRunUrl =
    "https://tybzhtypt.neu.edu.cn/bdlp_h5_fitness_test/view/db/#/pages/run/index"

  private val encryption = object {
    // 加密算法
    private val MD5_SALT = "rDJiNB9j7vD2"
    private val AES_KEY = "Wet2C8d34f62ndi3".toByteArray(Charsets.UTF_8)
    private val AES_IV = "K6iv85jBD8jgf32D".toByteArray(Charsets.UTF_8)

    fun signMD5(obj: Map<String, Any>): String {
      val textToHash = obj.keys.sorted().joinToString("") { key ->
        key + obj[key].toString()
      }
      val md5 = MessageDigest.getInstance("MD5")
      md5.update((textToHash + MD5_SALT).toByteArray(Charsets.UTF_8))
      return md5.digest().joinToString("") { "%02x".format(it) }
    }

    fun aesDecrypt(data: String): String {
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      val keySpec = SecretKeySpec(AES_KEY, "AES")
      val ivSpec = IvParameterSpec(AES_IV)
      cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
      val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(data))
      return String(decryptedBytes, Charsets.UTF_8)
    }

    fun aesEncrypt(data: String): String {
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      val keySpec = SecretKeySpec(AES_KEY, "AES")
      val ivSpec = IvParameterSpec(AES_IV)
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
      val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
      return Base64.getEncoder().encodeToString(encryptedBytes)
    }
  }

  // ST
  private var campusRunTicket: String? = null

  // 登录时返回的请求参数
  private var args: Map<String, Any>? = null

  // 客户端
  private val client = OkHttpClient.Builder()
    .followRedirects(false)
    .build()

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

  suspend fun loginCampusRun() = withContext(Dispatchers.IO) {
    // 登录步道乐跑

    var portalTicket = getPortalTicket()

    try {
      campusRunTicket = neu.loginNEUAppTicket(portalTicket, callbackUrl)
    } catch (e: Exception) {
      when (e) {
        is TicketFailedException -> {
          portalTicket = getPortalTicket(true)  // 过期了 重新登录
          campusRunTicket = neu.loginNEUAppTicket(portalTicket, callbackUrl)
        }
        else -> throw e
      }
    }

    //Log.d("CampusRun", "CampusRun Ticket: $campusRunTicket")

    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .build()

    val req1 = Request.Builder()
      .url("$callbackUrl?ticket=$campusRunTicket")
      .header("User-Agent", neu.userAgent ?: "NEUQRCode")
      .build()

    val res1 = Utils.executeRequest(client, req1, "登录步道乐跑失败")

    if (res1.code != 302) {
      Log.d("CampusRun", "Not 302: ${res1.code}")
      throw RequestFailedException("登录步道乐跑失败: ${res1.code}")
    }

    val req2 = Request.Builder()
      .url(callbackUrl)
      .header("Cookie", "Path=/; PHPSESSID=$campusRunTicket")
      .build()

    val res2 = Utils.executeRequest(client, req2, "登录步道乐跑失败")

    val location =
      res2.header("Location") ?: throw RequestFailedException("登录步道乐跑失败: 无法获取 Location")
    val query = location.split("?")[1].split("&").associate {
      val (key, value) = it.split("=")
      key to value
    }
    // 反序列化参数
    args = mapOf(
      "uid" to query["uid"]!!,
      "token" to query["token"]!!,
      "school_id" to query["school_id"]!!,
      "term_id" to query["term_id"]!!,
      "course_id" to query["course_id"]!!,
      "class_id" to "0",
      "student_num" to query["student_num"]!!,
      "card_id" to query["card_id"]!!,
      "version" to 1,
      "ostype" to "5",
      "role" to 1
    )

    // Log.d("CampusRun", "CampusRun Args: $args")
  }

  private fun random6(): String {
    // 随机六位数当作 nonce
    return (100000..999999).random().toString()
  }

  private fun toJsonObject(map: Map<String, Any>): JsonObject {
    return buildJsonObject {
      map.forEach { (key, value) ->
        when (value) {
          is String -> put(key, value)
          is Int -> put(key, value)
          is Boolean -> put(key, value)
          is Float -> put(key, value)
          is Double -> put(key, value)
          is Long -> put(key, value)
          is JsonObject -> put(key, value)
          is JsonArray -> put(key, value)
          else -> error("Unsupported type: ${value::class.simpleName}")
        }
      }
    }
  }

  // 乐跑 API 请求
  private suspend inline fun <reified T> campusRunApiRequest(
    endpoint: String,
    requestArgs: Map<String, Any>,
  ): T? {
    val url = "$apiHost$endpoint"

    val newArgs = requestArgs.toMutableMap()
    newArgs["nonce"] = random6()
    newArgs["timestamp"] = System.currentTimeMillis() / 1000

    val sign = encryption.signMD5(newArgs)
    newArgs["sign"] = sign

    val encrypted = encryption.aesEncrypt(Json.encodeToString(toJsonObject(newArgs)))

    // 编码为 form-urlencoded
    val requestBody = FormBody.Builder()
      .add("ostype", "5")
      .add("data", encrypted)
      .build()

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", neu.userAgent ?: "NEUQRCode")
      .header("Referer", referer)
      .post(requestBody)
      .build()

    val response = Utils.executeRequest(client, request, "请求步道乐跑 API 失败")

    val responseBody = response.body?.string()
      ?: throw RequestFailedException("请求步道乐跑 API 失败: 无法获取响应体")
    val responseDecrypted = encryption.aesDecrypt(
      Json.decodeFromString<CampusRunResponse>(responseBody).data
    )

    return Json.decodeFromString<T>(responseDecrypted)
  }

  suspend fun getIndex(): WpIndexResponse? {
    return campusRunApiRequest(api.index, args!!)
  }

  @ExperimentalSerializationApi
  suspend fun getBeforeRun(): BeforeRunResponse? {
    return campusRunApiRequest(api.beforeRun, args!!)
  }

  suspend fun getTermRunRecord(): GetTermRunRecordResponse? {
    return campusRunApiRequest(api.getTermRunRecord, args!!)
  }

  fun toStartRunUrl(): String {
    // Cookie to Url
    return "$startRunUrl?${
      args!!.map { (key, value) -> "$key=$value" }.joinToString("&")
    }&timestamp=${System.currentTimeMillis() / 1000}&login_type=4"
  }
}