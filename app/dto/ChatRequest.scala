package dto

case class ChatRequest(
  message: String,
  client_id: String,
  client_msg_id: Option[String] = None
)
