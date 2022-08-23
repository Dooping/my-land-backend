package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout, Stash, Timers}
import akka.pattern.StatusReply._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import scala.concurrent.duration._
import scala.language.postfixOps

object ObjectManager {
  def props(username: String, landId: Int): Props = Props(new ObjectManager(username, landId))

  case object TimerKey
  case object TimerTimeout
  case object Persisted

  // Commands
  trait Command
  case class AddLandObject(lat: Double, lon: Double, status: String, typeId: Int) extends Command
  case class DeleteLandObject(id: Int) extends Command
  case class ChangeLandObject(id: Int, lat: Double, lon: Double, status: String, typeId: Int) extends Command
  case class DeleteByType(typeId: Int) extends Command

  // Events
  case class LandObject(id: Int, lat: Double, lon: Double, status: String, typeId: Int)
  case class DeletedLandObject(id: Int)

}
class ObjectManager(username: String, landId: Int, receiveTimeoutDuration: Duration = 1 hour) extends Timers
  with PersistentActor
  with ActorLogging
  with Stash {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import ObjectManager._

  var objects: Map[Int, LandObject] = Map()
  var currentId = 1

  override def persistenceId: String = s"object-manager-$username-$landId"

  override def receiveRecover: Receive = {
    case obj @ LandObject(id, _, _, _, _) =>
      objects += id -> obj
      currentId += 1
    case DeletedLandObject(id) =>
      objects -= id
  }

  override def receiveCommand: Receive = {
    case cmd @ AddLandObject(lat, lon, status, typeId) =>
      log.info(s"Adding object: $cmd")
      persist(LandObject(currentId, lat, lon, status, typeId)) { event =>
        objects += event.id -> event
        currentId += 1
        sender ! Success(event)
      }

    case DeleteLandObject(id) =>
      log.info(s"Deleting object: $id")
      persist(DeletedLandObject(id)) { event =>
        objects -= event.id
        sender ! Success()
      }

    case cmd @ ChangeLandObject(id, lat, lon, status, typeId) =>
      objects.get(id).foreach { _ =>
        log.info(s"Changing object $id to $cmd")
        persist(LandObject(id, lat, lon, status, typeId)) { event =>
          objects += event.id -> event
          sender ! Success(event)
        }
      }

    case DeleteByType(typeId) =>
      log.info(s"Deleting all objects of type $typeId")
      val objectsToDelete = objects.values.filterNot(_.typeId == typeId).map(obj => DeletedLandObject(obj.id))
      if (objectsToDelete.nonEmpty) {
        timers.startSingleTimer(TimerKey, TimerTimeout, 1 second)
        persistAll(objectsToDelete.toSeq) { event =>
          objects -= event.id
          self ! Persisted
        }
        unstashAll()
        context.become(waitingForDeletes(objectsToDelete.size, sender))
      }

    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping...")
      context.stop(self)

    case _ => stash()
  }

  def waitingForDeletes(count: Int, original: ActorRef): Receive = {
    case Persisted =>
      if (count <= 1) {
        original ! Success()
        context.become(receiveCommand)
        timers.cancel(TimerKey)
      } else {
        unstashAll()
        context.become(waitingForDeletes(count - 1, original))
      }

    case TimerTimeout =>
      original ! Error("Some objects failed to be deleted")
      unstashAll()
      context.become(receiveCommand)

    case _ => stash()
  }

}
