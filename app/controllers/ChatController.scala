package controllers

import dto.ChatRequest
import dto.JsonFormats._

import javax.inject._
import play.api.libs.json._
import play.api.mvc._
import services.ChatService

import scala.concurrent.ExecutionContext

@Singleton
class ChatController @Inject()(
                                val controllerComponents: ControllerComponents,
                                chatService: ChatService
                              )(implicit ec: ExecutionContext)
  extends BaseController {

  def chat: Action[JsValue] = Action.async(parse.json) { request =>

    request.body
      .validate[ChatRequest]
      .fold(
        _ => {
          scala.concurrent.Future.successful(
            BadRequest(Json.obj(
              "success" -> false,
              "message" -> "Solicitud inválida: falta el campo 'message'",
              "data" -> JsNull
            ))
          )
        },
        chatRequest => {
          chatService.processMessage(chatRequest.message, chatRequest.session_id)
            .map(response => Ok(response))
            .recover {
              case e: Exception =>
                InternalServerError(Json.obj(
                  "success" -> false,
                  "message" -> s"Error interno: ${e.getMessage}",
                  "data" -> JsNull
                ))
            }
        }
      )
  }
}