package routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply._
import akka.pattern.{StatusReply, ask}
import akka.util.Timeout
import protocols.ObjectTypeJsonProtocol
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object ObjectTypeRoute extends ObjectTypeJsonProtocol {
  import actors.ObjectType._
  import actors.Land.LandObjectTypesCommand
  import actors.UserManagement.LandCommand

  implicit val timeout: Timeout = Timeout(5 seconds)

  def route(authenticator: ActorRef, username: String, landId: Int): Route = {
    pathPrefix("objectType") {
      (get & pathEndOrSingleSlash) {
        val getObjectTypeFuture = (authenticator ? LandCommand(username, LandObjectTypesCommand(landId, GetObjectTypes)))
          .mapTo[List[ObjectTypeEntity]]
        complete(getObjectTypeFuture)
      } ~
      (post & pathEndOrSingleSlash & entity(as[ObjType])) { objType =>
        val addObjectTypeFuture = (authenticator ? LandCommand(username, LandObjectTypesCommand(landId, AddObjectType(objType))))
          .mapTo[StatusReply[ObjectTypeEntity]]
          .map {
            case Success(entity: ObjectTypeEntity) =>
              HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
            case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
          }
        complete(addObjectTypeFuture)
      } ~
      path(IntNumber) { id =>
        put {
          entity(as[ObjType]) { objectType =>
            val patchObjectType = (authenticator ? LandCommand(username, LandObjectTypesCommand(landId, ChangeObjectType(id, objectType))))
              .mapTo[StatusReply[ObjectTypeEntity]]
              .map {
                case Success(entity: ObjectTypeEntity) => HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
                case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
              }
            complete(patchObjectType)
          }
        } ~
        delete {
          val deleteObjectType = (authenticator ? LandCommand(username, LandObjectTypesCommand(landId, DeleteObjectType(id))))
            .mapTo[StatusReply[Any]]
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(deleteObjectType)
        }
      }

    }
  }
}
