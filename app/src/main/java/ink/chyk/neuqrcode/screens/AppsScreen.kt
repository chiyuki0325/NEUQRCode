package ink.chyk.neuqrcode.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.navigation.*
import dev.darkokoa.pangu.*
import ink.chyk.neuqrcode.*
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.neu.*
import ink.chyk.neuqrcode.viewmodels.*
import kotlinx.serialization.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@ExperimentalSerializationApi
@Composable
fun AppsScreen(
  viewModel: AppsViewModel,
  @Suppress("UNUSED_PARAMETER")
  navController: NavController,
  innerPadding: PaddingValues
) {
  val appCardsOrder by viewModel.appCardsOrder.collectAsState()
  val showSortAppsDialog = remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
      .padding(16.dp),
  ) {
    LazyColumn {
      item {
        AppsHeader { showSortAppsDialog.value = true }
      }
      appCardsOrder.forEach {
        item {
          Spacer(modifier = Modifier.height(16.dp))
          AppCard(it, viewModel, Modifier.animateItem())
        }
      }
    }
  }

  if (showSortAppsDialog.value) {
    SortAppsDialog(viewModel, onDismiss = { showSortAppsDialog.value = false })
  }
}

@Composable
fun AppsHeader(
  showSortAppsDialog: () -> Unit
) {
  // 顶部间距和标题
  Spacer(modifier = Modifier.height(32.dp))
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = stringResource(R.string.apps_center),
      style = MaterialTheme.typography.headlineLarge,
    )
    Spacer(modifier = Modifier.weight(1f))
    IconButton(
      onClick = showSortAppsDialog,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_fluent_arrow_sort_24_regular),
        contentDescription = null,
      )
    }
  }
  Spacer(modifier = Modifier.height(16.dp))
}

@ExperimentalSerializationApi
@Composable
fun AppCard(key: String, viewModel: AppsViewModel, modifier: Modifier) {
  Box(modifier) {
    when (key) {
      "campus_run" -> CampusRunCard(viewModel)
      "mail_box" -> MailBoxCard(viewModel)
      "deep_seek" -> DeepSeekCard(viewModel)
    }
  }
}

@ExperimentalSerializationApi
@Composable
fun CampusRunCard(
  viewModel: AppsViewModel,
) {
  // 暂时不拆组件 因为懒
  // 以后需要改的话再拆

  LaunchedEffect(Unit) {
    viewModel.initCampusRun()
    // 加载步道乐跑数据
  }

  val context = LocalContext.current
  val loadingState = viewModel.campusRunState.collectAsState()
  val termName = viewModel.termName.collectAsState()
  val beforeRun = viewModel.beforeRun.collectAsState()
  val termRunRecord = viewModel.termRunRecord.collectAsState()

  Card(
    modifier = Modifier.animateContentSize(),
  ) {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // 乐跑图标
        Icon(
          painter = painterResource(id = R.drawable.ic_fluent_run_48_regular),
          contentDescription = null,
          modifier = Modifier.size(48.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
        ) {
          // 乐跑标题
          Text(stringResource(R.string.campus_run), fontSize = 20.sp, fontWeight = FontWeight.Bold)
          // 当前学期名字 或者 加载失败字样
          Text(
            when (loadingState.value) {
              LoadingState.LOADING -> stringResource(R.string.loading)
              LoadingState.FAILED -> stringResource(R.string.no_network)
              LoadingState.SUCCESS -> termName.value
                // 略加修改
                .replace("~", " ~ ")
                .replace("学年", "").let {
                  Pangu.spacingText(it)
                }
            },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
          )
        }
        IconButton(
          enabled = loadingState.value == LoadingState.SUCCESS,
          onClick = {
            // 进入乐跑按钮
            viewModel.startCampusRun(context)
          },
          colors = IconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = MaterialTheme.colorScheme.secondary,
          ),
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_golang),
            contentDescription = null,
            modifier = Modifier.height(32.dp),
          )
        }
      } // 第一行结束

      if (loadingState.value == LoadingState.SUCCESS) {
        // 成功之后绘制剩余的内容
        val fontSize = 15.sp

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        ) {
          Text(
            // 今日已有 123 名 xdx 完成跑步
            buildAnnotatedString {
              append("今日已有 ")
              withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(beforeRun.value?.today_run_user_num.toString())
              }
              append(" 名校友完成跑步")
            },
            fontSize = fontSize,
          )

          val distance = termRunRecord.value?.total_score_distance
          val pair = viewModel.distanceColorAndTextPair(distance)

          Row(
            verticalAlignment = Alignment.Top
          ) {
            Text(
              buildAnnotatedString {
                append("本学期有效跑步 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                  append(termRunRecord.value?.total_score_num)
                }
                append(" 次，")
              },
              fontSize = fontSize
            )

            Box(modifier = Modifier.wrapContentSize()) {
              // 用于存储文字的宽度
              var textWidth by remember { mutableIntStateOf(0) }

              Text(
                buildAnnotatedString {
                  append("共 ")
                  withStyle(
                    SpanStyle(
                      fontWeight = FontWeight.Bold,
                    )
                  ) {
                    append(distance)
                  }
                  append(" 公里")
                },
                onTextLayout = { textLayoutResult: TextLayoutResult ->
                  textWidth = textLayoutResult.size.width
                },
                fontSize = fontSize,
                modifier = Modifier.align(Alignment.CenterStart)
              )

              // 绘制下划线, generated by DeepSeek
              Canvas(
                modifier = Modifier
                  .width(textWidth.dp) // 根据文字宽度设置 Canvas 宽度
                  .height(2.dp)
                  .align(Alignment.BottomStart)
              ) {
                // 计算下划线的位置
                val canvasHeight = size.height

                // 绘制下划线
                drawLine(
                  color = pair.first,
                  start = Offset(0f, canvasHeight / 2),
                  end = Offset(textWidth.toFloat(), canvasHeight / 2),
                  strokeWidth = 2.dp.toPx()
                )
              }
            }
          }

          // 鼓励语
          Text(stringResource(pair.second), fontSize = fontSize)

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            // 七天进度
            beforeRun.value?.weekData?.list?.forEach {
              DailyProgressCircle(it.date, it.distance, modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}
// CampusRunCard end

@Composable
fun DailyProgressCircle(
  date: String,
  distance: String,
  modifier: Modifier = Modifier
) {
  var distanceFloat = distance.toFloat()
  if (distanceFloat > 5) {
    distanceFloat = 5f
  }
  val dailyMax = 5  // km

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(
        progress = {
          distanceFloat / dailyMax
        }
      )
      Text(
        text = distance,
        modifier = Modifier.align(Alignment.Center),
        fontSize = if (distance == "0") 14.sp else 12.sp
      )
    }
    Text(
      text = "周$date",
      fontSize = 12.sp,
    )
  }
}


@ExperimentalSerializationApi
@Composable
fun MailBoxCard(
  viewModel: AppsViewModel,
) {
  LaunchedEffect(Unit) {
    viewModel.initMailBox()
    // 加载邮箱数据
  }

  val context = LocalContext.current
  val mailList = viewModel.mailList.collectAsState()
  val loadingState = viewModel.mailListState.collectAsState()

  Card(
    modifier = Modifier.animateContentSize(),
  ) {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // 邮箱图标
        Icon(
          painter = painterResource(
            if (
              mailList.value?.num != "0" && loadingState.value == LoadingState.SUCCESS
            ) {
              R.drawable.ic_fluent_mail_unread_48_regular
            } else {
              R.drawable.ic_fluent_mail_48_regular
            }
          ),
          contentDescription = null,
          modifier = Modifier.size(48.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
        ) {
          // 邮箱标题
          Text(
            stringResource(R.string.student_mailbox),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
          )
          // 邮箱数量
          Text(
            text = when (loadingState.value) {
              LoadingState.LOADING -> buildAnnotatedString {
                append(stringResource(R.string.loading))
              }

              LoadingState.FAILED -> buildAnnotatedString {
                append(stringResource(R.string.no_network))
              }

              LoadingState.SUCCESS -> {
                buildAnnotatedString {
                  withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(mailList.value?.num)
                  }
                  append(stringResource(R.string.unreads))
                }
              }
            },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
          )
        }
        IconButton(
          onClick = {
            // 进入邮箱按钮
            viewModel.openCoreMail(context)
          },
          colors = IconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = MaterialTheme.colorScheme.secondary,
          ),
          modifier = Modifier.size(48.dp),
          enabled = loadingState.value == LoadingState.SUCCESS,
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_fluent_mail_read_32_regular),
            contentDescription = null,
            modifier = Modifier.height(32.dp),
          )
        }
      } // 第一行结束

      if (mailList.value?.num != "0") {
        // 有未读邮件
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        ) {
          val dark = isSystemInDarkTheme()
          mailList.value?.list?.forEach {
            // 邮件列表
            Spacer(modifier = Modifier.height(8.dp))
            MailItem(it, dark)
          }
        }
      }
    }
  }
}

@Composable
fun MailItem(
  mailDetails: MailDetails,
  dark: Boolean
) {
  // 在一行内 显示subject和from（减淡颜色）
  // 溢出的话显示省略号
  Row(
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 和课表类似，根据邮件主题计算颜色
    Surface(
      modifier = Modifier
        .width(4.dp)
        .height(16.dp)
        .clip(RoundedCornerShape(4.dp)),
      color = Color(HashColorHelper.calcColor(mailDetails.subject, dark))
    ) {}
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
          append(mailDetails.subject)
        }
        append(" - ")
        withStyle(SpanStyle(color = Color.Gray)) {
          append(mailDetails.from)
        }
      },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@ExperimentalSerializationApi
@Composable
fun DeepSeekCard(
  viewModel: AppsViewModel
) {
  val context = LocalContext.current

  Card {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // DeepSeek 图标
        Icon(
          painter = painterResource(R.drawable.deepseek),
          contentDescription = null,
          modifier = Modifier.size(48.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
        ) {
          // DeepSeek 标题
          Text(
            stringResource(R.string.deepseek),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
          )
          // DeepSeek 描述
          Text(
            stringResource(R.string.deepseek_description),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
          )
        }
        IconButton(
          onClick = {
            // 进入 DeepSeek 按钮
            viewModel.openDeepSeek(context)
          },
          colors = IconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = MaterialTheme.colorScheme.secondary,
          ),
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_fluent_send_32_regular),
            contentDescription = null,
            modifier = Modifier.height(32.dp),
          )
        }
      } // 第一行结束
    }
  }
}


@ExperimentalSerializationApi
@Composable
fun SortAppsDialog(
  viewModel: AppsViewModel,
  onDismiss: () -> Unit
) {
  // github.com/Calvin-LL/Reorderable

  @Composable
  fun appName(appId: String): String {
    return when (appId) {
      "campus_run" -> stringResource(R.string.campus_run)
      "mail_box" -> stringResource(R.string.student_mailbox)
      "deep_seek" -> stringResource(R.string.deepseek)
      else -> ""
    }
  }

  @Composable
  fun appIcon(appId: String): Int {
    return when (appId) {
      "campus_run" -> R.drawable.ic_fluent_run_48_regular
      "mail_box" -> R.drawable.ic_fluent_mail_48_regular
      "deep_seek" -> R.drawable.deepseek
      else -> R.drawable.ic_fluent_arrow_sort_24_regular
    }
  }

  var list by remember { mutableStateOf(viewModel.getAppCardsOrder()) }
  val lazyListState = rememberLazyListState()
  val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
    list = list.toMutableList().apply {
      add(to.index, removeAt(from.index))
    }
    viewModel.setAppCardsOrder(list)
  }

  Dialog(onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier
          .padding(16.dp)
          .clip(RoundedCornerShape(8.dp))
      ) {
        DialogTitle(
          R.drawable.ic_fluent_arrow_sort_24_regular,
          stringResource(R.string.sort_apps)
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
          state = lazyListState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(list, key = { it }) {
            ReorderableItem(reorderableLazyListState, key = it) { isDragging ->
              val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
              Surface(
                shadowElevation = elevation,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.draggableHandle().padding(8.dp)
                ) {
                  Icon(
                    painter = painterResource(appIcon(it)),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                  )
                  Text(appName(it))
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(R.string.done))
        }
      }
    }
  }
}