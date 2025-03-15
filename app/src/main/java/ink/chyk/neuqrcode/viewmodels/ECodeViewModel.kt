package ink.chyk.neuqrcode.viewmodels

import android.util.*
import androidx.lifecycle.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.*
import ink.chyk.neuqrcode.neu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ECodeViewModel(
  override val mmkv: MMKV,
  override val neu: NEUPass = NEUPass({ false })
  // 需要使其抛出异常以便显示空白二维码
) : BasicViewModel(mmkv, neu) {
  // 具体实现：app 名称，以及几个方法

  override val appName: String = "ecode"

  override suspend fun newAppTicket(portalTicket: String): String {
    return neu.loginECodeTicket(portalTicket)
  }

  override suspend fun newAppSession(): NEUAppSession {
    return neu.newECodeSession()
  }

  override suspend fun loginApp(session: NEUAppSession, appTicket: String) {
    return neu.loginECode(session, appTicket)
  }

  // 二维码数据
  private val _code = MutableStateFlow("")
  val code: StateFlow<String> = _code

  // 用户信息
  private val _userInfo = MutableStateFlow<ECodeUserInfoResponse?>(null)
  val userInfo: StateFlow<ECodeUserInfoResponse?> = _userInfo

  // 二维码生成时间
  private val _codeGenerateTime = MutableStateFlow(0L)
  val codeGenerateTime: StateFlow<Long> = _codeGenerateTime

  // 二维码过期时间
  private val _codeExpiredAt = MutableStateFlow(0L)
  // val codeExpiredAt: StateFlow<Long> = _codeExpiredAt


  /**
   * 获取二维码
   */
  private suspend fun _refreshECode() {
    try {
      prepareSessionAnd { session ->
        // 重新获取二维码
        val eCode = neu.getQRCode(session)
        if (eCode == null) {
          return@prepareSessionAnd
        }
        if (_userInfo.value == null) {
          val eCodeUserInfo = neu.getECodeUserInfo(session) ?: return@prepareSessionAnd
          _userInfo.value = eCodeUserInfo.data[0].attributes
        }
        _code.value = eCode.data[0].attributes.qrCode
        _codeGenerateTime.value = eCode.data[0].attributes.createTime
        _codeExpiredAt.value = eCode.data[0].attributes.qrInvalidTime
      }
    } catch (e: Exception) {
      // 网络问题
      Log.e("ECode", "Failed to refresh ECode: ${e.message}")
      _code.value = ""
      _loadingState.value = LoadingState.FAILED
    }
  }

  fun refreshECode() {
    viewModelScope.launch {
      _refreshECode()
    }
  }

  private fun startRefreshECode() {
    viewModelScope.launch {
      while (true) {
        // 计时器
        val currentTime: Long = System.currentTimeMillis()
        // Log.d("ECode", "Current Time: $currentTime, Expired At: $codeExpiredAt")
        if (currentTime >= _codeExpiredAt.value) {
          _refreshECode()
        }

        // 每秒检查一次
        delay(1000L)
      }
    }
  }

  init {
    startRefreshECode()
  }

  fun isAntiFlashlight(): Boolean {
    return mmkv.decodeBool("anti_flashlight", false)
  }
}