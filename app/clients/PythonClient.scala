package clients

import javax.inject._
import play.api.libs.json._
import play.api.libs.ws._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PythonClient @Inject()(
                              ws: WSClient
                            )(implicit ec: ExecutionContext){
  private val pythonUrl = "http://localhost:8000/chat"

  def sendMessage(message: String): Future[JsValue] = {
    ws.url(pythonUrl)
      .post(
        Json.obj(
          "message" -> message
        )
      )
      .map(_.json)
  }
}
