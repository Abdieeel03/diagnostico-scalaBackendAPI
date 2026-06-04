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
        errors => {
          scala.concurrent.Future.successful(
            BadRequest(Json.obj("message" -> "Invalid request"))
          )
        },
        chatRequest => {
          chatService.processMessage(chatRequest.message)
            .map(response => Ok(response))
        }
      )
  }
}