package clients

import javax.inject._
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class PythonClient @Inject()(
                              ws: WSClient,
                              config: Configuration
                            )(implicit ec: ExecutionContext){
  private val pythonUrl : String = config.get[String]("python.engine.url")

  def sendMessage(message: String, sessionId: Option[String]): Future[JsValue] = {
    ws.url(s"$pythonUrl/chat")
      .withRequestTimeout(60.seconds)
      .post(
        Json.obj(
          "message" -> message,
          "session_id" -> sessionId
        )
      )
      .map { response =>
        if (response.status >= 200 && response.status < 300) {
          response.json
        } else {
          Json.obj(
            "success" -> false,
            "message" -> s"Python engine respondió con error ${response.status}",
            "data" -> JsNull
          )
        }
      }
      .recover {
        case e: Exception =>
          Json.obj(
            "success" -> false,
            "message" -> s"Error de conexión con Python engine: ${e.getMessage}",
            "data" -> JsNull
          )
      }
  }
}
