package actors

import akka.actor.{ActorLogging, ActorRef, Terminated}
import akka.persistence.PersistentActor
import akka.pattern.StatusReply._
import org.mindrot.jbcrypt.BCrypt

object UserManagement {
  sealed trait Command
  case class GetPassword(username: String) extends Command
  case class Register(username: String, password: String) extends Command
  case class LandCommand(username: String, cmd: Land.Command)

  case class StoredPassword(username: String, password: String)
}
class UserManagement extends PersistentActor with ActorLogging {
  import UserManagement._

  var users: Map[String, String] = Map("david" -> BCrypt.hashpw("p4ssw0rd", BCrypt.gensalt())) //TODO: remove initialization
  var userLands: Map[String, ActorRef] = Map()

  override val persistenceId: String = "user-manager"

  override def receiveCommand: Receive = {
    case Register(username, password) =>
      if (users.contains(username)) {
        log.info(s"[$persistenceId] User $username already exists")
        sender ! Error(s"User $username already exists")
      } else {
        log.info(s"[$persistenceId] User $username registered")
        persist(StoredPassword(username, password)) { user =>
          users += user.username -> user.password
          sender ! Success
        }
      }

    case GetPassword(username) if users.contains(username) => sender ! Success(users(username))
    case GetPassword(username) => sender ! Error(s"User $username does not exist")

    case LandCommand(username, cmd) =>
      if (users.contains(username)) {
        val userLand: ActorRef = userLands.getOrElse(username, {
          log.info(s"[$persistenceId] Creating land actor for $username")
          val land = context.actorOf(Land.props(username))
          log.info(land.path.toString)
          userLands += username -> land
          context.watch(land)
          land
        })
        userLand.forward(cmd)
      } else sender ! Error(s"User $username does not exist")

    case Terminated(actorRef) =>
      userLands = userLands.filterNot(_._2 == actorRef)
      log.info(s"[$persistenceId] $actorRef was removed from active actors")

    case _ => log.warning("should not be here")
  }

  override def receiveRecover: Receive = {
    case StoredPassword(username, password) =>
      log.info(s"Recovered user $username with pw $password")
      users += username -> password
  }
}
