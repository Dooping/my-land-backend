package actors

import akka.actor.{Actor, ActorLogging, Props, ReceiveTimeout}
import akka.pattern.StatusReply._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import scala.concurrent.duration._
import scala.language.postfixOps

object Land {
  def props(username: String): Props = Props(new Land(username))

  sealed trait Command
  case class GetLand(id: Int) extends Command
  case object GetAllLands extends Command
  case class AddLand(name: String, description: String, area: Double, lat: Double, lon: Double, zoom: Double, bearing: Double, polygon: String) extends Command
  case class ChangeLandDescription(id: Int, description: String) extends Command
  case class ChangePolygon(id: Int, area: Double, lat: Double, lon: Double, zoom: Double, bearing: Double, polygon: String) extends Command
  case class DeleteLand(id: Int)

  trait Event
  case class LandEntity(id: Int, name: String, description: String, area: Double, lat: Double, lon: Double, zoom: Double, bearing: Double, polygon: String) extends Event
  case class DeletedLand(id: Int) extends Event
}

class Land(username: String, receiveTimeoutDuration: Duration = 1 hour) extends PersistentActor with ActorLogging {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import Land._

  var recoveredLands: Map[Int, LandEntity] = Map()
  var recoveredId = 1

  override val persistenceId: String = s"land-$username"

  override def receiveCommand: Receive = Actor.emptyBehavior

  override def receiveRecover: Receive = landRecovery

  def landRecovery: Receive = {
    case land @ LandEntity(id, _, _, _, _, _, _, _, _) =>
      log.info(s"[$persistenceId] Restoring land: $land")
      recoveredLands += id -> land
      recoveredId += 1
    case RecoveryCompleted => context.become(landReceive(recoveredLands, recoveredId))
  }

  def landReceive(lands: Map[Int, LandEntity], currentId: Int): Receive = {
    case GetAllLands =>
      sender ! lands.values.toList

    case GetLand(name) => sender ! lands.get(name)

    case land @ AddLand(name, description, area, lat, lon, zoom, bearing, polygon) =>
      if (lands.exists {
        case (_, land) => land.name == name
      }) {
        log.info(s"[$persistenceId] Land $name already exists")
        sender ! Error(s"Land $name already exists")
      } else {
        log.info(s"[$persistenceId] Adding land $land")
        persist(LandEntity(currentId, name, description, area, lat, lon, zoom, bearing, polygon)) { land =>
          context.become(landReceive(lands + (land.id -> land), currentId + 1))
          sender ! Success
        }
      }

    case ChangeLandDescription(id, description) =>
      lands.get(id) match {
        case Some(land) =>
          log.info(s"[$persistenceId] Changing $id description to: $description")
          val newLand = land.copy(description = description)
          persist(newLand) { land =>
            context.become(landReceive(lands + (land.id -> land), currentId))
            sender ! Success(land)
          }
        case None =>
          log.info(s"[$persistenceId] Land $id does not exist.")
          sender ! Error("The land requested does not exist")
      }

    case cmd @ ChangePolygon(id, area, lat, lon, zoom, bearing, polygon) =>
      lands.get(id) match {
        case Some(land) =>
          log.info(s"[$persistenceId] Changing $id polygon to: $cmd")
          val newLand = LandEntity(id, land.name, land.description, area, lat, lon, zoom, bearing, polygon)
          persist(newLand) { land =>
            context.become(landReceive(lands + (land.id -> land), currentId))
            sender ! Success(land)
          }
        case None =>
          log.info(s"[$persistenceId] Land $id does not exist.")
          sender ! Error("The land requested does not exist")
      }

    case DeleteLand(id) =>
      lands.get(id) match {
        case Some(_) =>
          log.info(s"[$persistenceId] Deleting land with $id")
          persist(DeletedLand(id)) { event =>
            context.become(landReceive(lands - event.id, currentId))
            sender ! Success
          }
        case None =>
          log.info(s"[$persistenceId] Land $id does not exist.")
          sender ! Error("The land requested does not exist")
      }


    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping...")
      context.stop(self)

  }
}
