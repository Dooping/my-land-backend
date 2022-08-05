package routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import protocols.LandJsonProtocol
import akka.http.scaladsl.server.Directives._
import akka.pattern.StatusReply._
import akka.pattern.{StatusReply, ask}
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object LandRoute extends LandJsonProtocol {
  import actors.Land._
  import actors.UserManagement.LandCommand

  implicit val timeout: Timeout = Timeout(3 seconds)

  def route(authenticator: ActorRef, username: String): Route = {
    pathPrefix("land") {
      path(IntNumber) { id =>
        ObjectTypeRoute.route(authenticator, username, id)
      } ~
      get {
        (parameter(Symbol("id").as[Int]) | path(IntNumber)) { id =>
          val getLandFuture = (authenticator ? LandCommand(username, GetLand(id)))
            .mapTo[Option[LandEntity]]
          complete(getLandFuture)
        } ~
        pathEndOrSingleSlash {
          val getLandsFuture = (authenticator ? LandCommand(username, GetAllLands))
            .mapTo[List[LandEntity]]
          complete(getLandsFuture)
        }
      } ~
      (post & pathEndOrSingleSlash & entity(as[AddLand])) { addLandCommand =>
        val addLandFuture = (authenticator ? LandCommand(username, addLandCommand))
          .mapTo[StatusReply[LandEntity]]
          .map {
            case Success(_) => HttpResponse(StatusCodes.Created)
            case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
          }
        complete(addLandFuture)
      } ~
      (patch & pathEndOrSingleSlash) {
        entity(as[ChangeLandDescription]) { landDescriptionCmd =>
          val patchLandDescription = (authenticator ? LandCommand(username, landDescriptionCmd))
            .mapTo[StatusReply[LandEntity]]
            .map(mapStatusReplyToHttpResponse)
          complete(patchLandDescription)
        } ~
        entity(as[ChangePolygon]) { polygonCmd =>
          val patchPolygon = (authenticator ? LandCommand(username, polygonCmd))
            .mapTo[StatusReply[LandEntity]]
            .map(mapStatusReplyToHttpResponse)
          complete(patchPolygon)
        }
      }
    }
  }

  def mapStatusReplyToHttpResponse(reply: StatusReply[LandEntity]): HttpResponse = reply match {
    case Success(land: LandEntity) => HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, land.toJson.prettyPrint))
    case Success(_) => HttpResponse(StatusCodes.OK)
    case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
  }

}
