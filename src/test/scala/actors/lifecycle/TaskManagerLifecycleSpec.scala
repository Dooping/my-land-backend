package actors.lifecycle

import actors.{TaskManager, UserManagement}
import akka.actor.{ActorSystem, Props, ReceiveTimeout}
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.language.postfixOps

class TaskManagerLifecycleSpec
  extends TestKit(ActorSystem("TaskManagerLifecycleSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
  }

  import TaskManager._
  import UserManagement._

  val username = "david"

  "A UserManagement actor" should {
    val userManagement = system.actorOf(Props[UserManagement], "user-management")
    "create a Task actor when forwarding the first message" in {
      EventFilter.info(s"[user-manager] Creating task actor for $username", occurrences = 1) intercept {
        userManagement ! TaskCommand(username, GetAllTasks)
        expectMsgType[List[TaskEntity]]
      }
    }

    "remove a Task actor when it idles for to long" in {
      userManagement ! TaskCommand(username, GetAllTasks)
      expectMsgType[List[TaskEntity]]
      val child = system.actorSelection("/user/user-management/*")
      EventFilter.info(pattern = s"\\[user-manager\\] \\S* was removed from active actors", occurrences = 1).intercept {
        child ! ReceiveTimeout
      }
    }

    "recreate a Land actor when another request is sent" in {
      EventFilter.info(s"[user-manager] Creating task actor for $username", occurrences = 2).intercept {
        userManagement ! TaskCommand(username, GetAllTasks)
        expectMsgType[List[TaskEntity]]
        val child = system.actorSelection("/user/user-management/*")
        child ! ReceiveTimeout
        Thread.sleep(100)
        userManagement ! TaskCommand(username, GetAllTasks)
        expectMsgType[List[TaskEntity]]
      }
    }

  }
}
