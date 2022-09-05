package actors.lifecycle

import actors.{Land, LandSpec, ObjectManager}
import akka.actor.{ActorSystem, Props}
import akka.pattern.StatusReply.Success
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{EventFilter, ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.language.postfixOps

class ObjectManagerLifecycleSpec
  extends TestKit(ActorSystem("ObjectManagerLifecycleSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
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

  import Land._
  import LandSpec.generateRandomAddLand
  import ObjectManager._

  val testUsername = "test"

  "A land actor" should {
    "create an ObjectManager actor when it doesn't exist" in {
      val landActor = system.actorOf(Land.props(testUsername))
      val land = generateRandomAddLand()
      landActor ! land
      receiveN(1)
      EventFilter.info(s"[land-$testUsername] Creating objects actor for land ${land.name}", occurrences = 1) intercept {
        landActor ! LandObjectsCommand(1, GetObjects)
        expectMsgType[List[LandObject]]
      }
    }

    "use an existing ObjectManager actor when it exists" in {
      val landActor = system.actorOf(Land.props(testUsername))
      val land = generateRandomAddLand()
      landActor ! land
      landActor ! LandObjectsCommand(1, GetObjects)
      receiveN(2)
      EventFilter.info(s"[land-$testUsername] Creating objects actor for land ${land.name}", occurrences = 0) intercept {
        landActor ! LandObjectsCommand(1, GetObjects)
        expectMsgType[List[LandObject]]
      }
    }

    "remove an ObjectManager actor when it idles for too long" in {
      val landProbe = TestProbe("land")
      EventFilter.info(s"[object-manager-$testUsername-1] Actor idle, stopping...", occurrences = 1) intercept {
        val objectManagerActor = landProbe.childActorOf(Props(new ObjectManager(testUsername, 1, 100 millis)))
        landProbe.watch(objectManagerActor)
        landProbe.expectTerminated(objectManagerActor, 500 millis)
      }
    }

    "signal the corresponding ObjectManager when a land is deleted" in {
      val landActor = system.actorOf(Land.props(testUsername))
      val land = generateRandomAddLand()
      landActor ! land
      landActor ! LandObjectsCommand(1, GetObjects)
      receiveN(2)
      EventFilter.info(s"[object-manager-$testUsername-1] Destroying actor and all data", occurrences = 1) intercept {
        landActor ! DeleteLand(1)
        expectMsg(Success())
      }
    }
  }

}
