package clients

import javax.inject._
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PythonClient @Inject()(
                              ws: WSClient,
                              config: Configuration
                            )(implicit ec: ExecutionContext){
  private val pythonUrl : String = config.get[String]("python.engine.url")

  def sendMessage(message: String): Future[JsValue] = {
    ws.url(s"$pythonUrl/chat")
      .post(
        Json.obj(
          "message" -> message
        )
      )
      .map(_.json)
  }
}
