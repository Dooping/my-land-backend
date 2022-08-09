package routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.pattern.StatusReply.{Error, Success}
import akka.pattern.{StatusReply, ask}
import akka.util.Timeout
import protocols.{ObjectTypeJsonProtocol, TemplateJsonProtocol}
import utils.JwtHelper.admin

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object TemplateRoute extends ObjectTypeJsonProtocol with TemplateJsonProtocol {

  import actors.Template._

  implicit val timeout: Timeout = Timeout(3 seconds)

  def route(authenticator: ActorRef, template: ActorRef, authPayload: (String, Boolean)): Route = {
    path("template") {
      (get & parameter(Symbol("locale").as[String].withDefault("en"))) { locale =>
        val templateFuture = (template ? GetObjectTypeOptions(authPayload._1, locale))
          .mapTo[ObjectTypeOptionsResponse]
        complete(templateFuture)
      } ~
      admin(authPayload) {
        (post & entity(as[RegisterNewLandTemplate])) { registerCommand =>
          val templateFuture = (template ? registerCommand)
            .mapTo[StatusReply[Any]]
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = reason.getMessage)
            }
          complete(templateFuture)
        } ~
        (put & entity(as[ChangeLandTemplate])) { putCommand =>
          val templateFuture = (template ? putCommand)
            .mapTo[StatusReply[Any]]
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = reason.getMessage)
            }
          complete(templateFuture)
        } ~
        (delete & parameter(Symbol("locale").as[String], Symbol("name").as[String])) { (locale, name) =>
          val templateFuture = (template ? DeleteLandTemplate(locale, name))
            .mapTo[StatusReply[Any]]
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = reason.getMessage)
            }
          complete(templateFuture)
        }
      }
    }
  }

}
