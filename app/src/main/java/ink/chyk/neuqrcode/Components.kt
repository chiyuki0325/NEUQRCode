package ink.chyk.neuqrcode

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*

@Composable
fun NEUTitle(
  text: String
) {
  // 统一身份认证标题
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .padding(vertical = 16.dp, horizontal = 8.dp),
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
      contentDescription = "NEU Logo",
    )
    Text(text, style = MaterialTheme.typography.headlineSmall)
  }
}

@Composable
fun DialogTitle(icon: Int, text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = text
    )
    Text(
      text = text,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(start = 8.dp)
    )
  }
}



@Composable
fun RowButton(
  iconResource: Int,
  text: String,
  content: String? = null,
  clickable: Boolean = false,
  onClick: () -> Unit = {}
) {
  var rowModifier = Modifier
    .fillMaxWidth()
    .height(48.dp)
    .padding(horizontal = 16.dp)
    .clip(RoundedCornerShape(8.dp))
  if (clickable) {
    rowModifier = rowModifier.clickable(onClick = onClick)
  }
  Row(
    modifier = rowModifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row {
      Icon(
        painter = painterResource(iconResource),
        contentDescription = text
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(text)
      if (content != null) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(content, color = MaterialTheme.colorScheme.secondary)
      }
    }
    if (clickable) {
      Icon(
        painter = painterResource(R.drawable.ic_fluent_chevron_right_20_filled),
        contentDescription = "前往"
      )
    } else {
      Box {}
    }
  }
}