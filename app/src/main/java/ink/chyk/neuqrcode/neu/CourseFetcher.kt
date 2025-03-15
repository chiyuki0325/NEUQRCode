package ink.chyk.neuqrcode.neu

import android.util.*
import com.tencent.mmkv.*
import ink.chyk.neuqrcode.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.*
import java.util.regex.*
import kotlin.Pair

class CourseFetcher(
  private val neu: NEUPass,
  private val mmkv: MMKV
) {
  /*
    NEUQRCode EAMS Course Fetcher v2
    2025-03-05

    credits:
    - https://github.com/neucn/elise
    - https://github.com/whoisnian/getMyCourses
  */

  private val courseTableUrl = "http://219.216.96.4/eams/courseTableForStd.action"
  private val courseTableActionUrl =
    "http://219.216.96.4/eams/courseTableForStd!courseTable.action"
  private val eamsHomeUrl = "http://219.216.96.4/eams/homeExt.action"

  private var session: Pair<String, String>? = null  // 在此 class 内全局可用
  private val sb: StringBuilder = StringBuilder()

  suspend fun fetchCourses(): CourseFetcherResult {
    // 登录教务系统
    val html: String
    try {
      session = newSession()
      //Log.d("CourseFetcher", "Session: $session")
      val portalTicket = getPortalTicket(true)
      //Log.d("CourseFetcher", "Portal ticket: $portalTicket")
      val eamsTicket = loginEAMSTicket(portalTicket)
      //Log.d("CourseFetcher", "EAMS ticket: $eamsTicket")
      html = loginEAMS(eamsTicket)
    } catch (e: Exception) {
      Log.e("CourseFetcher", "Failed to login EAMS", e)
      sb.append("登录教务系统失败，请检查网络连接\n")
      sb.append(e.message)
      return CourseFetcherResult(
        output = sb.toString(),
        hasErrors = true
      )
    }

    sb.append("登录教务系统成功\n")

    // 获取日期信息
    val startDay: ZonedDateTime
    try {
      val week = getCurrentWeek(html)
      startDay = getSemesterStartDay(week)
    } catch (e: Exception) {
      Log.e("CourseFetcher", "Failed to get current week", e)
      sb.append("获取当前教学周失败\n")
      sb.append(e.message)
      return CourseFetcherResult(
        output = sb.toString(),
        hasErrors = true
      )
    }

    // 开始生成
    sb.append("\n======开始获取课程表======\n")

    // 获取课程表 html
    val courseTableHtml: String
    try {
      courseTableHtml = getCourseTablePage()
    } catch (e: Exception) {
      Log.e("CourseFetcher", "Failed to get course table page", e)
      sb.append("获取课程表失败\n")
      sb.append(e.message)
      return CourseFetcherResult(
        output = sb.toString(),
        hasErrors = true
      )
    }

    // 解析课程表
    val courses: List<ImportCourse>
    try {
      courses = parseCourses(courseTableHtml)
    } catch (e: Exception) {
      Log.e("CourseFetcher", "Failed to parse courses", e)
      sb.append("解析课程表失败\n")
      sb.append(e.message)
      return CourseFetcherResult(
        output = sb.toString(),
        hasErrors = true
      )
    }

    Log.d("CourseFetcher", "Courses: $courses")

    sb.append("获取到的课程数量: ${courses.size}\n")

    val printedCourses = mutableSetOf<String>()

    sb.append("课程: ")
    courses.forEach {
      val name = it.name
      if (!printedCourses.contains(name)) {
        sb.append("${it.name} [${it.roomName}], ")
        printedCourses.add(name)
      }
    }

    sb.delete(sb.length - 2, sb.length)

    sb.append("\n======课程表获取完毕======\n")

    return CourseFetcherResult(
      result = courses,
      startDay = startDay,
      output = sb.toString(),
      hasErrors = false
    )
  }

  // copied from BasicViewModel
  private suspend fun getPortalTicket(reLogin: Boolean = false): String {
    val studentId = mmkv.decodeString("student_id")!!
    val password = mmkv.decodeString("password")!!
    var portalTicket: String? = mmkv.decodeString("portal_ticket")
    if (portalTicket == null || reLogin) {
      portalTicket = neu.loginPortalTicket(studentId, password)
      mmkv.encode("portal_ticket", portalTicket)
    }
    return portalTicket
  }


  private suspend fun newSession(): Pair<String, String> {
    // 请求一次教务系统主页，获取一个新的 jsessionid 在登录时使用
    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .build()
    val request = Request.Builder()
      .url(eamsHomeUrl)
      .build()

    return withContext(Dispatchers.IO) {
      val response = Utils.executeRequest(client, request, "获取 session 失败")

      if (response.code == 302) {
        val headers = response.headers
        val getCookie = fun(name: String): String? {
          for (i in 0 until headers.size) {
            if (headers.name(i) == "Set-Cookie") {
              val value = headers.value(i)
              if (value.startsWith(name)) {
                return value.substringAfter("=").substringBefore(";")
              }
            }
          }
          return null
        }
        val sessionId = getCookie("JSESSIONID")
        // JSESSIONID 和 GSESSIONID 后续请求需要都带上
        // 但它们内容是一样的
        // 所以只记录一个
        val serverName = getCookie("SERVERNAME")
        // 教务系统后端有多台服务器
        // 同一个 jsessionid 只能在同一台服务器上使用
        // 所以要一并记录下服务器名
        // 后续请求都用这台服务器
        if (sessionId != null && serverName != null) {
          return@withContext Pair(sessionId, serverName)
        } else {
          throw RequestFailedException("获取 EAMS session 失败")
        }
      } else {
        throw RequestFailedException("获取 EAMS session 失败")
      }
    }
  }


  private suspend fun loginEAMSTicket(
    portalTicket: String
  ): String {
    // 登录教务系统，获取教务系统的 ticket
    val eamsTicket =
      neu.loginNEUAppTicket(portalTicket, "$eamsHomeUrl;jsessionid=${session?.first}")
    return eamsTicket
  }

  private fun buildEAMSRequest(
    url: String
  ): Request.Builder {
    // 构建一个带 session 信息的 Request.Builder
    return Request.Builder()
      .url(url)
      .header(
        "Cookie",
        "JSESSIONID=${session?.first}; GSESSIONID=${session?.first}; SERVERNAME=${session?.second}"
      )
      .header(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36"
      )
  }

  suspend fun loginEAMS(eamsTicket: String): String {
    if (session == null) {
      throw SessionExpiredException()
    }
    val session = session!! // name shadowing

    // 初始化 OkHttpClient
    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .build()

    // 构建登录请求和后续请求
    val loginRequest =
      buildEAMSRequest("$eamsHomeUrl;jsessionid=${session.first}?ticket=$eamsTicket")
        .get()
        .build()
    val afterLoginRequest = buildEAMSRequest("$eamsHomeUrl;jsessionid=${session.first}")
      .get()
      .build()

    return withContext(Dispatchers.IO) {
      // 执行登录请求
      val loginResponse = Utils.executeRequest(client, loginRequest, "登录失败")

      // 检查是否需要重定向
      if (loginResponse.code == 302) {
        // 执行后续请求
        val afterLoginResponse = Utils.executeRequest(client, afterLoginRequest, "登录后请求失败")

        // 登录成功
        Log.d("CourseFetcher", "Login success")
        afterLoginResponse.body?.string() ?: ""
      } else {
        throw TicketExpiredException()
      }
    }
  }

  private fun getCurrentWeek(html: String): Int {
    // 获取当前周数
    // translated with DeepSeek
    if (!html.contains("教学周")) {
      throw IllegalArgumentException("获取当前教学周失败！")
    }
    val content = html.substring(html.indexOf("id=\"teach-week\">"), html.indexOf("教学周") + 10)

    val reg = Regex("学期\\s*<font size=\"\\d+px\">(\\d+)</font>\\s*教学周")
    val res = reg.find(content) ?: throw IllegalArgumentException("无法获取当前教学周")

    val week = res.groupValues[1].toIntOrNull() ?: throw IllegalArgumentException("无法解析教学周")
    sb.append("当前为第 $week 教学周\n")

    return week
  }

  private fun getSemesterStartDay(week: Int): ZonedDateTime {
    val now = ZonedDateTime.now(ZoneId.of("UTC+8"))
    val daySum = now.dayOfWeek.value + week * 7 - 7
    val termStart = now
      .withHour(0)
      .withMinute(0)
      .withSecond(0)
      .withNano(0)
      .minusDays(daySum.toLong())
    sb.append("计算得到本学期开始于 ${termStart.year}-${termStart.monthValue}-${termStart.dayOfMonth}\n")

    return termStart
  }

  private suspend fun getCourseTablePage(): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .build()

    // 第一次请求：获取 ids 和 semesterId
    val (ids, semesterId) = fetchIdsAndSemesterId(client)

    // 第二次请求：获取课程表页面
    fetchCourseTablePage(client, ids, semesterId)
  }

  /**
   * 获取 ids 和 semesterId
   */
  private suspend fun fetchIdsAndSemesterId(client: OkHttpClient): Pair<String, String> {
    val request = buildEAMSRequest(courseTableUrl).get().build()
    val response = Utils.executeRequest(client, request, "获取必要参数 ids 和 semesterId 失败")

    val html = response.body?.string() ?: throw RequestFailedException("响应体为空")
    val ids = extractIds(html) ?: throw RequestFailedException("无法提取 ids")
    val semesterId =
      extractSemesterId(response) ?: throw RequestFailedException("无法提取 semesterId")

    Log.d("CourseFetcher", "ids: $ids, semesterId: $semesterId")
    return ids to semesterId
  }

  /**
   * 提取 ids
   */
  private fun extractIds(html: String): String? {
    val IDS_MATCH = "bg.form.addInput(form,\"ids\",\""
    if (!html.contains(IDS_MATCH)) return null

    val startIndex = html.indexOf(IDS_MATCH) + IDS_MATCH.length
    val endIndex = html.indexOf("\");", startIndex)
    return html.substring(startIndex, endIndex)
  }

  /**
   * 提取 semesterId
   */
  private fun extractSemesterId(response: Response): String? {
    return response.headers["Set-Cookie"]
      ?.substringAfter("semester.id=")
      ?.substringBefore(";")
  }

  /**
   * 获取课程表页面
   */
  private suspend fun fetchCourseTablePage(
    client: OkHttpClient,
    ids: String,
    semesterId: String
  ): String {
    val requestBody =
      "ignoreHead=1&showPrintAndExport=1&setting.kind=std&startWeek=&semester.id=$semesterId&ids=$ids"
    val request = buildEAMSRequest(courseTableActionUrl)
      .post(requestBody.toRequestBody())
      .header("Content-Type", "application/x-www-form-urlencoded")
      .build()

    val response = Utils.executeRequest(client, request, "获取课程表失败")

    val html = response.body?.string() ?: throw RequestFailedException("课程表响应体为空")
    if (!html.contains("课表格式说明")) {
      throw RequestFailedException("课程表内容无效")
    }

    //Log.d("CourseFetcher", html)
    return html
  }

  // 解析课表
  // 从 elise 移植

  private fun parseCourses(html: String): List<ImportCourse> {
    val courses = mutableListOf<ImportCourse>()

    val reg1 = Pattern.compile(
      """TaskActivity\(actTeacherId.join\(','\),actTeacherName.join\(','\),"(.*)","(.*)\(.*\)","(.*)","(.*)","(.*)",null,null,assistantName,"",""\);((?:\s*index =\d+\*unitCount\+\d+;\s*.*\s)+)"""
    )
    val reg2 = Pattern.compile("""\s*index =(\d+)\*unitCount\+(\d+);\s*""")
    val matcher1 = reg1.matcher(html)
    while (matcher1.find()) {
      val course = ImportCourse(
        id = matcher1.group(1) ?: throw IllegalArgumentException("无法获取课程 ID"),
        name = matcher1.group(2) ?: throw IllegalArgumentException("无法获取课程名称"),
        roomId = matcher1.group(3) ?: throw IllegalArgumentException("无法获取教室 ID"),
        roomName = matcher1.group(4) ?: throw IllegalArgumentException("无法获取教室名称"),
        weeks = matcher1.group(5) ?: throw IllegalArgumentException("无法获取周数"),
      )
      val indexStrList = matcher1.group(6)
        ?.split("table0.activities[index][table0.activities[index].length]=activity;")
        ?: throw IllegalArgumentException("无法获取课程时间")
      for (indexStr in indexStrList) {
        if (!indexStr.contains("unitCount")) {
          continue
        }
        val matcher2 = reg2.matcher(indexStr)
        if (matcher2.find()) {
          val courseTime = CourseTime(
            dayOfTheWeek = matcher2.group(1)?.toInt()
              ?: throw IllegalArgumentException("无法获取星期"),
            timeOfTheDay = matcher2.group(2)?.toInt()
              ?: throw IllegalArgumentException("无法获取节次")
          )
          course.courseTimes.add(courseTime)
        }
      }
      courses.add(course)
    }
    return courses
  }
}