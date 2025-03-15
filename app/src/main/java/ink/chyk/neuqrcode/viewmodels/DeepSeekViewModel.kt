package ink.chyk.neuqrcode.viewmodels

import androidx.lifecycle.ViewModel
import com.tencent.mmkv.MMKV
import ink.chyk.neuqrcode.neu.*
import kotlinx.coroutines.flow.*

class DeepSeekViewModel(
  private val neu: NEUPass,
  private val mmkv: MMKV,
  private val deepseek: DeepSeek = DeepSeek(neu, mmkv)
) : ViewModel() {
  private var _rail = MutableStateFlow(false)
  val rail: StateFlow<Boolean> = _rail

  fun toggleRail() {
    _rail.value = !_rail.value
  }

}