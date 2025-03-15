package ink.chyk.neuqrcode.neu

data class DeepSeekResponse<T>(
    val e: Int,
    val d: T,
    val m: String
)