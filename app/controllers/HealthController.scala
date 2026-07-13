package controllers

import javax.inject._
import play.api.libs.json._
import play.api.mvc._

@Singleton
class HealthController @Inject()(val controllerComponents: ControllerComponents)
  extends BaseController {

  def health: Action[AnyContent] = Action {
    Ok(Json.obj(
      "success" -> true,
      "message" -> "Scala Backend API funcionando correctamente",
      "data" -> Json.obj(
        "service" -> "scala-backend-api",
        "version" -> "1.0.0",
        "status" -> "ok"
      )
    ))
  }
}
