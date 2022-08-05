package actors

import akka.actor.{Actor, ActorLogging, Props, ReceiveTimeout}
import akka.pattern.StatusReply._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import java.util.Date
import scala.concurrent.duration._
import scala.language.postfixOps

object ObjectType {
  def props(username: String, landId: Int): Props = Props(new ObjectType(username, landId))

  case class ObjType(name: String, color: String, icon: String)

  sealed trait Command
  case class AddObjectType(obj: ObjType) extends Command
  case class BatchAddObjectType(objectTypes: List[ObjType]) extends Command
  case object GetObjectTypes extends Command
  case class ChangeObjectType(id: Int, objType: ObjType) extends Command
  case class DeleteObjectType(id: Int) extends Command

  trait Event
  case class ObjectTypeEntity(id: Int, name: String, color: String, icon: String, createdAt: Date, modifiedAt: Date) extends Event
  case class ObjectTypeDeleted(id: Int)
}
class ObjectType(username: String, land: Int, receiveTimeoutDuration: Duration = 1 hour) extends PersistentActor with ActorLogging {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import ObjectType._

  var recoveredObjectTypes: Map[Int, ObjectTypeEntity] = Map()
  var recoveredId = 1

  val persistenceId: String = s"object-types-$username-$land"

  override def receiveCommand: Receive = Actor.emptyBehavior

  override def receiveRecover: Receive = {
    case event @ ObjectTypeEntity(id, _, _, _, _, _) =>
      log.info(s"[$persistenceId] Recovering $event")
      recoveredObjectTypes += id -> event
      recoveredId += 1
    case DeleteObjectType(id) =>
      log.info(s"[$persistenceId] Recovering deletion $id")
      recoveredObjectTypes -= id
    case RecoveryCompleted =>
      context.become(objectTypeReceive(recoveredObjectTypes, recoveredId))
  }

  private def objectTypeReceive(objectTypes: Map[Int, ObjectTypeEntity], currentId: Int): Receive = {
    case AddObjectType(obj) =>
      log.info(s"[$persistenceId] Adding ObjectType $obj")
      persist(ObjectTypeEntity(currentId, obj.name, obj.color, obj.icon, new Date, new Date)) { event =>
        context.become(objectTypeReceive(objectTypes + (event.id -> event), currentId + 1))
        sender ! Success(event)
      }

    case BatchAddObjectType(list) =>
      log.info(s"[$persistenceId] Adding multiple objectTypes $list")
      val entities = list.zip(LazyList.from(currentId)).map {
        case (obj, id) => ObjectTypeEntity(id, obj.name, obj.color, obj.icon, new Date, new Date)
      }
      var completed = 0
      persistAll(entities) { _ =>
        completed += 1
        if (completed == entities.length) {
          context.become(objectTypeReceive(objectTypes ++ entities.map(o => (o.id, o)).toMap, currentId + entities.length))
          sender ! Success(entities)
        }
      }


    case ChangeObjectType(id, ObjType(name, color, icon)) =>
      objectTypes.get(id) match {
        case Some(ObjectTypeEntity(_, _, _, _, createdAt, _)) =>
          log.info(s"[$persistenceId] Changing ObjectType $id")
          persist(ObjectTypeEntity(id, name, color, icon, createdAt, new Date)) { event =>
            context.become(objectTypeReceive(objectTypes + (event.id -> event), currentId))
            sender ! Success(event)
          }
        case None =>
          log.info(s"[$persistenceId] ObjectType $id not found")
          sender ! Error(s"No object type found with id $id")
      }

    case GetObjectTypes =>
      log.info(s"[$persistenceId] Sending all object types ${objectTypes.values.toList}")
      sender ! objectTypes.values.toList

    case DeleteObjectType(id) =>
      objectTypes.get(id) match {
        case Some(_) =>
          log.info(s"[$persistenceId] Deleting $id")
          persist(ObjectTypeDeleted(id)) { event =>
            context.become(objectTypeReceive(objectTypes - event.id, currentId))
            sender ! Success
          }
        case None =>
          log.info(s"[$persistenceId] ObjectType $id not found")
          sender ! Error(s"No object type found with id $id")
      }


    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping...")
      context.stop(self)
  }
}
