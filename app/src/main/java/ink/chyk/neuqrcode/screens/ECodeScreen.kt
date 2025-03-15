package ink.chyk.neuqrcode.screens

import android.app.*
import android.graphics.*
import android.view.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.navigation.*
import com.google.zxing.*
import com.google.zxing.qrcode.*
import com.google.zxing.qrcode.decoder.*
import ink.chyk.neuqrcode.LoadingState
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.viewmodels.*
import java.time.*
import java.time.format.*


@Composable
fun ECodeScreen(viewModel: ECodeViewModel, navController: NavController) {
  val loadingState by viewModel.loadingState.collectAsState()
  val code by viewModel.code.collectAsState()
  val hasCode = code.isNotEmpty()
  val userInfo by viewModel.userInfo.collectAsState()
  val codeGenerateTime by viewModel.codeGenerateTime.collectAsState()

  val context = LocalContext.current
  val activity = context as? Activity

  // 进入界面时，调整亮度为最大值
  LaunchedEffect(Unit) {
    val now = LocalTime.now()
    val isAnti = viewModel.isAntiFlashlight()
        || now.isBefore(LocalTime.of(7, 0))
        || now.isAfter(LocalTime.of(22, 0))
    if (!isAnti)
      activity?.let {
        setScreenBrightness(it.window, 1.0f)
      }
  }

  // 退出界面时，恢复亮度
  DisposableEffect(Unit) {
    onDispose {
      activity?.let {
        setScreenBrightness(it.window, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
      }
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Image(
          painter = painterResource(
            if (isSystemInDarkTheme()) {
              R.drawable.logo_white
            } else {
              R.drawable.logo_black
            }
          ),
          modifier = Modifier.height(48.dp),
          contentDescription = "NEU ECode",
          contentScale = ContentScale.FillHeight
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text("一码通", style = MaterialTheme.typography.headlineLarge)
      }

      Spacer(modifier = Modifier.height(32.dp))

      if (loadingState == LoadingState.SUCCESS && hasCode) {
        ECodeImage(code) {
          viewModel.refreshECode()
        }
      } else if (loadingState == LoadingState.LOADING) {
        ECodeLoading()
      } else {
        ECodeFailed {
          viewModel.refreshECode()
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      Text(
        text = if (userInfo != null) {
          "${userInfo?.userCode} | ${userInfo?.userName} | ${userInfo?.unitName}"
        } else if (loadingState == LoadingState.LOADING) {
          "加载中..."
        } else {
          ""
        },
        style = MaterialTheme.typography.bodyMedium
      )

      Text(
        text = if (loadingState == LoadingState.SUCCESS) {
          if (hasCode) {
            // 成功 有二维码
            val instant = Instant.ofEpochMilli(codeGenerateTime)
            val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val generateTime = localDateTime.format(formatter)
            stringResource(R.string.generate_time).format(generateTime)
          } else {
            // 响应成功 但是没有二维码
            stringResource(R.string.generate_failed_unknown_server)
          }
        } else if (loadingState == LoadingState.FAILED) {
          // 连接失败
          stringResource(R.string.generate_failed_network)
        } else {
          // 加载中
          ""
        },
        style = MaterialTheme.typography.bodyMedium
      )

    }
  }
}


@Composable
fun ECodeLoading() {
  Box(
    modifier = Modifier
      .size(210.dp)
      .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center
  ) {
    CircularProgressIndicator()
  }
}

@Composable
fun ECodeImage(
  code: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(210.dp)
      .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center
  ) {
    val customFont = FontFamily(Font(R.font.iconfont))
    val primaryColor = Color(0xff006533)
    val backgroundColor = Color.White

    ColoredQRCode(
      code,
      dpToPx(210.dp),
      primaryColor.toArgb(),
      backgroundColor.toArgb()
    ).let {
      Image(
        bitmap = it?.asImageBitmap() ?: return@let Text(stringResource(R.string.failed_qr)),
        contentDescription = "ECode",
        modifier = Modifier
          .size(210.dp)
          .clip(RoundedCornerShape(8.dp))
          .clickable { onClick() },
        contentScale = ContentScale.FillWidth,
      )
    }

    Text(
      text = "●",
      color = primaryColor,
      fontFamily = customFont,
      fontSize = 48.sp,
    )

    Text(
      text = "\uE6BE",
      color = backgroundColor,
      fontFamily = customFont,
      fontSize = 32.sp,
    )
  }
}

@Composable
fun ECodeFailed(onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(210.dp)
      .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Image(
      painter = painterResource(R.drawable.no_internet),
      contentDescription = "No Internet",
      modifier = Modifier.size(48.dp),
      contentScale = ContentScale.FillHeight
    )
  }
}

// Generated by ChatGPT

@Composable
fun dpToPx(dp: Dp): Int {
  val density = LocalDensity.current
  return with(density) { dp.toPx().toInt() }
}

@Composable
fun ColoredQRCode(
  text: String,
  size: Int,
  foregroundColor: Int,
  backgroundColor: Int
): Bitmap? {
  try {
    val qrCodeWriter = QRCodeWriter()
    val hints = mapOf(
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
      EncodeHintType.MARGIN to 1
    )
    val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hints)

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    for (x in 0 until width) {
      for (y in 0 until height) {
        bitmap.setPixel(
          x,
          y,
          if (bitMatrix[x, y]) foregroundColor else backgroundColor
        )
      }
    }
    return bitmap
  } catch (e: WriterException) {
    e.printStackTrace()
    return null
  }
}


fun setScreenBrightness(window: Window, brightness: Float) {
  val layoutParams = window.attributes
  layoutParams.screenBrightness = brightness // 范围：0.0f（最暗）到 1.0f（最亮）
  window.attributes = layoutParams
}
