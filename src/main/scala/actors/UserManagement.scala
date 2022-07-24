package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.{Actor, ActorLogging}
import akka.persistence.PersistentActor
import akka.pattern.StatusReply._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import org.mindrot.jbcrypt.BCrypt

object UserManagement {
  sealed trait Command
  case class GetPassword(username: String) extends Command
  case class Register(username: String, password: String) extends Command

  case class StoredPassword(username: String, password: String)
  /*final case class State(users: Map[String, String])

  // commands
  sealed trait Command
  case class GetPassword(username: String, replyTo: ActorRef[CommandResult]) extends Command
  case class Register(username: String, password: String, replyTo: ActorRef[CommandResult]) extends Command

  sealed trait CommandResult
  case class CommandFailed(reason: String) extends CommandResult
  case class CommandSuccess(data: String = "") extends CommandResult

  sealed trait Event
  //events
  case class StoredPassword(username: String, password: String) extends Event

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(Map()),
      commandHandler = commandHandler,
      eventHandler = eventHandler)

  val commandHandler: (State, Command) => ReplyEffect[Event, State] = { (state, command) =>
    command match {
      case Register(username, password, replyTo) =>
        if (state.users.contains(username)) {
          Effect.none.thenReply(replyTo)(_ => CommandFailed(s"User $username already exists"))
        } else {
          Effect.persist(StoredPassword(username, password)).thenReply(replyTo)(_ => CommandSuccess())
        }
      case GetPassword(username, replyTo) if state.users.contains(username) => Effect.none.thenReply(replyTo)(_ => CommandSuccess(state.users(username)))
      case GetPassword(username, replyTo) => Effect.reply(replyTo)(CommandFailed(s"User $username does not exist"))
      case _ => Effect.unhandled.thenNoReply()
    }
  }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case StoredPassword(username, password) => state.copy(state.users + (username -> password))
    }
  }*/
}
class UserManagement extends PersistentActor with ActorLogging {
  import UserManagement._

  var users: Map[String, String] = Map("david" -> BCrypt.hashpw("p4ssw0rd", BCrypt.gensalt()))

  override def persistenceId: String = "user-manager"

  override def receiveCommand: Receive = {
    case Register(username, password) =>
      if (users.contains(username)) {
        log.info(s"User $username already exists")
        sender ! Error(s"User $username already exists")
      } else {
        log.info(s"User $username registered")
        users += username -> password
        persistAsync(StoredPassword(username, password)) { _ =>
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
