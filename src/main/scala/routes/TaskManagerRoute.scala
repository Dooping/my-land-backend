package routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply._
import akka.pattern.{StatusReply, ask}
import akka.util.Timeout
import protocols.TaskJsonProtocol
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object TaskManagerRoute extends TaskJsonProtocol {
  import actors.TaskManager._
  import actors.UserManagement.TaskCommand

  implicit val timeout: Timeout = Timeout(3 seconds)

  def route(authenticator: ActorRef, username: String): Route = {
    pathPrefix("task") {
      (get & pathEndOrSingleSlash) {
        parameter(Symbol("query").as[String].withDefault("all")) { query =>
          val command = query match {
            case "season" => GetSeasonTasks
            case "open" => GetOpenTasks
            case _ => GetAllTasks
          }
          val getTaskFuture = (authenticator ? TaskCommand(username, command))
            .mapTo[List[TaskEntity]]
          complete(getTaskFuture)
        }
      } ~
      pathPrefix(IntNumber) { id =>
        put {
          (entity(as[TaskModel]) & pathEndOrSingleSlash) { model =>
            val changeTaskFuture = (authenticator ? TaskCommand(username, ModifyTask(id, model)))
              .mapTo[StatusReply[TaskEntity]]
              .map(mapToEntity)
            complete(changeTaskFuture)
          } ~
          path("complete") {
            val changeTaskFuture = (authenticator ? TaskCommand(username, CompleteTask(id)))
              .mapTo[StatusReply[TaskEntity]]
              .map(mapToEntity)
            complete(changeTaskFuture)
          } ~
          path("archive") {
            val changeTaskFuture = (authenticator ? TaskCommand(username, ArchiveTask(id)))
              .mapTo[StatusReply[TaskEntity]]
              .map(mapToEntity)
            complete(changeTaskFuture)
          }
        } ~
        (delete & pathEndOrSingleSlash) {
          val deleteTaskFuture = (authenticator ? TaskCommand(username, DeleteTask(id)))
            .map {
              case Success(_) => HttpResponse(StatusCodes.OK)
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(deleteTaskFuture)
        }
      }
    }
  }

  def landRoute(authenticator: ActorRef, username: String, landId: Int): Route = {
    path("task") {
      (parameter(Symbol("object").as[Int]) & get) { objectId =>
        val getTaskFuture = (authenticator ? TaskCommand(username, GetLandObjectTasks(landId, objectId)))
          .mapTo[List[TaskEntity]]
        complete(getTaskFuture)
      } ~
      get {
        val getTaskFuture = (authenticator ? TaskCommand(username, GetLandTasks(landId)))
          .mapTo[List[TaskEntity]]
        complete(getTaskFuture)
      } ~
      post {
        entity(as[TaskModel]) { task =>
          val createTaskFuture = (authenticator ? TaskCommand(username, CreateTask(landId, task)))
            .mapTo[StatusReply[TaskEntity]]
            .map {
              case Success(entity: TaskEntity) =>
                HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(createTaskFuture)
        } ~
        entity(as[List[TaskModel]]) { tasks =>
          val createTasksFuture = (authenticator ? TaskCommand(username, CreateTasks(landId, tasks)))
            .mapTo[StatusReply[List[TaskEntity]]]
            .map {
              case Success(entities: List[TaskEntity]) =>
                HttpResponse(StatusCodes.Created, entity = HttpEntity(ContentTypes.`application/json`, entities.toJson.prettyPrint))
              case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
            }
          complete(createTasksFuture)
        }
      }
    }
  }

  def mapToEntity(response: StatusReply[TaskEntity]): HttpResponse = response match {
    case Success(entity: TaskEntity) => HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, entity.toJson.prettyPrint))
    case Error(reason) => HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(reason.getMessage))
  }
}
