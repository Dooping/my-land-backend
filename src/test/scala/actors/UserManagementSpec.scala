package actors

import akka.actor.{ActorSystem, Props}
import akka.pattern.StatusReply._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.language.postfixOps

class UserManagementSpec
  extends TestKit(ActorSystem("UserManagementSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication())))
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import UserManagement._


  val username = "testUsername"
  val passwordHashed = "testPasswordHash"

  "An authentication actor" should {
    val userManagementActor = system.actorOf(Props[UserManagement])

    "register a user correctly" in {
      userManagementActor ! Register(username, passwordHashed)
      expectMsg(Success())
    }

    "fail when registering a user again" in {
      userManagementActor ! Register(username, passwordHashed)
      expectMsg(Error(s"User $username already exists"))
    }

    "return hashed password of user" in {
      userManagementActor ! GetPassword("david")
      expectMsgPF() {
        case Success(_: String) =>
      }
    }
  }

}
