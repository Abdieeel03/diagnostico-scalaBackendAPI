package services

import javax.inject._
import clients.PythonClient
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ChatService @Inject()(
                           pythonClient: PythonClient
                           )(implicit ec: ExecutionContext) {
  def processMessage(message: String): Future[JsValue] = {
    pythonClient.sendMessage(message)
  }
}
