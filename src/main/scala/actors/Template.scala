package actors

import actors.ObjectType.ObjType
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

  // responses
  case class ObjectTypeOptionsResponse(default: Map[String, List[ObjType]], fromLands: Set[List[ObjType]])
}
class Template(user: ActorRef, receiveTimeoutDuration: Duration = 1 hour) extends Timers with PersistentActor with ActorLogging with Stash {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import Template._
  import UserManagement.LandCommand
  import Land.{LandObjectTypesCommand, GetAllLands, LandEntity}
  import ObjectType._

  var recoveredDefaultObjectTypes: Map[String, Map[String, List[ObjType]]] = Map()

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

    case DeletedDefaultObjectType(locale, name) =>
      recoveredDefaultObjectTypes.get(locale) match {
        case Some(map) =>
          log.info(s"[$persistenceId] Recovering deletion: $name")
          recoveredDefaultObjectTypes += locale -> (map - name)
        case None =>
          log.warning(s"[$persistenceId] No template found: $locale - $name")
      }

    case RecoveryCompleted =>
      context.become(waitingForRequest(recoveredDefaultObjectTypes))
  }

  def waitingForRequest(defaultObjectTypes: Map[String, Map[String, List[ObjType]]]): Receive = {
    case GetObjectTypeOptions(username, locale) =>
      user ! LandCommand(username, GetAllLands)
      unstashAll()
      context.become(waitingForLands(sender, username, locale, defaultObjectTypes))

    case RegisterNewLandTemplate(locale, name, objTypes) =>
      log.info(s"Registering new template $name for locale $locale")
      defaultObjectTypes.get(locale) match {
        case Some(map) =>
          map.get(name) match {
            case Some(_) => sender ! Error(s"Template $name already exists for locale $locale")
            case None =>
              persist(StoredDefaultObjectType(locale, name, objTypes)) { event =>
                val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> (map + (event.name -> event.objTypes)))
                sender ! Success()
                context.become(waitingForRequest(newDefaultObjectTypes))
              }
          }
        case None => persist(StoredDefaultObjectType(locale, name, objTypes)) { event =>
          val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> Map(event.name -> event.objTypes))
          sender ! Success()
          context.become(waitingForRequest(newDefaultObjectTypes))
        }
      }

    case ChangeLandTemplate(locale, name, objTypes) =>
      log.info(s"Changing existing template $name for locale $locale")
      defaultObjectTypes.get(locale) match {
        case Some(map) =>
          map.get(name) match {
            case Some(_) =>
              persist(StoredDefaultObjectType(locale, name, objTypes)) { event =>
                val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> (map + (event.name -> event.objTypes)))
                sender ! Success()
                context.become(waitingForRequest(newDefaultObjectTypes))
              }
            case None => sender ! Error(s"Template $name for locale $locale does not exist")
          }
        case None => sender ! Error(s"Locale $locale does not exist")
      }

    case DeleteLandTemplate(locale, name) =>
      defaultObjectTypes.get(locale) match {
        case Some(map) =>
          persist(DeletedDefaultObjectType(locale, name)) { event =>
            val newDefaultObjectTypes = defaultObjectTypes + (event.locale -> (map - name))
            sender ! Success()
            context.become(waitingForRequest(newDefaultObjectTypes))
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

  def waitingForLands(client: ActorRef, username: String, locale: String, defaultObjectTypes:  Map[String, Map[String, List[ObjType]]]): Receive = {
    case lands: List[LandEntity] =>
      if (lands.isEmpty) {
        defaultObjectTypes.get(locale) match {
          case Some(map) => client ! Success(ObjectTypeOptionsResponse(map, Set()))
          case None => client ! Success(ObjectTypeOptionsResponse(Map(), Set()))
        }
        unstashAll()
        context.become(waitingForRequest(defaultObjectTypes))
      } else {
        val expectedResponses = lands.length
        lands.map(_.id).foreach { id =>
          user ! LandCommand(username, LandObjectTypesCommand(id, GetObjectTypes))
        }
        unstashAll()
        context.become(waitingForObjectTypeResponses(client, locale, defaultObjectTypes, expectedResponses, Set()))

        timers.startSingleTimer(TimerKey, TimerTimeout, 1 second)
      }

    case _: List[ObjectTypeEntity] =>

    case msg =>
      log.warning(s"[$persistenceId] Stashing a message that can't be processed in waitingForLands: $msg")
      stash()
  }

  def waitingForObjectTypeResponses(
                                     client: ActorRef,
                                     locale: String,
                                     defaultObjectTypes:  Map[String, Map[String, List[ObjType]]],
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
        context.become(waitingForRequest(defaultObjectTypes))
      } else {
        context.become(waitingForObjectTypeResponses(client, locale, defaultObjectTypes, messagesToReceive, objectTypesFromLands))
      }

    case TimerTimeout =>
      defaultObjectTypes.get(locale) match {
        case Some(map) => client ! Success(ObjectTypeOptionsResponse(map, fromLands))
        case None => client ! Success(ObjectTypeOptionsResponse(Map(), fromLands))
      }
      unstashAll()
      context.become(waitingForRequest(defaultObjectTypes))

    case msg =>
      log.warning(s"[$persistenceId] Stashing a message that can't be processed in waitingForObjectTypeResponses: $msg")
      stash()
  }
}
