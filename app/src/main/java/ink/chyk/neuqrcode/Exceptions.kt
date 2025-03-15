package ink.chyk.neuqrcode

class RequestFailedException(url: String) : Exception("请求失败: $url")
class PasswordIncorrectException : Exception("密码错误")
open class TicketFailedException(a: String = "获取 ticket 失败") : Exception(a)
class TicketExpiredException : TicketFailedException("ticket 过期")
class SessionExpiredException : Exception("session 过期")