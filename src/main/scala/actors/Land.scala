package actors

import akka.actor.{Actor, ActorLogging, Props, ReceiveTimeout}
import akka.pattern.StatusReply._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import scala.concurrent.duration._
import scala.language.postfixOps

object Land {
  def props(username: String, idleTimeout: Duration = 1 hour): Props = Props(new Land(username, idleTimeout))

  sealed trait Command
  case class GetLand(name: String) extends Command
  case object GetAllLands extends Command
  case class AddLand(name: String, description: String, area: Double, lat: Double, lon: Double, zoom: Double, bearing: Double, polygon: String) extends Command
  case class ChangeLandDescription(landName: String, description: String) extends Command
  case class ChangePolygon(landName: String, area: Double, lat: Double, lon: Double, zoom: Double, bearing: Double, polygon: String) extends Command

  case class LandEntity(name: String, description: String, area: Double, lat: Double, lon: Double, zoom: Double, bearing: Double, polygon: String)
}

class Land(username: String, receiveTimeoutDuration: Duration) extends PersistentActor with ActorLogging {
  context.setReceiveTimeout(receiveTimeoutDuration)

  import Land._

  var recoveredLands: Map[String, LandEntity] = Map()

  override val persistenceId: String = s"land-$username"

  override def receiveCommand: Receive = Actor.emptyBehavior

  override def receiveRecover: Receive = landRecovery

  def landRecovery: Receive = {
    case land @ LandEntity(name, _, _, _, _, _, _, _) =>
      log.info(s"[$persistenceId] Restoring land: $land")
      recoveredLands += name -> land
    case RecoveryCompleted => context.become(landReceive(recoveredLands))
  }

  def landReceive(lands: Map[String, LandEntity]): Receive = {
    case GetAllLands =>
      sender ! lands.values.toList

    case GetLand(name) => sender ! lands.get(name)

    case land @ AddLand(name, description, area, lat, lon, zoom, bearing, polygon) =>
      if (lands.contains(name)) {
        log.info(s"[$persistenceId] Land $name already exists")
        sender ! Error(s"Land $name already exists")
      } else {
        log.info(s"[$persistenceId] Adding land $land")
        persist(LandEntity(name, description, area, lat, lon, zoom, bearing, polygon)) { land =>
          context.become(landReceive(lands + (land.name -> land)))
          sender ! Success
        }
      }

    case ChangeLandDescription(landName, description) =>
      if (lands.contains(landName)) {
        log.info(s"[$persistenceId] Changing $landName description to: $description")
        val newLand = lands(landName).copy(description = description)
        persist(newLand) { land =>
          context.become(landReceive(lands + (land.name -> land)))
          sender ! Success
        }
      } else {
        log.info(s"[$persistenceId] Land $landName does not exist.")
        sender ! Error("The land requested does not exist")
      }

    case cmd @ ChangePolygon(landName, area, lat, lon, zoom, bearing, polygon) =>
      if (lands.contains(landName)) {
        log.info(s"[$persistenceId] Changing $landName polygon to: $cmd")
        val oldLand = lands(landName)
        val newLand = LandEntity(landName, oldLand.description, area, lat, lon, zoom, bearing, polygon)
        persist(newLand) { land =>
          context.become(landReceive(lands + (land.name -> land)))
          sender ! Success
        }
      } else {
        log.info(s"[$persistenceId] Land $landName does not exist.")
        sender ! Error("The land requested does not exist")
      }

    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping")
      context.stop(self)

  }
}
