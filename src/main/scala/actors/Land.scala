package actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, ReceiveTimeout, Terminated}
import akka.pattern.StatusReply._
import akka.persistence.{PersistentActor, RecoveryCompleted}

import java.util.Date
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

  case class DeleteLand(id: Int) extends Command

  case class LandObjectTypesCommand(landId: Int, cmd: ObjectType.Command) extends Command

  case class LandTaskTypesCommand(landId: Int, cmd: TaskType.Command) extends Command

  case class LandObjectsCommand(landId: Int, cmd: ObjectManager.Command) extends Command

  case object Destroy extends Command

  case object Implode extends Command

  trait Event

  case class LandEntity(
                         id: Int,
                         name: String,
                         description: String,
                         area: Double,
                         lat: Double,
                         lon: Double,
                         zoom: Double,
                         bearing: Double,
                         polygon: String,
                         createdAt: Date,
                         modifiedAt: Date) extends Event {
    override def equals(obj: Any): Boolean = {
      val other = obj.asInstanceOf[LandEntity]
      obj.isInstanceOf[LandEntity] &&
        id == other.id &&
        name == other.name &&
        description == other.description &&
        area == other.area &&
        lat == other.lat &&
        lon == other.lon &&
        zoom == other.zoom &&
        bearing == other.bearing &&
        polygon == other.polygon
    }
  }

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
    case land@LandEntity(id, _, _, _, _, _, _, _, _, _, _) =>
      log.info(s"[$persistenceId] Restoring land: $land")
      recoveredLands += id -> land
      recoveredId += 1
    case RecoveryCompleted => context.become(landReceive(recoveredLands, recoveredId, Map(), Map(), Map()))
  }

  def landReceive(
                   lands: Map[Int, LandEntity],
                   currentId: Int,
                   landObjectTypes: Map[Int, ActorRef],
                   landTaskTypes: Map[Int, ActorRef],
                   landObjects: Map[Int, ActorRef]): Receive = {
    case GetAllLands =>
      sender ! lands.values.toList

    case GetLand(name) => sender ! lands.get(name)

    case land@AddLand(name, description, area, lat, lon, zoom, bearing, polygon) =>
      if (lands.exists {
        case (_, land) => land.name == name
      }) {
        log.info(s"[$persistenceId] Land $name already exists")
        sender ! Error(s"Land $name already exists")
      } else {
        log.info(s"[$persistenceId] Adding land $land")
        persist(LandEntity(currentId, name, description, area, lat, lon, zoom, bearing, polygon, new Date, new Date)) { land =>
          context.become(landReceive(lands + (land.id -> land), currentId + 1, landObjectTypes, landTaskTypes, landObjects))
          sender ! Success(land)
        }
      }

    case ChangeLandDescription(id, description) =>
      lands.get(id) match {
        case Some(land) =>
          log.info(s"[$persistenceId] Changing $id description to: $description")
          val newLand = land.copy(description = description, modifiedAt = new Date)
          persist(newLand) { land =>
            context.become(landReceive(lands + (land.id -> land), currentId, landObjectTypes, landTaskTypes, landObjects))
            sender ! Success(land)
          }
        case None =>
          log.info(s"[$persistenceId] Land $id does not exist.")
          sender ! Error("The land requested does not exist")
      }

    case cmd@ChangePolygon(id, area, lat, lon, zoom, bearing, polygon) =>
      lands.get(id) match {
        case Some(land) =>
          log.info(s"[$persistenceId] Changing $id polygon to: $cmd")
          val newLand = LandEntity(id, land.name, land.description, area, lat, lon, zoom, bearing, polygon, land.createdAt, new Date)
          persist(newLand) { land =>
            context.become(landReceive(lands + (land.id -> land), currentId, landObjectTypes, landTaskTypes, landObjects))
            sender ! Success(land)
          }
        case None =>
          log.info(s"[$persistenceId] Land $id does not exist.")
          sender ! Error("The land requested does not exist")
      }

    case DeleteLand(id) =>
      lands.get(id) match {
        case Some(_) =>
          log.info(s"[$persistenceId] Deleting land $id")
          persist(DeletedLand(id)) { event =>
            landObjects
              .get(id)
              .orElse(Some(context.actorOf(ObjectManager.props(username, id), s"objects-$username-$id")))
              .foreach(_ ! ObjectManager.Destroy)
            landObjectTypes
              .get(id)
              .orElse(Some(context.actorOf(ObjectType.props(username, id), s"object-type-$username-$id")))
              .foreach(_ ! ObjectType.Destroy)
            landTaskTypes
              .get(id)
              .orElse(Some(context.actorOf(TaskType.props(username, id), s"task-type-$username-$id")))
              .foreach(_ ! TaskType.Destroy)
            context.become(landReceive(lands - event.id, currentId, landObjectTypes, landTaskTypes, landObjects))
            sender ! Success()
          }
        case None =>
          log.info(s"[$persistenceId] Land $id does not exist.")
          sender ! Error("The land requested does not exist")
      }

    case Destroy =>
      log.info(s"[$persistenceId] Destroying all lands...")
      lands.keys.foreach(self ! DeleteLand(_))
      self ! Implode
      sender ! Success()

    case ReceiveTimeout =>
      log.info(s"[$persistenceId] Actor idle, stopping...")
      context.stop(self)

    case Implode =>
      log.info(s"[$persistenceId] Destroying land manager...")
      deleteMessages(lastSequenceNr)
      context.stop(self)


    case LandObjectTypesCommand(id, cmd) =>
      lands.get(id) match {
        case Some(land) =>
          val objTypesActor: ActorRef = landObjectTypes.getOrElse(id, {
            log.info(s"[$persistenceId] Creating object types actor for land ${land.name}")
            val actor = context.actorOf(ObjectType.props(username, id), s"object-type-$username-$id")
            context.watch(actor)
            context.become(landReceive(lands, currentId, landObjectTypes + (id -> actor), landTaskTypes, landObjects))
            actor
          })
          objTypesActor.forward(cmd)

        case None => sender ! Error(s"Land with ID $id does not exist")
      }

    case LandTaskTypesCommand(id, cmd) =>
      lands.get(id) match {
        case Some(land) =>
          val taskTypesActor: ActorRef = landTaskTypes.getOrElse(id, {
            log.info(s"[$persistenceId] Creating task types actor for land ${land.name}")
            val actor = context.actorOf(TaskType.props(username, id), s"task-type-$username-$id")
            context.watch(actor)
            context.become(landReceive(lands, currentId, landObjectTypes, landTaskTypes + (id -> actor), landObjects))
            actor
          })
          taskTypesActor.forward(cmd)

        case None => sender ! Error(s"Land with ID $id does not exist")
      }

    case LandObjectsCommand(id, cmd) =>
      lands.get(id) match {
        case Some(land) =>
          val objectsActor: ActorRef = landObjects.getOrElse(id, {
            log.info(s"[$persistenceId] Creating objects actor for land ${land.name}")
            val actor = context.actorOf(ObjectManager.props(username, id), s"objects-$username-$id")
            context.watch(actor)
            context.become(landReceive(lands, currentId, landObjectTypes, landTaskTypes, landObjects + (id -> actor)))
            actor
          })
          objectsActor.forward(cmd)

        case None => sender ! Error(s"Land with ID $id does not exist")
      }

    case Terminated(actorRef) =>
      context.become(
        landReceive(
          lands,
          currentId,
          landObjectTypes.filterNot(_._2 == actorRef),
          landTaskTypes.filterNot(_._2 == actorRef),
          landObjects.filterNot(_._2 == actorRef)
        )
      )
      log.info(s"[$persistenceId] $actorRef was removed from active actors")

  }
}
