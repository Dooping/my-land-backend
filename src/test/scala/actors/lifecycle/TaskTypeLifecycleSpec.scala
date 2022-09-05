package actors.lifecycle

import actors.{Land, LandSpec, TaskType}
import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, ReceiveTimeout}
import akka.pattern.StatusReply.Success
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.language.postfixOps

class TaskTypeLifecycleSpec
  extends TestKit(ActorSystem("TaskTypeLifecycleSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)
  var landActor: ActorRef = Actor.noSender

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
    landActor = system.actorOf(Land.props(username), s"land-$username")
  }

  override def afterEach(): Unit = {
    super.afterEach()
    landActor ! PoisonPill
    Thread.sleep(100)
  }

  import Land._
  import LandSpec._
  import TaskType._

  private def AddFirstLand() = {
    landActor ! generateRandomAddLand(Some(landName))
    receiveOne(1 second)
  }

  val username = "david"
  val landId = 1
  val landName = "land 1"

  "A Land actor" should {
    "create an ObjectType actor when forwarding the first message" in {
      AddFirstLand()
      EventFilter.info(s"[land-$username] Creating task types actor for land $landName", occurrences = 1) intercept {
        landActor ! LandTaskTypesCommand(landId, GetTaskTypes)
        expectMsgType[List[TaskTypeEntity]]
      }
    }

    "remove an ObjectType actor when it idles for to long" in {
      AddFirstLand()
      landActor ! LandTaskTypesCommand(landId, GetTaskTypes)
      expectMsgType[List[TaskTypeEntity]]
      val child = system.actorSelection(s"/user/land-$username/*")
      EventFilter.info(pattern = s"\\[land-$username\\] \\S* was removed from active actors", occurrences = 1).intercept {
        child ! ReceiveTimeout
      }
    }

    "recreate an ObjectType actor when another request is sent" in {
      AddFirstLand()
      EventFilter.info(s"[land-$username] Creating task types actor for land $landName", occurrences = 2).intercept {
        landActor ! LandTaskTypesCommand(landId, GetTaskTypes)
        expectMsgType[List[TaskTypeEntity]]
        val child = system.actorSelection(s"/user/land-$username/*")
        child ! ReceiveTimeout
        Thread.sleep(100)
        landActor ! LandTaskTypesCommand(landId, GetTaskTypes)
        expectMsgType[List[TaskTypeEntity]]
      }
    }


    "signal the corresponding TaskType actor when a land is deleted" in {
      val landActor = system.actorOf(Land.props(username))
      val land = generateRandomAddLand()
      landActor ! land
      landActor ! LandTaskTypesCommand(1, GetTaskTypes)
      receiveN(2)
      EventFilter.info(s"[task-types-$username-1] Destroying actor and all data", occurrences = 1) intercept {
        landActor ! DeleteLand(1)
        expectMsg(Success())
      }
    }

  }
}
