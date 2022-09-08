package actors

import actors.Land.LandTaskTypesCommand
import actors.ObjectType.ObjType
import actors.TaskType.{GetTaskTypes, TaskTypeEntity, TaskTypeModel}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout, Stash, Timers}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.pattern.StatusReply._

import scala.concurrent.duration._
import scala.language.postfixOps

object Template {
  def props(userManager: ActorRef): Props = Props(new Template(userManager))

  case object TimerKey
  case object TimerTimeout

  trait Command
  case class GetObjectTypeOptions(username: String, locale: String) extends Command
  case class GetTaskTypeOptions(username: String, locale: String) extends Command
  case class RegisterNewLandObjectTemplate(locale: String, name: String, objTypes: List[ObjType]) extends Command
  case class RegisterNewTaskTemplate(locale: String, name: String, taskTypes: List[TaskTypeModel]) extends Command
  case class ChangeLandObjectTemplate(locale: String, name: String, objTypes: List[ObjType]) extends Command
  case class ChangeTaskTemplate(locale: String, name: String, taskTypes: List[TaskTypeModel]) extends Command
  case class DeleteLandObjectTemplate(locale: String, name: String) extends Command
  case class DeleteTaskTemplate(locale: String, name: String) extends Command

  // events
  case class StoredDefaultObjectType(locale: String = "en", name: String, objTypes: List[ObjType])
  case class DeletedDefaultObjectType(locale: String = "en", name: String)
  case class StoredDefaultTaskType(locale: String = "en", name: String, taskTypes: List[TaskTypeModel])
  case class DeletedDefaultTaskType(locale: String = "en", name: String)

  // responses
  case class ObjectTypeOptionsResponse(default: Map[String, List[ObjType]], fromLands: Set[List[ObjType]])
  case class TaskTypeOptionsResponse(default: Map[String, List[TaskTypeModel]], fromLands: Set[List[TaskTypeModel]])
}
class Template(user: ActorRef, receiveTimeoutDuration: Duration = 1 hour) extends Timers with PersistentActor with ActorLogging with Stash {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import Template._
  import UserManagement.LandCommand
  import Land.{LandObjectTypesCommand, GetAllLands, LandEntity}
  import ObjectType._

  var recoveredDefaultObjectTypes: Map[String, Map[String, List[ObjType]]] = Map()
  var recoveredDefaultTaskTypes: Map[String, Map[String, List[TaskTypeModel]]] = Map()

  override val persistenceId: String = "template"

  override def receiveCommand: Receive = Actor.emptyBehavior

  override def receiveRecover: Receive = {
    case StoredDefaultObjectType(locale, name, objTypes) =>
      log.info(s"[$persistenceId] Recovering $objTypes")
      recoveredDefaultObjectTypes.get(locale) match {
        case Some(map) =>
          recoveredDefaultObjectTypes += locale -> (map + (name -> objTypes))
        case None => recoveredDefaultObjectTypes += locale -> Map(name -> objTypes)
      }

    case StoredDefaultTaskType(locale, name, taskTypes) =>
      log.info(s"[$persistenceId] Recovering $taskTypes")
      recoveredDefaultTaskTypes.get(locale) match {
        case Some(map) =>
          recoveredDefaultTaskTypes += locale -> (map + (name -> taskTypes))
        case None => recoveredDefaultTaskTypes += locale -> Map(name -> taskTypes)
      }

    case DeletedDefaultObjectType(locale, name) =>
      recoveredDefaultObjectTypes.get(locale) match {
        case Some(map) =>
          log.info(s"[$persistenceId] Recovering deletion: $name")
          recoveredDefaultObjectTypes += locale -> (map - name)
        case None =>
          log.warning(s"[$persistenceId] No template found: $locale - $name")
      }

    case DeletedDefaultTaskType(locale, name) =>
      recoveredDefaultTaskTypes.get(locale) match {
        case Some(map) =>
          log.info(s"[$persistenceId] Recovering deletion: $name")
          recoveredDefaultTaskTypes += locale -> (map - name)
        case None =>
          log.warning(s"[$persistenceId] No template found: $locale - $name")
      }

    case RecoveryCompleted =>
      context.become(waitingForRequest(recoveredDefaultObjectTypes, recoveredDefaultTaskTypes))
  }

  def waitingForRequest(defaultObjectTypes: Map[String, Map[String, List[ObjType]]], defaultTaskTypes: Map[String, Map[String, List[TaskTypeModel]]]): Receive = {
    case cmd @ GetObjectTypeOptions(username, _) =>
      user ! LandCommand(username, GetAllLands)
      unstashAll()
      context.become(waitingForLands(sender, cmd, defaultObjectTypes, defaultTaskTypes))

    case cmd @ GetTaskTypeOptions(username, _) =>
      user ! LandCommand(username, GetAllLands)
      unstashAll()
      context.become(waitingForLands(sender, cmd, defaultObjectTypes, defaultTaskTypes))

    case RegisterNewLandObjectTemplate(locale, name, objTypes) =>
      log.info(s"Registering new template $name for locale $locale")
      defaultObjectTypes.get(locale) match {
        case Some(map) =>
          map.get(name) match {
            case Some(_) => sender ! Error(s"Template $name already exists for locale $locale")
            case None =>
              persist(StoredDefaultObjectType(locale, name, objTypes)) { event =>
                log.debug(s"template added : $event")
                val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> (map + (event.name -> event.objTypes)))
                sender ! Success()
                context.become(waitingForRequest(newDefaultObjectTypes, defaultTaskTypes))
              }
          }
        case None => persist(StoredDefaultObjectType(locale, name, objTypes)) { event =>
          log.debug(s"template added : $event")
          val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> Map(event.name -> event.objTypes))
          sender ! Success()
          context.become(waitingForRequest(newDefaultObjectTypes, defaultTaskTypes))
        }
      }

    case RegisterNewTaskTemplate(locale, name, taskTypes) =>
      log.info(s"Registering new template $name for locale $locale")
      defaultTaskTypes.get(locale) match {
        case Some(map) =>
          map.get(name) match {
            case Some(_) => sender ! Error(s"Template $name already exists for locale $locale")
            case None =>
              persist(StoredDefaultTaskType(locale, name, taskTypes)) { event =>
                log.debug(s"template added : $event")
                val newDefaultTaskTypes = defaultTaskTypes + (event.locale -> (map + (event.name -> event.taskTypes)))
                sender ! Success()
                context.become(waitingForRequest(defaultObjectTypes, newDefaultTaskTypes))
              }
          }
        case None => persist(StoredDefaultTaskType(locale, name, taskTypes)) { event =>
          log.debug(s"template added : $event")
          val newDefaultTaskTypes = defaultTaskTypes + (event.locale -> Map(event.name -> event.taskTypes))
          sender ! Success()
          context.become(waitingForRequest(defaultObjectTypes, newDefaultTaskTypes))
        }
      }

    case ChangeLandObjectTemplate(locale, name, objTypes) =>
      log.info(s"Changing existing template $name for locale $locale")
      defaultObjectTypes.get(locale) match {
        case Some(map) =>
          map.get(name) match {
            case Some(_) =>
              persist(StoredDefaultObjectType(locale, name, objTypes)) { event =>
                val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> (map + (event.name -> event.objTypes)))
                sender ! Success()
                context.become(waitingForRequest(newDefaultObjectTypes, defaultTaskTypes))
              }
            case None => sender ! Error(s"Template $name for locale $locale does not exist")
          }
        case None => sender ! Error(s"Locale $locale does not exist")
      }

    case ChangeTaskTemplate(locale, name, taskTypes) =>
      log.info(s"Changing existing template $name for locale $locale")
      defaultTaskTypes.get(locale) match {
        case Some(map) =>
          map.get(name) match {
            case Some(_) =>
              persist(StoredDefaultTaskType(locale, name, taskTypes)) { event =>
                val newDefaultTaskTypes = defaultTaskTypes + (event.locale -> (map + (event.name -> event.taskTypes)))
                sender ! Success()
                context.become(waitingForRequest(defaultObjectTypes, newDefaultTaskTypes))
              }
            case None => sender ! Error(s"Template $name for locale $locale does not exist")
          }
        case None => sender ! Error(s"Locale $locale does not exist")
      }

    case DeleteLandObjectTemplate(locale, name) =>
      defaultObjectTypes.get(locale) match {
        case Some(map) =>
          persist(DeletedDefaultObjectType(locale, name)) { event =>
            val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> (map - name))
            sender ! Success()
            context.become(waitingForRequest(newDefaultObjectTypes, defaultTaskTypes))
          }
        case None =>
          sender ! Error(s"Locale $locale does not exist")
      }

    case DeleteTaskTemplate(locale, name) =>
      defaultTaskTypes.get(locale) match {
        case Some(map) =>
          persist(DeletedDefaultObjectType(locale, name)) { event =>
            val newDefaultTaskTypes = defaultTaskTypes + (event.locale -> (map - name))
            sender ! Success()
            context.become(waitingForRequest(defaultObjectTypes, newDefaultTaskTypes))
          }
        case None =>
          sender ! Error(s"Locale $locale does not exist")
      }

    case msg =>
      log.warning(s"[$persistenceId] Stashing a message that can't be processed in waitingForRequest: $msg")
      stash()

    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping...")
      context.stop(self)
  }

  def waitingForLands(
                       client: ActorRef,
                       cmd: Template.Command,
                       defaultObjectTypes: Map[String, Map[String, List[ObjType]]],
                       defaultTaskTypes: Map[String, Map[String, List[TaskTypeModel]]]
                     ): Receive = {
    case lands: List[LandEntity] =>
      cmd match {
        case GetObjectTypeOptions(username, locale) =>
          if (lands.isEmpty) {
            defaultObjectTypes.get(locale) match {
              case Some(map) =>
                client ! Success(ObjectTypeOptionsResponse(map, Set()))
              case None => client ! Success(ObjectTypeOptionsResponse(Map(), Set()))
            }
            unstashAll()
            context.become(waitingForRequest(defaultObjectTypes, defaultTaskTypes))
          } else {
            val expectedResponses = lands.length
            lands.map(_.id).foreach { id =>
              user ! LandCommand(username, LandObjectTypesCommand(id, GetObjectTypes))
            }
            unstashAll()
            context.become(waitingForObjectTypeResponses(client, locale, defaultObjectTypes, defaultTaskTypes, expectedResponses, Set()))

            timers.startSingleTimer(TimerKey, TimerTimeout, 1 second)
          }
        case GetTaskTypeOptions(username, locale) =>
          if (lands.isEmpty) {
            defaultTaskTypes.get(locale) match {
              case Some(map) => client ! Success(TaskTypeOptionsResponse(map, Set()))
              case None => client ! Success(TaskTypeOptionsResponse(Map(), Set()))
            }
            unstashAll()
            context.become(waitingForRequest(defaultObjectTypes, defaultTaskTypes))
          } else {
            val expectedResponses = lands.length
            lands.map(_.id).foreach { id =>
              user ! LandCommand(username, LandTaskTypesCommand(id, GetTaskTypes))
            }
            unstashAll()
            context.become(waitingForTaskTypeResponses(client, locale, defaultObjectTypes, defaultTaskTypes, expectedResponses, Set()))

            timers.startSingleTimer(TimerKey, TimerTimeout, 1 second)
          }
      }

    case _: List[ObjectTypeEntity] =>
    case _: List[TaskTypeEntity] =>

    case msg =>
      log.warning(s"[$persistenceId] Stashing a message that can't be processed in waitingForLands: $msg")
      stash()
  }

  def waitingForObjectTypeResponses(
                                     client: ActorRef,
                                     locale: String,
                                     defaultObjectTypes:  Map[String, Map[String, List[ObjType]]],
                                     defaultTaskTypes:  Map[String, Map[String, List[TaskTypeModel]]],
                                     expectedMessages: Int,
                                     fromLands: Set[List[ObjType]]
   ): Receive = {
    case list: List[ObjectTypeEntity] =>
      log.info(s"Received response from object type actor $list")
      val messagesToReceive = expectedMessages - 1
      val mappedList = list.map(o => ObjType(o.name, o.color, o.icon))
      val objectTypesFromLands = fromLands + mappedList
      if (messagesToReceive <= 0) {
        timers.cancel(TimerKey)
        defaultObjectTypes.get(locale) match {
          case Some(map) => client ! Success(ObjectTypeOptionsResponse(map, objectTypesFromLands))
          case None => client ! Success(ObjectTypeOptionsResponse(Map(), objectTypesFromLands))
        }
        unstashAll()
        context.become(waitingForRequest(defaultObjectTypes, defaultTaskTypes))
      } else {
        context.become(waitingForObjectTypeResponses(client, locale, defaultObjectTypes, defaultTaskTypes, messagesToReceive, objectTypesFromLands))
      }

    case TimerTimeout =>
      defaultObjectTypes.get(locale) match {
        case Some(map) => client ! Success(ObjectTypeOptionsResponse(map, fromLands))
        case None => client ! Success(ObjectTypeOptionsResponse(Map(), fromLands))
      }
      unstashAll()
      context.become(waitingForRequest(defaultObjectTypes, defaultTaskTypes))

    case msg =>
      log.warning(s"[$persistenceId] Stashing a message that can't be processed in waitingForObjectTypeResponses: $msg")
      stash()
  }

  def waitingForTaskTypeResponses(
                                     client: ActorRef,
                                     locale: String,
                                     defaultObjectTypes:  Map[String, Map[String, List[ObjType]]],
                                     defaultTaskTypes:  Map[String, Map[String, List[TaskTypeModel]]],
                                     expectedMessages: Int,
                                     fromLands: Set[List[TaskTypeModel]]
                                   ): Receive = {
    case list: List[TaskTypeEntity] =>
      log.info(s"Received response from task type actor $list")
      val messagesToReceive = expectedMessages - 1
      val mappedList = list.map(t => TaskTypeModel(t.name, t.description))
      val taskTypesFromLands = fromLands + mappedList
      if (messagesToReceive <= 0) {
        timers.cancel(TimerKey)
        defaultTaskTypes.get(locale) match {
          case Some(map) => client ! Success(TaskTypeOptionsResponse(map, taskTypesFromLands))
          case None => client ! Success(TaskTypeOptionsResponse(Map(), taskTypesFromLands))
        }
        unstashAll()
        context.become(waitingForRequest(defaultObjectTypes, defaultTaskTypes))
      } else {
        context.become(waitingForTaskTypeResponses(client, locale, defaultObjectTypes, defaultTaskTypes, messagesToReceive, taskTypesFromLands))
      }

    case TimerTimeout =>
      defaultTaskTypes.get(locale) match {
        case Some(map) => client ! Success(TaskTypeOptionsResponse(map, fromLands))
        case None => client ! Success(TaskTypeOptionsResponse(Map(), fromLands))
      }
      unstashAll()
      context.become(waitingForRequest(defaultObjectTypes, defaultTaskTypes))

    case msg =>
      log.warning(s"[$persistenceId] Stashing a message that can't be processed in waitingForObjectTypeResponses: $msg")
      stash()
  }
}
