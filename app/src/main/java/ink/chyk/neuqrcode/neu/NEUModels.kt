package ink.chyk.neuqrcode.neu

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class UserSSOLoginResponse(
  val code: Int,
  val result: JsonElement, // 直接接收原始 JSON 元素
  val msg: String
) {
  val tgt: String? by lazy {
    when {
      result is JsonObject -> result.jsonObject["tgt"]?.jsonPrimitive?.content
      result is JsonArray && result.isEmpty() -> null
      else -> throw IllegalStateException("Unexpected result format")
    }
  }
}

@Serializable
data class PersonalSession(
  val lc: String,
  val vl: String,
  val sessId: String,
)

@Serializable
data class NEUAppSession(
  val session: String,
  val xsrfToken: String,
  val expiredAt: Long
)

@Serializable
data class ListedResponse<T>(
  val data: List<ResponseData<T>>
)

@Serializable
data class ResponseData<T>(
  val type: Nothing? = null,
  val attributes: T
)

@Serializable
data class ECodeResponse(
  val qrCode: String,
  val createTime: Long,
  val qrInvalidTime: Long
)

@Serializable
data class ECodeUserInfoResponse(
  val userCode: String,
  val userName: String,
  val unitName: String,
  val idType: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class PersonalResponse<T>(
  val e: Int,  // code
  val m: String,  // message
  val d: T  // data
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class UserInfoOuter(
  val info: UserInfo
)

@Serializable
data class UserInfo(
  val uid: String,
  val name: String,
  val xgh: String, // 学工号
  val identity: String,
  val identity_id: String,
  val sex: Int,
  val depart: String,
  val mobile: String,
  val email: String,
  val organ: JsonElement,  // 不明
  val organs: JsonElement,  // 不明
  val avatar: String,  // 其实这个才是真正的 avatar_url
  val avatar_url: String,  // 这个是空字符串，意义不明
  val time: String,
  val is_manager: Boolean
)

@Serializable
data class PersonalDataIdOuter(
  val data: List<PersonalDataId>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class PersonalDataId(
  val key: String,
  val id: String,
  // 只需要这两个字段
)

@Serializable
data class PersonalDataItemOuter(
  val data: PersonalDataItem
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class PersonalDataItem(
  val value: JsonElement,
  val unit: String? = null,
  val url: String? = null,
) {
  val valueString: String? by lazy {
    value.jsonPrimitive.content
  }
}

@Serializable
data class UploadImageResponse(
  val url: String,
  val name: String,
  val type: String,
  val size: Int,
  val id: String
)


// 课程信息（在 CourseFetcher 中用）
data class ImportCourse(
  val id: String,
  var name: String,
  val roomId: String,
  var roomName: String,
  val weeks: String,

  // 一节课的时长可能是 2 节或 4 节
  // 所以 courseTimes 里可能有多个时间
  val courseTimes: MutableList<CourseTime> = mutableListOf(),

  // 在 CourseImporter 预处理步骤中赋值
  // 合并为一整个时间段
  var startTime: String? = null,
  var endTime: String? = null,
  var weekDayDelta: Int? = null,
  var period: CoursePeriod? = null,
)

// 课程具体时间，周几第几节
data class CourseTime(
  val dayOfTheWeek: Int,
  val timeOfTheDay: Int,
)

// 解析好的每一天的课程信息（转为 JSON 存到数据库中）
@Serializable
data class Course(
  val name: String,
  val location: String,
  val start: String,
  val end: String,
  val period: CoursePeriod
)

enum class CoursePeriod {
  MORNING, AFTERNOON, EVENING
}

@Serializable
data class MailListResponse(
  @Serializable(with = MailListTransformer::class)
  val list: List<MailDetails>,
  val num: String,
  val url: String
)

object MailListTransformer : JsonTransformingSerializer<List<MailDetails>>(ListSerializer(MailDetails.serializer())) {
  override fun transformDeserialize(element: JsonElement): JsonElement {
    return if (element is JsonArray) {
      element
    } else {
      JsonArray(listOf(element))
    }
  }
}

@Serializable
data class MailDetails(
  val mid: String,
  val msid: String? = null,
  val fid: String,
  val flag: String,
  @Serializable(with = StringOrArraySerializer::class)
  val from: String,
  @Serializable(with = StringOrArraySerializer::class)
  val to: String,
  val subject: String,
  val size: String,
  val date: String,
)

// generated with deepseek
// 我还不是很了解 kotlinx.serialization 的自定义序列化器

object StringOrArraySerializer : KSerializer<String> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrArray", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: String) {
    encoder.encodeString(value)
  }

  override fun deserialize(decoder: Decoder): String {
    return when (val element = decoder.decodeSerializableValue(JsonElement.serializer())) {
      is JsonArray -> element.joinToString(" ") { it.jsonPrimitive.content }
      is JsonPrimitive -> element.content
      else -> throw SerializationException("Expected String or Array for field")
    }
  }
}