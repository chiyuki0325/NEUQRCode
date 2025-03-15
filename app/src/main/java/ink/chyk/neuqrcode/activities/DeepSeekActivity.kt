package ink.chyk.neuqrcode.activities

import android.os.Bundle
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.*
import androidx.lifecycle.viewmodel.compose.*
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.neu.*
import ink.chyk.neuqrcode.ui.theme.*
import ink.chyk.neuqrcode.viewmodels.*

@ExperimentalMaterial3Api
class DeepSeekActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      DeepSeekScreen()
    }
  }
}

@ExperimentalMaterial3Api
@Composable
fun DeepSeekScreen() {
  val viewModel = viewModel<DeepSeekViewModel>(
    factory = DeepSeekViewModelFactory(
      NEUPass { true } // 自行处理异常
    )
  )

  AppTheme {
    WithTopBar(viewModel) {
      Text("Text")
    }
  }
}


@ExperimentalMaterial3Api
@Composable
// https://developer.android.com/develop/ui/compose/components/app-bars?hl=zh-cn#center
fun WithTopBar(
  viewModel: DeepSeekViewModel,
  content: @Composable (DeepSeekViewModel) -> Unit = {}
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
  val rail = viewModel.rail.collectAsState()

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

    topBar = {
      CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        title = {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = stringResource(R.string.neu_deepseek),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = {
            viewModel.toggleRail()
          }) {
            Icon(
              painter = painterResource(R.drawable.ic_fluent_list_24_filled),
              contentDescription = "Categories"
            )
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { innerPadding ->
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      //RailedDrawer(rail, viewModel)
      Box(
        modifier = Modifier
          .fillMaxSize(),
      ) {
        content(viewModel)
      }
    }
  }
}


/*
@Composable
fun RailedDrawer(
  rail: State<Boolean>,
  viewModel: DeepSeekViewModel
) {
  // 左侧抽屉
  Box(
    modifier = Modifier
      .padding(vertical = 8.dp)
      .fillMaxHeight()
  ) {
    Column {
      DrawerItem.items.forEach { item ->
        // 动画背景颜色
        val backgroundColor by animateColorAsState(
          targetValue = if (category.value == item.category)
            MaterialTheme.colorScheme.secondaryContainer
          else
            Color.Transparent
        )

        // 动画宽度
        val boxWidth by animateDpAsState(
          targetValue = if (rail.value) 120.dp else 40.dp,
        )

        Box(
          modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor) // 使用动画背景颜色
            .width(boxWidth) // 添加动画宽度
            .height(40.dp)
            .clickable {
              viewModel.setCategory(item.category)
              if (rail.value) {
                viewModel.toggleRail()
              }
            }
        ) {
          Row(
            modifier = Modifier
              .padding(8.dp)
              .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AnimatedVisibility(
              visible = true,
              enter = fadeIn(),
              exit = fadeOut()
            ) {
              Icon(
                painter = painterResource(item.icon),
                contentDescription = "Category Icon",
              )
            }
            AnimatedVisibility(
              visible = rail.value,
              enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
              exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
              Text(
                text = stringResource(item.label),
                modifier = Modifier.padding(start = 8.dp)
              )
            }
          }
        }
      }
    }
  }
}
 */