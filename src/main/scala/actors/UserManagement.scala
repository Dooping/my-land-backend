package actors

import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import akka.pattern.StatusReply._
import org.mindrot.jbcrypt.BCrypt

object UserManagement {
  sealed trait Command
  case class GetPassword(username: String) extends Command
  case class Register(username: String, password: String) extends Command

  case class StoredPassword(username: String, password: String)
}
class UserManagement extends PersistentActor with ActorLogging {
  import UserManagement._

  var users: Map[String, String] = Map("david" -> BCrypt.hashpw("p4ssw0rd", BCrypt.gensalt()))

  override val persistenceId: String = "user-manager"

  override def receiveCommand: Receive = {
    case Register(username, password) =>
      if (users.contains(username)) {
        log.info(s"User $username already exists")
        sender ! Error(s"User $username already exists")
      } else {
        log.info(s"User $username registered")
        persist(StoredPassword(username, password)) { user =>
          users += user.username -> user.password
          sender ! Success
        }
      }

    case GetPassword(username) if users.contains(username) => sender ! Success(users(username))
    case GetPassword(username) => sender ! Error(s"User $username does not exist")
    case _ => log.warning("should not be here")
  }

  override def receiveRecover: Receive = {
    case StoredPassword(username, password) =>
      log.info(s"Recovered user $username with pw $password")
      users += username -> password
  }
}
