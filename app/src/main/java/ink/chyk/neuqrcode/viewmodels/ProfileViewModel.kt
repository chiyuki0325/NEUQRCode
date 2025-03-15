package ink.chyk.neuqrcode.viewmodels

import android.content.Context
import android.content.Intent
import android.util.*
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.*
import coil3.network.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.*
import ink.chyk.neuqrcode.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import ink.chyk.neuqrcode.activities.*
import ink.chyk.neuqrcode.neu.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.random.Random

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class GitHubRelease(
  @SerialName("tag_name")
  val tagName: String,
  val body: String
)

class ProfileViewModel(
  val mmkv: MMKV,
  val neu: NEUPass
) : PersonalViewModel(neu, mmkv) {
  private val _userInfo = MutableStateFlow<UserInfo?>(null)
  val userInfo: StateFlow<UserInfo?> = _userInfo
  private val _cardBalance = MutableStateFlow<PersonalDataItem?>(null)
  val cardBalance: StateFlow<PersonalDataItem?> = _cardBalance
  private val _netBalance = MutableStateFlow<PersonalDataItem?>(null)
  val netBalance: StateFlow<PersonalDataItem?> = _netBalance
  private val _loadComplete = MutableStateFlow(false)
  private val _headers = MutableStateFlow<NetworkHeaders?>(null)
  val headers: StateFlow<NetworkHeaders?> = _headers
  val loadComplete: StateFlow<Boolean> = _loadComplete

  fun refreshUserInfo() {
    viewModelScope.launch {
      _refreshUserInfo()
    }
  }

  private suspend fun _refreshUserInfo() {
    prepareSessionAnd { session ->
      try {
        val userInfoResponse = neu.getUserInfo(session)
        _userInfo.value = userInfoResponse.first?.info
        updateSession(userInfoResponse.second)

        val idsResponse = neu.getPersonalDataIds(session)
        val ids = idsResponse.first
        updateSession(idsResponse.second)

        if (ids == null) {
          return@prepareSessionAnd
        }

        // 随机等待几十毫秒防止风控
        delay(Random.nextInt(120).toLong())

        val cardBalanceResponse = neu.getPersonalDataItem(session, ids, "card.balance")
        _cardBalance.value = cardBalanceResponse.first?.data
        updateSession(cardBalanceResponse.second)

        delay(Random.nextInt(120).toLong())

        val netBalanceResponse = neu.getPersonalDataItem(session, ids, "net.balance")
        _netBalance.value = netBalanceResponse.first?.data
        updateSession(netBalanceResponse.second)

        _headers.value = NetworkHeaders.Builder()
          .add("Cookie", "SESS_ID=${session.sessId}; CK_LC=${session.lc}; CK_VL=${session.vl}")
          .add("Referer", "https://personal.neu.edu.cn/portal/")
          .build()

        _loadComplete.value = true
      } catch (_: SessionExpiredException) {
        // 重新登录
        mmkv.remove("personal_lc")
        mmkv.remove("personal_vl")
        mmkv.remove("personal_sess_id")
        _refreshUserInfo()
      }
    }
  }

  fun logout(context: Context) {
    mmkv.clearAll()
    val intent = Intent(context, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
  }

  fun openLink(context: Context, c: Int) {
    val browserIntent = Intent(
      Intent.ACTION_VIEW,
      android.net.Uri.parse(context.getString(c))
    )
    context.startActivity(browserIntent)
  }

  fun checkUpdate(context: Context) {
    viewModelScope.launch {
      _checkUpdate(context)
    }
  }

  private suspend fun _checkUpdate(context: Context) {
    suspend fun toast(msg: Int? = null, msgStr: String? = null) {
      withContext(Dispatchers.Main) {
        Toast.makeText(
          context,
          msgStr ?: context.getString(msg!!), Toast.LENGTH_SHORT
        ).show()
      }
    }

    val packageInfo =
      context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val isDebug = Utils.isDebug(versionName)

    if (isDebug) {
      toast(R.string.debug_no_update)
      return
    }

    toast(R.string.checking_update)

    val versionMajorName = versionName?.substringBefore("-")  // v3.0.2-3-commithash -> v3.0.2

    val client = OkHttpClient.Builder()
      .build()

    val request = Request.Builder()
      .url("https://r.chyk.ink/https://api.github.com/repos/chiyuki0325/NEUQRCode/releases/latest")
      .header("User-Agent", "NEUQRCode/$versionName")
      .build()

    withContext(Dispatchers.IO) {
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val body = response.body?.string()
          val responseJson = Json.decodeFromString<GitHubRelease>(body!!)
          val tagName = responseJson.tagName
          if (tagName != versionMajorName) {
            val sb = StringBuilder()
            sb.append(context.getString(R.string.found_latest_release, tagName))
              .append("\n")
              .append(responseJson.body)
            toast(msgStr = sb.toString())
            openLink(context, R.string.releases_url)
          } else {
            toast(R.string.already_latest_release)
          }
        } else {
          toast(R.string.update_check_failed)
        }
      }
    }
  }

  private suspend fun _recharge(context: Context) = withContext(Dispatchers.IO) {
    var portalTicket = getPortalTicket()
    var rechargeTicket: String
    val url = "https://pay.neu.edu.cn/drCasLogin"

    try {
      rechargeTicket = neu.loginNEUAppTicket(portalTicket, url)
    } catch (e: TicketFailedException) {
      portalTicket = getPortalTicket(true)
      rechargeTicket = neu.loginNEUAppTicket(portalTicket, url)
    }

    val redirectUrl = "$url?ticket=$rechargeTicket"

    val intent = CustomTabsIntent.Builder()
      .setShowTitle(false)
      .setUrlBarHidingEnabled(true)
      .setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
      .setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
      .build()

    intent.launchUrl(context, android.net.Uri.parse(redirectUrl))
  }

  fun recharge(context: Context) {
    viewModelScope.launch {
      _recharge(context)
    }
  }

  fun uploadAvatar(
    byteArray: ByteArray,
    mimeType: String,
    fileName: String,
    onUploadComplete: () -> Unit
  ) {
    Log.d("uploadAvatar", "Uploading avatar file $fileName")
    viewModelScope.launch {
      prepareSessionAnd { session ->
        val response = neu.uploadImage(session, byteArray, mimeType, fileName)
        updateSession(response.second)
        if (response.first == null) {
          Log.d("uploadAvatar", "Upload failed")
          throw Exception("Upload failed")
        }
        Log.d("uploadAvatar", "Response: ${response.first}")
        Log.d("uploadAvatar", "Uploaded url: ${response.first!!.url}")
        _userInfo.value = _userInfo.value?.copy(avatar = response.first!!.url)
        val response2 = neu.updateAvatar(session, response.first!!.url)
        Log.d("uploadAvatar", "Update avatar response: ${response2.first}")
        updateSession(response2.second)
        onUploadComplete()
      }
    }
  }
}