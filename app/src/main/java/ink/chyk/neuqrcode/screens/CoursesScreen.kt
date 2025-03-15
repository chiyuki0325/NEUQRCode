package ink.chyk.neuqrcode.screens

import android.content.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.navigation.*
import dev.darkokoa.pangu.*
import ink.chyk.neuqrcode.*
import ink.chyk.neuqrcode.activities.*
import ink.chyk.neuqrcode.viewmodels.*
import java.time.format.TextStyle as TimeTextStyle
import java.util.Locale
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.neu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import kotlin.math.*


@Composable
fun CoursesScreen(
  viewModel: CoursesViewModel,
  @Suppress("UNUSED_PARAMETER")
  navController: NavController,  // 没必要
  innerPadding: PaddingValues
) {
  // 课程表界面

  // date 状态放在最顶级组件 之后逐级传递
  val dateState = MutableStateFlow<String>(viewModel.today)

  if (!viewModel.isCourseImported()) {
    return ImportCoursesSplash()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    TodayCourses(viewModel, dateState)
  }
}

// 早午晚图标常量
val MORNING_ICON = R.drawable.ic_fluent_weather_sunny_24_regular
val AFTERNOON_ICON = R.drawable.ic_fluent_weather_sunny_low_24_regular
val EVENING_ICON = R.drawable.ic_fluent_weather_partly_cloudy_night_24_regular

@Composable
fun TodayCourses(
  viewModel: CoursesViewModel,
  dateState: MutableStateFlow<String>
) {
  // 课表主组件
  // 其中的 dateState 由最顶级组件传递

  val showWeekJumpDialog = remember { mutableStateOf(false) }

  val density = LocalDensity.current
  var width by remember { mutableIntStateOf(0) }
  val widthDp = with(density) { width.toDp() }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { size ->
        width = size.width
      }
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      // 日期和每日一言
      TodayTitle(viewModel, dateState)
      Spacer(modifier = Modifier.height(16.dp))

      // 课程卡片封装组件
      CoursesCardOuter(viewModel, dateState, widthDp)
    }
    // 底部选择器
    DaySelector(viewModel, showJumpDialog = { showWeekJumpDialog.value = true }, dateState)
  }

  // 日期跳转对话框
  if (showWeekJumpDialog.value) {
    WeekJumpDialog(
      viewModel = viewModel,
      onDismissRequest = { showWeekJumpDialog.value = false },
      dateState = dateState
    )
  }
}

@Composable
fun CoursesCardOuter(
  viewModel: CoursesViewModel,
  dateState: MutableStateFlow<String>,
  width: Dp,
) {
  // 课程卡片封装组件（左右滑动翻页）
  val dateId by dateState.collectAsState()
  val density = LocalDensity.current
  val widthPx = with(density) { width.toPx() }
  val scope = rememberCoroutineScope()

  var currentDate by remember { mutableStateOf(dateId) }
  var prevDate by remember { mutableStateOf(viewModel.prevDay(dateId)) }
  var nextDate by remember { mutableStateOf(viewModel.nextDay(dateId)) }

  var offsetX by remember { mutableFloatStateOf(0f) }

  val spacingPx = with(density) { 4.dp.toPx() }

  // 处理外部dateId变化
  LaunchedEffect(dateId) {
    if (dateId != currentDate) {
      currentDate = dateId
      offsetX = 0f
    }
  }

  // 更新前后日期
  LaunchedEffect(currentDate) {
    prevDate = viewModel.prevDay(currentDate)
    nextDate = viewModel.nextDay(currentDate)
  }

  var animating by remember { mutableStateOf(false) }

  val dragModifier = Modifier.draggable(
    orientation = Orientation.Horizontal,
    state = rememberDraggableState { delta ->
      offsetX += delta
    },
    enabled = !animating,
    onDragStopped = { velocity ->
      Log.d("CoursesScreen", "Drag Stopped")

      val target = when {
        offsetX < -widthPx / 5 -> -widthPx
        offsetX > widthPx / 5 -> widthPx
        else -> 0f
      }

      scope.launch {
        animating = true

        animate(
          initialValue = offsetX,
          targetValue = target,
          animationSpec = tween(durationMillis = 200)
        ) { value, _ -> offsetX = value }

        if (target != 0f) {
          val newDate = if (target < 0) nextDate else prevDate
          dateState.value = newDate
        }
        animating = false
      }
    }
  )

  Box(
    modifier = Modifier
      .width(width)
      .fillMaxHeight()
      .then(dragModifier)
      .clipToBounds()
  ) {
    // 前一页
    CoursesCard(
      viewModel, prevDate,
      modifier = Modifier
        .width(width)
        .offset { IntOffset((offsetX - widthPx - spacingPx).roundToInt(), 0) }
    )

    // 当前页
    CoursesCard(
      viewModel, currentDate,
      modifier = Modifier
        .width(width)
        .offset { IntOffset(offsetX.roundToInt(), 0) }
    )

    // 后一页
    CoursesCard(
      viewModel, nextDate,
      modifier = Modifier
        .width(width)
        .offset { IntOffset((offsetX + widthPx + spacingPx).roundToInt(), 0) }
    )
  }
}

@Composable
fun TodayTitle(
  viewModel: CoursesViewModel,
  dateState: MutableStateFlow<String>
) {
  val date by dateState.collectAsState()
  val quote by viewModel.quote.collectAsState()
  val ctx = LocalContext.current

  Text(
    ctx.getString(R.string.date).format(
      date.slice(4..5).toInt(),  // 月
      date.slice(6..7).toInt(),  // 日
      viewModel.getWeekday(date)
    ),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.fillMaxWidth(),
  )
  Spacer(modifier = Modifier.height(8.dp))
  FoldInTextAnimation(quote)
}

@Composable
fun CoursesCard(
  viewModel: CoursesViewModel,
  dateId: String,
  modifier: Modifier = Modifier
) {
  // 课表卡片

  val todayCourses = viewModel.getCoursesByDate(dateId)
  val scrollState = rememberScrollState()

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(0.dp)
      .verticalScroll(scrollState),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      if (todayCourses.isEmpty()) {
        NoCoursesSplash(dateId)
      } else {
        TodayCoursesList(todayCourses)
      }
    }
  }
}


@Composable
fun FoldInTextAnimation(quote: HitokotoQuote?) {
  var isVisible by remember { mutableStateOf(false) }

  // 触发动画
  LaunchedEffect(Unit) {
    isVisible = true
  }

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    AnimatedVisibility(
      visible = isVisible,
      enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start), // 从左往右展开
      exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start) // 从右往左折叠
    ) {
      Text(
        text = quote?.hitokoto ?: "",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.animateContentSize() // 内容大小变化的动画
      )
    }

    AnimatedVisibility(
      visible = isVisible,
      enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
      exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
    ) {
      Text(
        text = quote?.from?.let { "—— $it" } ?: "",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        modifier = Modifier.animateContentSize()
      )
    }
  }
}

@Composable
fun TodayCoursesList(
  todayCourses: List<Course>,
  dark: Boolean = isSystemInDarkTheme()
) {
  val ctx = LocalContext.current
  var previousIcon = 0  // 用于判断是否需要显示早午晚标题
  todayCourses.forEach {
    val icon = when (it.period) {
      CoursePeriod.MORNING -> MORNING_ICON
      CoursePeriod.AFTERNOON -> AFTERNOON_ICON
      CoursePeriod.EVENING -> EVENING_ICON
    }

    if (icon != previousIcon) {
      // 课程区间变化了，显示早午晚标题

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // 早午晚标题
        Text(
          when (it.period) {
            CoursePeriod.MORNING -> ctx.getString(R.string.morning_courses)
            CoursePeriod.AFTERNOON -> ctx.getString(R.string.afternoon_courses)
            CoursePeriod.EVENING -> ctx.getString(R.string.evening_courses)
          },
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))

        // 这是一条可爱的分割线
        Surface(
          modifier = Modifier
            .height(1.dp)
            .fillMaxWidth(),
          color = if (dark) Color.DarkGray else Color.LightGray
        ) {}
      }
      previousIcon = icon
    }

    Box(
      modifier = Modifier
        .height(64.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          // 课程开始和结束时间
          Column(modifier = Modifier.width(IntrinsicSize.Min)) {
            Text(
              it.start,
              fontFamily = FontFamily(Font(R.font.roboto_numeric)),
              fontWeight = FontWeight.Medium
            )
            Text(
              it.end,
              fontFamily = FontFamily(Font(R.font.roboto_numeric)),
              fontWeight = FontWeight.Medium
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          // 课程颜色
          Surface(
            modifier = Modifier
              .width(4.dp)
              .height(40.dp)
              .clip(RoundedCornerShape(4.dp)),
            color = Color(HashColorHelper.calcColor(it.name, dark))
          ) {}
          Spacer(modifier = Modifier.width(8.dp))
          // 使用剩余权重，截断多余文本，显示省略号
          Column(modifier = Modifier.weight(1f)) {
            // 课程名称
            Text(
              Pangu.spacingText(it.name),
              style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            // 课程地点
            Text(
              locationToAnnotated(it.location),
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          painter = painterResource(icon),
          contentDescription = "Time Delimiter",
        )
      }
    }
  }
}

@Composable
fun DaySelector(
  viewModel: CoursesViewModel,
  showJumpDialog: () -> Unit,
  dateState: MutableStateFlow<String>,
  dark: Boolean = isSystemInDarkTheme(),
) {
  val ctx = LocalContext.current
  val dateId by dateState.collectAsState()

  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      viewModel.thisWeekDates(dateId).forEach {
        // 简单解包
        val (pack1, courseCount) = it
        val (thatDate, thatDateId) = pack1

        val backgroundColor = if (thatDateId == dateId) {
          if (dark) Color.DarkGray else Color.LightGray
        } else Color.Transparent

        Box(
          modifier = Modifier
            .weight(1f) // 每个子项占相等比例的空间
            .aspectRatio(1f) // 保持正方形
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable {
              dateState.value = thatDateId
            },
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              thatDate.dayOfMonth.toString(),
              // style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              "${
                thatDate.dayOfWeek.getDisplayName(
                  TimeTextStyle.SHORT,
                  Locale.getDefault()
                )
              } $courseCount",
              style = TextStyle(fontSize = 8.sp)
            )
          }
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      PrevNextButton(
        icon = R.drawable.ic_fluent_chevron_left_20_filled,
        onClick = { dateState.value = viewModel.prevWeek(dateId) }
      )
      val thisWeekNum = viewModel.thisWeek(dateId)
      Text(
        if (thisWeekNum == -1) ctx.getString(R.string.in_vacation)
        else
          ctx.getString(
            if (viewModel.isToday(dateId)) R.string.current_week
            else R.string.current_week_navigated
          ).format(thisWeekNum),

        modifier = Modifier.clickable {
          showJumpDialog()
        }
      )

      if (!viewModel.isToday(dateId)) {
        Text(
          modifier = Modifier.clickable {
            dateState.value = viewModel.backToday()
          },
          text = ctx.getString(R.string.week_jump_today),
          color = MaterialTheme.colorScheme.secondary,
          fontWeight = FontWeight.Bold
        )
      }

      PrevNextButton(
        icon = R.drawable.ic_fluent_chevron_right_20_filled,
        onClick = { dateState.value = viewModel.nextWeek(dateId) }
      )
    }
  }
}

@Composable
fun PrevNextButton(
  icon: Int,
  onClick: () -> Unit
) {
  IconButton(
    onClick = onClick
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = "Prev/Next Button",
    )
  }
}

@Composable
fun NoCoursesSplash(dateId: String) {
  val ctx = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(48.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Image(
      painter = painterResource(
        listOf(
          R.drawable.neko1,
          R.drawable.neko2,
          R.drawable.neko3
        )[dateId.hashCode().absoluteValue % 3]
      ),
      contentDescription = "No Courses",
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      ctx.getString(R.string.no_course),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    Spacer(modifier = Modifier.height(16.dp))
    ImportCoursesButton()
  }
}

@Composable
fun ImportCoursesSplash() {
  val ctx = LocalContext.current
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(R.drawable.ic_fluent_arrow_download_24_regular),
            contentDescription = "Import Courses",
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            ctx.getString(R.string.import_courses),
            style = MaterialTheme.typography.headlineMedium
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(ctx.getString(R.string.import_courses_splash_1))
        Text(ctx.getString(R.string.import_courses_splash_2))
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center
        ) {
          ImportCoursesButton()
        }
      }
    }
  }
}

@Composable
fun ImportCoursesButton(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  Button(
    modifier = modifier,
    onClick = {
      val intent = Intent(context, ImportCoursesActivity::class.java)
      context.startActivity(intent)
    }) { Text(context.getString(R.string.import_courses)) }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekJumpDialog(
  viewModel: CoursesViewModel,
  onDismissRequest: () -> Unit,
  dateState: MutableStateFlow<String>
) {
  val datePickerState = rememberDatePickerState(
    selectableDates = object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val selectedDate = java.time.LocalDate.ofEpochDay(utcTimeMillis / 86400000)
          .format(viewModel.formatter)
        return viewModel.isDateInTerm(selectedDate)
      }
    }
  )
  val selectedDate = datePickerState.selectedDateMillis?.let {
    val date = java.time.LocalDate.ofEpochDay(it / 86400000)
    date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
  } ?: ""

  // 切换至第几周的对话框
  Dialog(
    properties = DialogProperties(usePlatformDefaultWidth = false),
    onDismissRequest = onDismissRequest
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier
          .padding(16.dp)
          .clip(RoundedCornerShape(8.dp))
      ) {
        DialogTitle(
          R.drawable.ic_fluent_calendar_24_regular,
          stringResource(R.string.week_jump)
        )

        Spacer(modifier = Modifier.height(8.dp))

        DatePicker(
          state = datePickerState,
          showModeToggle = false,
          modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
        )


        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(
            onClick = {
              dateState.value = viewModel.backToday()
              onDismissRequest()
            }
          ) {
            Text(stringResource(R.string.week_jump_today))
          }
          Spacer(modifier = Modifier.width(8.dp))
          TextButton(
            onClick = {
              onDismissRequest()
            }
          ) {
            Text(stringResource(R.string.cancel))
          }
          Spacer(modifier = Modifier.width(8.dp))
          TextButton(
            onClick = {
              if (viewModel.isDateInTerm(selectedDate)) {
                dateState.value = selectedDate
                onDismissRequest()
              }
            }
          ) {
            Text(stringResource(R.string.confirm))
          }
        }
      }
    }
  }
}

fun locationToAnnotated(text: String): AnnotatedString {
  return if (text.endsWith("浑南)") || text.endsWith("南湖)")) {
    val location = text.substringBefore(" (")
    val campus = text.substringAfter("(").substringBefore(")")
    buildAnnotatedString {
      withStyle(style = SpanStyle(fontSize = 14.sp)) {
        append(location)
      }
      withStyle(
        style = SpanStyle(
          fontSize = 14.sp,
          color = Color.Gray
        )
      ) {
        append("（")
        append(campus)
        append("）")
      }
    }
  } else {
    buildAnnotatedString {
      withStyle(style = SpanStyle(fontSize = 14.sp)) {
        append(text)
      }
    }
  }
}