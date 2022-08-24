package routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply._
import akka.pattern.{StatusReply, ask}
import akka.util.Timeout
import protocols.ObjectManagerJsonProtocol
import shapeless.syntax.std.tuple.productTupleOps
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object LandObjectRoute extends ObjectManagerJsonProtocol {
  import actors.Land.LandObjectsCommand
  import actors.UserManagement.LandCommand
  import actors.ObjectManager._

  implicit val timeout: Timeout = Timeout(3 seconds)

  def route(authenticator: ActorRef, username: String, landId: Int): Route = {
    path("object") {
      get {
        val getObjectsFuture = (authenticator ? LandCommand(username, LandObjectsCommand(landId, GetObjects)))
          .mapTo[List[LandObject]]
        complete(getObjectsFuture)
      } ~
      (post & entity(as[AddLandObject])) { command =>
        val postObjectsFuture = (authenticator ? LandCommand(username, LandObjectsCommand(landId, command)))
          .mapTo[StatusReply[LandObject]]
          .map {
            case Success(entity: LandObject) =>
              HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
            case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
          }
        complete(postObjectsFuture)
      } ~
      path(IntNumber) { id =>
        (put & entity(as[AddLandObject])) { command =>
          val changeCommand = AddLandObject.unapply(command).map(ChangeLandObject tupled _.+:(id)).get
          val putObjectsFuture = (authenticator ? LandCommand(username, LandObjectsCommand(landId, changeCommand)))
            .mapTo[StatusReply[LandObject]]
            .map {
              case Success(entity: LandObject) =>
                HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(putObjectsFuture)
        } ~
        delete {
          val deleteObjectsFuture = (authenticator ? LandCommand(username, LandObjectsCommand(landId, DeleteLandObject(id))))
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(deleteObjectsFuture)
        }
      } ~
      (delete & parameter(Symbol("type").as[Int])) { typeId =>
        val deleteObjectsFuture = (authenticator ? LandCommand(username, LandObjectsCommand(landId, DeleteByType(typeId))))
          .map {
            case Success(_) => HttpResponse(StatusCodes.OK)
            case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
          }
        complete(deleteObjectsFuture)
      }
    }
  }

}
