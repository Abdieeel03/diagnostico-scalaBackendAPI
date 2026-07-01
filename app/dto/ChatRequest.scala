package dto

case class ChatRequest(
  message: String,
  session_id: Option[String] = None
)
