package routes

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply._
import akka.pattern.{StatusReply, ask}
import akka.util.Timeout
import protocols.TaskTypeJsonProtocol
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object TaskTypeRoute extends TaskTypeJsonProtocol with SprayJsonSupport {
  import actors.Land.LandTaskTypesCommand
  import actors.TaskType._
  import actors.UserManagement.LandCommand

  implicit val timeout: Timeout = Timeout(3 seconds)

  def route(authenticator: ActorRef, username: String, landId: Int): Route = {
    pathPrefix("taskType") {
      (get & pathEndOrSingleSlash) {
        val getTaskTypeFuture = (authenticator ? LandCommand(username, LandTaskTypesCommand(landId, GetTaskTypes)))
          .mapTo[List[TaskTypeEntity]]
        complete(getTaskTypeFuture)
      } ~
      (post & pathEndOrSingleSlash) {
        entity(as[TaskTypeModel]) { taskType =>
          val addTaskTypeFuture = (authenticator ? LandCommand(username, LandTaskTypesCommand(landId, AddTaskType(taskType))))
            .mapTo[StatusReply[TaskTypeEntity]]
            .map {
              case Success(entity: TaskTypeEntity) =>
                HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(addTaskTypeFuture)
        } ~
        entity(as[List[TaskTypeModel]]) { taskTypes =>
          val addTaskTypesFuture = (authenticator ? LandCommand(username, LandTaskTypesCommand(landId, BatchAddTaskType(taskTypes))))
            .mapTo[StatusReply[List[TaskTypeEntity]]]
            .map {
              case Success(entities: List[TaskTypeEntity]) =>
                HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, entities.toJson.prettyPrint))
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(addTaskTypesFuture)
        }
      } ~
      path(IntNumber) { id =>
        put {
          entity(as[TaskTypeModel]) { taskType =>
            val patchTaskType = (authenticator ? LandCommand(username, LandTaskTypesCommand(landId, ChangeTaskType(id, taskType))))
              .mapTo[StatusReply[TaskTypeEntity]]
              .map {
                case Success(entity: TaskTypeEntity) => HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
                case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
              }
            complete(patchTaskType)
          }
        } ~
        delete {
          val deleteTaskType = (authenticator ? LandCommand(username, LandTaskTypesCommand(landId, DeleteTaskType(id))))
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(deleteTaskType)
        }
      }

    }
  }
}
