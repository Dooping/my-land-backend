package actors

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.pattern.StatusReply.{Error, Success}
import akka.persistence.PersistentActor

import java.util.Date
import scala.concurrent.duration._
import scala.language.postfixOps

object TaskManager {

  def props(username: String): Props = Props(new TaskManager(username))

  case class TaskModel(objectId: Int, taskTypeId: Int, priority: Int, notes: String) {
    def toEntity(id: Int, landId: Int): TaskEntity ={
      val now = new Date
      TaskEntity(id, landId, objectId, taskTypeId, priority, notes, now, now)
    }
  }

  trait Command
  case object GetAllTasks extends Command
  case class GetLandTasks(landId: Int) extends Command
  case class GetLandObjectTasks(landId: Int, objectId: Int) extends Command
  case object GetSeasonTasks extends Command
  case object GetOpenTasks extends Command
  case class CreateTask(landId: Int, task: TaskModel) extends Command
  case class CreateTasks(landId: Int, tasks: List[TaskModel]) extends Command
  case class ModifyTask(id: Int, taskModel: TaskModel) extends Command
  case class ArchiveTask(id: Int) extends Command
  case class CompleteTask(id: Int) extends Command
  case class DeleteTask(id: Int) extends Command
  case object Destroy extends Command

  // Events
  case class TaskEntity(
    id: Int,
    landId: Int,
    objectId: Int,
    taskTypeId: Int,
    priority: Int,
    notes: String,
    createdAt: Date,
    modifiedAt: Date,
    completedAt: Option[Date] = None,
    archivedAt: Option[Date] = None
  )
  case class Archived(id: Int, archivedAt: Date)
  case class Completed(id: Int, completedAt: Date)
  case class Deleted(id: Int)
}
class TaskManager(username: String, receiveTimeoutDuration: Duration = 1 hour) extends PersistentActor with ActorLogging {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import TaskManager._

  var tasks: Map[Int, TaskEntity] = Map()
  var currentId = 1

  override def persistenceId: String = s"task-manager-$username"

  override def receiveRecover: Receive = {
    case task: TaskEntity =>
      tasks += task.id -> task
      currentId += 1

    case Archived(id, archivedAt) if tasks.contains(id) =>
      val oldTask = tasks(id)
      tasks += id -> oldTask.copy(archivedAt = Some(archivedAt), modifiedAt = List(oldTask.modifiedAt, archivedAt).max)

    case Completed(id, completedAt) if tasks.contains(id) =>
      val oldTask = tasks(id)
      tasks += id -> tasks(id).copy(completedAt = Some(completedAt), modifiedAt = List(oldTask.modifiedAt, completedAt).max)

    case Deleted(id) =>
      tasks -= id

    case msg => log.warning(s"Couldn't recover message: $msg")


  }

  override def receiveCommand: Receive = {
    case GetAllTasks =>
      sender ! tasks.values.toList
    case GetSeasonTasks =>
      sender ! tasks.values.toList.filterNot(_.archivedAt.isDefined)
    case GetOpenTasks =>
      sender ! tasks.values.toList.filterNot(t => t.completedAt.isDefined || t.archivedAt.isDefined)
    case GetLandTasks(landId) =>
      sender ! tasks.values.toList
        .filter(_.landId == landId)
        .filterNot(_.archivedAt.isDefined)
    case GetLandObjectTasks(landId, objectId) =>
      sender ! tasks.values.toList
        .filter(_.landId == landId)
        .filter(_.objectId == objectId)
        .filterNot(_.archivedAt.isDefined)


    case CreateTask(landId, task) =>
      log.info(s"[$persistenceId] Creating a new task $task")
      persist(task.toEntity(currentId, landId)) { event =>
        tasks += event.id -> event
        currentId += 1
        sender ! Success(event)
      }

    case CreateTasks(landId, tasks) =>
      log.info(s"[$persistenceId] Creating tasks in batch $tasks")
      val now = new Date
      val zippedTasks = tasks.zip(LazyList.from(currentId))
      val entityList = zippedTasks.map { case (t, id) => TaskEntity(id, landId, t.objectId, t.taskTypeId, t.priority, t.notes, now, now) }
      var resultCounter = 0
      persistAll(entityList) { event =>
        resultCounter += 1
        currentId += 1
        this.tasks += event.id -> event
        if (resultCounter == tasks.length) {
          sender ! Success(entityList)
        }
      }

    case ModifyTask(id, task) =>
      tasks.get(id) match {
        case Some(oldEntity) =>
          log.info(s"[$persistenceId] Changing task $id")
          persist(
            oldEntity.copy(
              objectId = task.objectId,
              taskTypeId = task.taskTypeId,
              priority = task.priority,
              notes = task.notes,
              modifiedAt = new Date
            )
          ) { event =>
            tasks += event.id -> event
            sender ! Success(event)
          }

        case None =>
          log.info(s"[$persistenceId] task $id not found")
          sender ! Error(s"No task found with id $id")
      }

    case ArchiveTask(id) =>
      tasks.get(id) match {
        case Some(oldTask) =>
          log.info(s"[$persistenceId] Archiving task $id")
          persist(Archived(id, new Date)) { event =>
            val newTask = oldTask.copy(archivedAt = Some(event.archivedAt))
            tasks += id -> newTask
            sender ! Success(newTask)
          }
        case None =>
          log.info(s"[$persistenceId] task $id not found")
          sender ! Error(s"No task found with id $id")
      }

    case CompleteTask(id) =>
      tasks.get(id) match {
        case Some(oldTask) =>
          log.info(s"[$persistenceId] Completing task $id")
          persist(Completed(id, new Date)) { event =>
            val newTask = oldTask.copy(completedAt = Some(event.completedAt))
            tasks += id -> newTask
            sender ! Success(newTask)
          }
        case None =>
          log.info(s"[$persistenceId] task $id not found")
          sender ! Error(s"No task found with id $id")
      }

    case DeleteTask(id) =>
      tasks.get(id) match {
        case Some(_) =>
          log.info(s"[$persistenceId] Deleting task $id")
          persist(Deleted(id)) { _ =>
            sender ! Success()
          }

        case None =>
          log.info(s"[$persistenceId] task $id not found")
          sender ! Error(s"No task found with id $id")
      }

    case Destroy =>
      log.info(s"[$persistenceId] Destroying actor and all data")
      deleteMessages(lastSequenceNr)
      context.stop(self)

    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping...")
      context.stop(self)

  }
}
