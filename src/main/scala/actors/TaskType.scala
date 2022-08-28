package actors

import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.pattern.StatusReply.{Error, Success, success}
import akka.persistence.PersistentActor

import scala.concurrent.duration._
import scala.language.postfixOps

object TaskType {
  def props(username: String, landId: Int): Props = Props(new TaskType(username, landId))

  case class TaskTypeModel(name: String, description: String)

  sealed trait Command
  case class AddTaskType(taskType: TaskTypeModel) extends Command
  case class BatchAddTaskType(taskTypes: List[TaskTypeModel]) extends Command
  case object GetTaskTypes extends Command
  case class ChangeTaskType(id: Int, taskType: TaskTypeModel) extends Command
  case class DeleteTaskType(id: Int) extends Command
  case object Destroy extends Command

  // Events
  case class TaskTypeEntity(id: Int, name: String, description: String)
  case class Deleted(id: Int)

}
class TaskType(username: String, landId: Int, receiveTimeoutDuration: Duration = 1 hour) extends PersistentActor with ActorLogging {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import TaskType._

  var taskTypes: Map[Int, TaskTypeEntity] = Map()
  var currentId: Int = 1

  override def persistenceId: String = s"task-type-$username-$landId"

  override def receiveRecover: Receive = {
    case taskType: TaskTypeEntity =>
      taskTypes += taskType.id -> taskType
      currentId += 1
    case Deleted(id) => taskTypes -= id
  }

  override def receiveCommand: Receive = {
    case AddTaskType(taskType) =>
      log.info(s"Adding task type $taskType")
      val entity = TaskTypeEntity(currentId, taskType.name, taskType.description)
      persist(entity) { event =>
        taskTypes += event.id -> event
        currentId += 1
        sender ! Success(entity)
      }

    case BatchAddTaskType(taskTypes) =>
      log.info(s"Adding batch $taskTypes")
      val taskTypeEntities = taskTypes
        .zip(LazyList.from(currentId))
        .map { case (model, id) => TaskTypeEntity(id, model.name, model.description)}
      var completed = 0
      persistAll(taskTypeEntities) { event =>
        this.taskTypes += event.id -> event
        currentId += 1
        completed += 1
        if (completed == taskTypeEntities.length) {
          sender ! Success(taskTypeEntities)
        }
      }

    case GetTaskTypes =>
      sender ! taskTypes.values.toList

    case ChangeTaskType(id, taskType) =>
      taskTypes.get(id) match {
        case Some(_) =>
          log.info(s"[$persistenceId] Changing task type $id")
          persist(TaskTypeEntity(id, taskType.name, taskType.description)) { event =>
            taskTypes += id -> TaskTypeEntity(id, taskType.name, taskType.description)
            sender ! Success(event)
          }
        case None =>
          log.info(s"[$persistenceId] TaskType $id not found")
          sender ! Error(s"No task type found with id $id")
      }

    case DeleteTaskType(id) =>
      taskTypes.get(id) match {
        case Some(_) =>
          log.info(s"[$persistenceId] Deleting task type $id")
          persist(Deleted(id)) { event =>
            taskTypes -= id
            sender ! Success()
          }
        case None =>
          log.info(s"[$persistenceId] task type $id not found")
          sender ! Error(s"No task type found with id $id")
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
