package ink.chyk.neuqrcode.activities

import android.content.*
import android.os.*
import android.util.*
import android.widget.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.*
import androidx.lifecycle.viewmodel.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.*
import ink.chyk.neuqrcode.screens.*
import ink.chyk.neuqrcode.viewmodels.*

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
        "一码通",
        R.drawable.ic_fluent_qr_code_24_filled,
        "ecode"
      ),
      BottomNavigationItem(
        "课程表",
        R.drawable.ic_fluent_calendar_24_filled,
        "courses"
      ),
      BottomNavigationItem(
        "个人信息",
        R.drawable.ic_fluent_person_24_filled,
        "profile"
      )
    )
  }
}

@Composable
fun MainApp(screen: String?) {
  val eCodeViewModel: ECodeViewModel = viewModel(factory = ECodeViewModelFactory())
  val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory())
  val coursesViewModel: CoursesViewModel = viewModel(factory = CoursesViewModelFactory())

  val navController = rememberNavController()
  var selectedItem by remember { mutableIntStateOf(0) }

  LaunchedEffect(navController) {
    navController.currentBackStackEntryFlow.collect { backStackEntry ->
      // 根据当前的目的地更新底部导航栏的选中项
      selectedItem = when (backStackEntry.destination.route) {
        "ecode" -> 0
        "courses" -> 1
        "profile" -> 2
        else -> 0
      }
    }
  }

  AppBackground(
    bottomBar = {
      NavigationBar {
        BottomNavigationItem().navigationItems().forEachIndexed { index, item ->
          NavigationBarItem(
            selected = index == selectedItem,
            label = { Text(item.label) },
            icon = { Icon(painter = painterResource(item.icon), contentDescription = item.label) },
            onClick = {
              if (selectedItem != index) {
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
  ) {
    NavHost(navController = navController, startDestination = screen ?: "ecode") {
      composable("ecode") {
        ECodeScreen(viewModel = eCodeViewModel, navController = navController)
      }
      composable("courses") {
        CoursesScreen(viewModel = coursesViewModel, navController = navController)
      }
      composable("profile") {
        ProfileScreen(viewModel = profileViewModel, navController = navController)
      }
    }
  }
}