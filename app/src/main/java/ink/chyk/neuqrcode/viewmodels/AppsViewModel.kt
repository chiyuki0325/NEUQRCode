package ink.chyk.neuqrcode.viewmodels

import android.content.*
import android.util.*
import android.widget.*
import androidx.compose.ui.graphics.*
import androidx.lifecycle.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.LoadingState
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.activities.*
import ink.chyk.neuqrcode.neu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlin.Pair

@ExperimentalSerializationApi
class AppsViewModel(
  private val neu: NEUPass,
  private val mmkv: MMKV,
  private val campusRun: CampusRun
) : PersonalViewModel(
  neu, mmkv
) {
  // 加载状态：步道乐跑
  private var _campusRunState = MutableStateFlow(LoadingState.LOADING)
  val campusRunState: StateFlow<LoadingState> = _campusRunState

  // 当前学期名称
  private var _termName = MutableStateFlow("")
  val termName: StateFlow<String> = _termName

  // 存储乐跑 API 返回的数据
  private var _beforeRun = MutableStateFlow<BeforeRunResponse?>(null)
  val beforeRun: StateFlow<BeforeRunResponse?> = _beforeRun
  private var _termRunRecord = MutableStateFlow<GetTermRunRecordResponse?>(null)
  val termRunRecord: StateFlow<GetTermRunRecordResponse?> = _termRunRecord

  // 加载状态：邮件
  private var _mailListState = MutableStateFlow(LoadingState.LOADING)
  val mailListState: StateFlow<LoadingState> = _mailListState

  // 邮件列表
  private var _mailList = MutableStateFlow<MailListResponse?>(null)
  val mailList: StateFlow<MailListResponse?> = _mailList
  private val _coreMailRedirector = MutableStateFlow<String?>(null)

  private val t1 = arrayOf(
    R.string.run_t1_1,
    R.string.run_t1_2,
    R.string.run_t1_3,
  )
  private val t2 = arrayOf(
    R.string.run_t2_1,
    R.string.run_t2_2,
    R.string.run_t2_3,
  )
  private val t3 = arrayOf(
    R.string.run_t3_1,
    R.string.run_t3_2,
    R.string.run_t3_3,
  )
  private val t4 = arrayOf(
    R.string.run_t4_1,
    R.string.run_t4_2,
    R.string.run_t4_3,
  )
  private val t5 = arrayOf(
    R.string.run_t5_1,
    R.string.run_t5_2,
    R.string.run_t5_3,
    R.string.run_t5_4,
    R.string.run_t5_5
  )

  private var _appCardsOrder = MutableStateFlow(getAppCardsOrder())
  val appCardsOrder: StateFlow<List<String>> = _appCardsOrder

  fun getAppCardsOrder(): List<String> {
    return mmkv.decodeString("app_cards_order")?.split(",") ?: listOf(
      "campus_run",
      "mail_box",
      "deep_seek"
    )
  }

  fun setAppCardsOrder(order: List<String>) {
    mmkv.encode("app_cards_order", order.joinToString(","))
    _appCardsOrder.value = order
  }

  fun initCampusRun() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        try {
          // 登录步道乐跑
          campusRun.loginCampusRun()
          val index = campusRun.getIndex()
          _termName.value = index?.term_name ?: "服务器返回错误"
          _beforeRun.value = campusRun.getBeforeRun()
          _termRunRecord.value = campusRun.getTermRunRecord()
          _campusRunState.value = LoadingState.SUCCESS
        } catch (e: Exception) {
          Log.e("AppsViewModel", "initCampusRun: ${e.message}")
          _campusRunState.value = LoadingState.FAILED
        }
      }
    }
  }

  fun startCampusRun(context: Context) {
    // 进入乐跑

    val intent = Intent(context, CampusRunningActivity::class.java)
    intent.putExtra("url", campusRun.toStartRunUrl())
    context.startActivity(intent)
  }

  fun distanceColorAndTextPair(
    distance: String?,
  ): Pair<Color, Int> {
    // 返回对应的颜色和随机抽取的文字
    val distanceDouble = distance?.toDoubleOrNull()
    return when {
      distanceDouble == null -> Color(255, 0, 0) to t1.random()
      distanceDouble < 20 -> Color(255, 0, 0) to t1.random()
      distanceDouble < 40 -> Color(255, 165, 0) to t2.random()
      distanceDouble < 80 -> Color(0, 165, 255) to t3.random()
      distanceDouble < 96 -> Color(0, 255, 165) to t4.random()
      else -> Color(20, 255, 20) to t5.random()
    }
  }

  fun initMailBox() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        try {
          // 获取未读邮件
          prepareSessionAnd {
            val mailListResponse = neu.getMailList(it)
            val mailList = mailListResponse.first
            updateSession(mailListResponse.second)
            _mailList.value = mailList
            _coreMailRedirector.value = mailList?.url
            _mailListState.value = LoadingState.SUCCESS
          }
        } catch (e: Exception) {
          Log.e("AppsViewModel", "initMailBox: ${e.message}")
          _mailListState.value = LoadingState.FAILED
        }
      }
    }
  }

  private suspend fun getCoreMailUrl(): String? {
    val redirector = _coreMailRedirector.value
    if (redirector != null) {
      val pair = prepareSessionAnd { session -> neu.getCoreMailUrl(session, redirector) }
      updateSession(pair.second)
      val coreMailUrl = pair.first
      return coreMailUrl
    } else {
      return null
    }
  }

  fun openCoreMail(context: Context) {
    viewModelScope.launch {
      val url = getCoreMailUrl()
      if (url != null) {
        /*
        val intent = CustomTabsIntent.Builder()
          .setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
          .setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
          .build()
        val uri = Uri.parse(url)
        intent.launchUrl(context, uri)
         */

        val intent = Intent(context, WebPageActivity::class.java)
        intent.putExtra("url", url)
        context.startActivity(intent)
      } else {
        Toast.makeText(context, R.string.error_open_coremail, Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun openDeepSeek(context: Context) {
    // 进入 DeepSeek
    val intent = Intent(context, DeepSeekActivity::class.java)
    context.startActivity(intent)
  }
}
