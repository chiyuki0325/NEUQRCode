package ink.chyk.neuqrcode.activities

import android.content.*
import android.os.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.ui.theme.*
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.Utils
import kotlinx.coroutines.time.*
import java.time.*

class TermsOfServiceActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 初始化 mmkv
    MMKV.initialize(this)
    val mmkv = MMKV.defaultMMKV()
    enableEdgeToEdge()
    setContent {
      TermsOfServiceScreen {
        mmkv.encode("tos", 220)
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
      }
    }
  }
}

@Composable
fun TermsOfServiceScreen(
  onAccept: () -> Unit = {}
) {
  val scrollState = rememberScrollState()
  var countdown by remember { mutableIntStateOf(10) }
  var timer by remember { mutableStateOf(false) }
  var isButtonEnabled by remember { mutableStateOf(false) }
  var check1 by remember { mutableStateOf(false) }
  var check2 by remember { mutableStateOf(false) }
  var check3 by remember { mutableStateOf(false) }

  val ctx = LocalContext.current


  LaunchedEffect(Unit) {
    if (!Utils.isDebug(ctx)) {
      while (countdown > 0) {
        delay(
          Duration.ofSeconds(1)
        )
        countdown--
      }
    }
    timer = true
  }

  LaunchedEffect(scrollState.canScrollForward) {
    if (!scrollState.canScrollForward) isButtonEnabled = true
  }

  AppTheme {
    Scaffold { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(16.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize(),
          verticalArrangement = Arrangement.Center,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_fluent_book_information_24_regular),
              contentDescription = "Back",
            )
            Text(
              text = stringResource(R.string.tos_title),
              style = MaterialTheme.typography.headlineSmall,
              modifier = Modifier.padding(start = 8.dp)
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = stringResource(R.string.tos_read_carefully),
          )
          Spacer(modifier = Modifier.height(16.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .clipToBounds()
              .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
              .clip(RoundedCornerShape(8.dp))
          ) {
            TextField(
              value = stringResource(R.string.tos_v220),
              onValueChange = {},
              readOnly = true,
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
          Check(
            checked = check1,
            onCheckedChange = { check1 = it },
            res = R.string.tos_v220_checkbox_1
          )
          Check(
            checked = check2,
            onCheckedChange = { check2 = it },
            res = R.string.tos_v220_checkbox_2
          )
          Check(
            checked = check3,
            onCheckedChange = { check3 = it },
            res = R.string.tos_v220_checkbox_3
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            Button(
              onClick = onAccept,
              enabled = isButtonEnabled && timer && check1 && check2 && check3
            ) {
              Text(
                text = if (timer) {
                  stringResource(R.string.tos_confirm)
                } else {
                  countdown.toString() + "s"
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun Check(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  res: Int
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange
    )
    Text(
      text = stringResource(res)
    )
  }
}
