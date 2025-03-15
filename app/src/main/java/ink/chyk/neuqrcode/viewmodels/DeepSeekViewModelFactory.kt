package ink.chyk.neuqrcode.viewmodels

import androidx.lifecycle.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.neu.*

class DeepSeekViewModelFactory(
  private val neu: NEUPass
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(DeepSeekViewModel::class.java)) {
      return DeepSeekViewModel(
        neu,
        MMKV.defaultMMKV()
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}