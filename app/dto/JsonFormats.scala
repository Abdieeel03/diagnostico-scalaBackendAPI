package dto

import play.api.libs.json._

object JsonFormats {
  implicit val chatRequestFormat: OFormat[ChatRequest] =
    Json.format[ChatRequest]
}
