package ink.chyk.neuqrcode.activities

import android.content.*
import android.os.*
import android.util.*
import android.widget.*
import android.app.ActivityManager
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.*
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.*
import ink.chyk.neuqrcode.screens.*
import ink.chyk.neuqrcode.viewmodels.*
import ink.chyk.neuqrcode.R
import ink.chyk.neuqrcode.ui.theme.*
import kotlin.math.*

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 初始化 mmkv
    MMKV.initialize(this)

    val mmkv = MMKV.defaultMMKV()
    val screen = intent.getStringExtra("screen")
    Log.d("MainActivity", "screen: $screen")

    // 检查是否登录？
    if (!mmkv.containsKey("student_id")) {
      Toast.makeText(this, "请先登录校园账号。", Toast.LENGTH_SHORT).show()
      val intent = Intent(this, LoginActivity::class.java)
      startActivity(intent)
      finish()
      return
    }

    // 检查是否同意服务条款？
    if ((!mmkv.containsKey("tos")) || (mmkv.decodeInt("tos") < 220)) {
      val intent = Intent(this, TermsOfServiceActivity::class.java)
      startActivity(intent)
      finish()
      return
    }

    enableEdgeToEdge()
    setContent {
      MainApp(screen)
    }
  }
}


data class BottomNavigationItem(
  val label: String = "",
  val icon: Int = 0,
  val route: String = ""
) {
  fun navigationItems(): List<BottomNavigationItem> {
    return listOf(
      BottomNavigationItem(
        "课表",
        R.drawable.ic_fluent_calendar_24_filled,
        "courses"
      ),
      BottomNavigationItem(
        "一码通",
        R.drawable.ic_fluent_qr_code_24_filled,
        "ecode"
      ),
      BottomNavigationItem(
        "应用",
        R.drawable.ic_fluent_apps_24_filled,
        "apps"
      ),
      BottomNavigationItem(
        "我的",
        R.drawable.ic_fluent_person_24_filled,
        "profile"
      )
    )
  }
}

fun routeToIndex(route: String): Int {
  return when (route) {
    "courses" -> 0
    "ecode" -> 1
    "apps" -> 2
    "profile" -> 3
    else -> 1
  }
}

fun enter(
  previousItem: Int = 0,
  selectedItem: Int = 0
): EnterTransition {
  val time = log2((abs(previousItem - selectedItem) + 1).toFloat()).toInt() * 300
  return if (previousItem < selectedItem) {
    slideInHorizontally(
      initialOffsetX = { it },
      animationSpec = tween(durationMillis = time)
    )
  } else {
    slideInHorizontally(
      initialOffsetX = { -it },
      animationSpec = tween(durationMillis = time)
    )
  }
}

fun exit(
  previousItem: Int = 0,
  selectedItem: Int = 0
): ExitTransition {
  val time = log2((abs(previousItem - selectedItem) + 1).toFloat()).toInt() * 300
  return if (previousItem < selectedItem) {
    slideOutHorizontally(
      targetOffsetX = { -it },
      animationSpec = tween(durationMillis = time)
    )
  } else {
    slideOutHorizontally(
      targetOffsetX = { it },
      animationSpec = tween(durationMillis = time)
    )
  }
}

@Composable
fun MainApp(screen: String?) {
  val ctx = LocalContext.current
  val onFailed = {
    // 检查应用是否在前台
    val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val appProcesses = activityManager.runningAppProcesses ?: emptyList()
    val isAppInForeground = appProcesses.any {
      it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
              it.processName == ctx.packageName
    }

    // 只有在前台时才显示错误，防止锁屏或者后台
    if (isAppInForeground) {
      val intent = Intent(ctx, ErrorActivity::class.java)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.startActivity(intent)
    }
    
    true
  }
  val eCodeViewModel: ECodeViewModel = viewModel(factory = ECodeViewModelFactory(onFailed))
  val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(onFailed))
  val coursesViewModel: CoursesViewModel = viewModel(factory = CoursesViewModelFactory())

  val navController = rememberNavController()
  var previousSelectedItem by remember { mutableStateOf(0) }
  var selectedItem by remember { mutableIntStateOf(0) }

  LaunchedEffect(navController) {
    navController.currentBackStackEntryFlow.collect { backStackEntry ->
      // 根据当前的目的地更新底部导航栏的选中项
      selectedItem = routeToIndex(backStackEntry.destination.route ?: "ecode")
    }
  }

  AppTheme {
    Scaffold(
      bottomBar = {
        NavigationBar {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp)
          ) {
            BottomNavigationItem().navigationItems().forEachIndexed { index, item ->
              NavigationBarItem(
                selected = index == selectedItem,
                label = { Text(item.label) },
                icon = {
                  Icon(
                    painter = painterResource(item.icon),
                    contentDescription = item.label
                  )
                },
                onClick = {
                  if (selectedItem != index) {
                    previousSelectedItem = selectedItem
                    selectedItem = index
                    navController.navigate(item.route) {
                      // https://medium.com/@bharadwaj.rns/bottom-navigation-in-jetpack-compose-using-material3-c153ccbf0593
                      popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                      }
                      launchSingleTop = true
                      restoreState = true
                    }
                  }
                }
              )
            }
          }
        }
      }
    ) { innerPadding ->
      NavHost(navController = navController, startDestination = screen ?: "ecode") {
        composable(
          "courses",
          enterTransition = { enter(previousSelectedItem, selectedItem) },
          exitTransition = { exit(previousSelectedItem, selectedItem) }
        ) {
          CoursesScreen(
            viewModel = coursesViewModel,
            navController = navController,
            innerPadding = innerPadding
          )
        }
        composable(
          "ecode",
          enterTransition = { enter(previousSelectedItem, selectedItem) },
          exitTransition = { exit(previousSelectedItem, selectedItem) }
        ) {
          ECodeScreen(viewModel = eCodeViewModel, navController = navController)
        }
        composable(
          "apps",
          enterTransition = { enter(previousSelectedItem, selectedItem) },
          exitTransition = { exit(previousSelectedItem, selectedItem) }
        ) {
          AppsScreen(navController = navController)
        }
        composable(
          "profile",
          enterTransition = { enter(previousSelectedItem, selectedItem) },
          exitTransition = { exit(previousSelectedItem, selectedItem) }
        ) {
          ProfileScreen(
            viewModel = profileViewModel,
            navController = navController,
            innerPadding = innerPadding
          )
        }
      }
    }
  }
}