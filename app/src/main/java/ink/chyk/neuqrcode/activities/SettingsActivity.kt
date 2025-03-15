package ink.chyk.neuqrcode.activities

import android.app.*
import android.content.*
import android.content.pm.*
import android.os.*
import android.widget.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.ui.theme.*
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.RowButton
import ink.chyk.neuqrcode.Utils

class SettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppTheme {
        Scaffold { innerPadding ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
              .padding(16.dp),
          ) {
            Settings()
          }
        }
      }
    }
  }
}

@Composable
fun Settings() {
  val ctx = LocalContext.current
  val mmkv = MMKV.defaultMMKV()
  var antiFlashlightState by remember {
    mutableStateOf(mmkv.decodeBool("anti_flashlight", false))
  }
  var campusRunningAlwaysDarkState by remember {
    mutableStateOf(mmkv.decodeBool("campus_running_always_dark", false))
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Spacer(modifier = Modifier.height(32.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = {
          // 退出设置
          val activity = ctx as SettingsActivity
          activity.finish()
        },
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_fluent_chevron_left_20_filled),
          contentDescription = "Back",
        )
      }
      Text(
        text = stringResource(R.string.settings),
        style = MaterialTheme.typography.headlineLarge,
      )
    }
    Box {}
    SettingsCard(
      name = stringResource(R.string.create_courses_shortcut),
      description = stringResource(R.string.create_courses_shortcut_description),
      icon = R.drawable.ic_fluent_calendar_24_regular,
      onClick = {
        createShortcut(ctx)
      }
    )
    SettingsCard(
      name = stringResource(R.string.anti_flashlight),
      description = stringResource(R.string.anti_flashlight_description),
      icon = R.drawable.ic_fluent_flashlight_off_24_regular,
      state = antiFlashlightState,
      onClick = {
        antiFlashlightState = !antiFlashlightState
        mmkv.encode("anti_flashlight", antiFlashlightState)
      }
    )
    SettingsCard(
      name = stringResource(R.string.campus_running_always_dark),
      description = stringResource(R.string.campus_running_always_dark_description),
      icon = R.drawable.ic_fluent_weather_moon_24_regular,
      state = campusRunningAlwaysDarkState,
      onClick = {
        campusRunningAlwaysDarkState = !campusRunningAlwaysDarkState
        mmkv.encode("campus_running_always_dark", campusRunningAlwaysDarkState)
      }
    )

    if (Utils.isDebug(ctx)) {
      RowButton(
        R.drawable.ic_fluent_apps_24_filled,
        "调试 TBS 内核",
        "debugtbs",
        clickable = true,
        onClick = {
          val intent = Intent(ctx, WebPageActivity::class.java)
          intent.putExtra("url", "https://debugtbs.qq.com")
          ctx.startActivity(intent)
        }
      )
      RowButton(
        R.drawable.ic_fluent_apps_24_filled,
        "调试 X5 内核",
        "debugx5",
        clickable = true,
        onClick = {
          val intent = Intent(ctx, WebPageActivity::class.java)
          intent.putExtra("url", "https://debugx5.qq.com")
          ctx.startActivity(intent)
        }
      )
    }
  }
}

@Composable
fun SettingsCard(
  name: String,
  description: String,
  icon: Int,
  state: Boolean? = null,
  onClick: () -> Unit
) {
  Card(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(16.dp)
        .defaultMinSize(minHeight = 48.dp),
      horizontalArrangement = Arrangement.SpaceBetween // 将内容分布到两端
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f) // 占据剩余空间
      ) {
        Icon(
          painter = painterResource(icon),
          contentDescription = name,
          modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
          modifier = Modifier.weight(1f) // 使文本区域可以自动换行
        ) {
          Text(
            text = name,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(), // 确保文本占满宽度
            softWrap = true // 允许文本自动换行
          )
        }
      }

      if (state != null) {
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
          checked = state,
          onCheckedChange = { onClick() },
        )
      }
    }
  }
}


fun createShortcut(
  context: Context
) {
  val activity = context as Activity
  val shortcutManager = activity.getSystemService(ShortcutManager::class.java)

  // 创建 Intent 指定要启动的 Activity 和携带的参数
  val intent = Intent(activity, MainActivity::class.java).apply {
    action = Intent.ACTION_VIEW
    putExtra("screen", "courses") // 添加参数
  }

  // 创建快捷方式信息
  val shortcut = ShortcutInfo.Builder(activity, "courses_shortcut") // 唯一 ID
    .setShortLabel("课程表") // 显示名称
    .setLongLabel("NEU课程表") // 长名称
    .setIcon(android.graphics.drawable.Icon.createWithResource(activity, R.mipmap.ic_courses)) // 图标
    .setIntent(intent) // 设置 Intent
    .build()

  // 添加快捷方式
  shortcutManager.requestPinShortcut(shortcut, null)

  Toast.makeText(context, "已尝试创建课程表快捷方式\n若未创建请检查权限。", Toast.LENGTH_SHORT)
    .show()
}
