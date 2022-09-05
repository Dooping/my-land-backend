package actors

import akka.actor.{Actor, ActorRef, ActorSystem, Kill, PoisonPill, Props}
import akka.pattern.StatusReply
import akka.pattern.StatusReply._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalacheck.Gen
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import shapeless.syntax.std.tuple._

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.Random

object LandSpec {

  import actors.Land._

  private def landGen(name: Option[String]): Gen[(String, String, Double, Double, Double, Double, Double, String)] = for {
    name <- name.map(Gen.const).getOrElse(Gen.alphaStr)
    description <- Gen.alphaStr
    area <- Gen.double
    lat <- Gen.double
    lon <- Gen.double
    zoom <- Gen.double
    bearing <- Gen.double
    polygon <- Gen.alphaStr
  } yield (name, description, area, lat, lon, zoom, bearing, polygon)

  def generateRandomAddLand(name: Option[String] = None): AddLand = AddLand tupled landGen(name).sample.get


  def generateRandomLandEntity(id: Option[Int] = None): LandEntity = {
    val landEntityGen = for {
      id <- id.map(Gen.const).getOrElse(Gen.choose(1, 1000))
      land <- landGen(None)
    } yield LandEntity tupled id +: land
    landEntityGen.sample.get
  }
}
class LandSpec
  extends TestKit(ActorSystem("LandSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication())))
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val username = "test"

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)
  var landTestActor: ActorRef = Actor.noSender

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
    landTestActor = system.actorOf(Land.props(username))
  }

  override def afterEach(): Unit = {
    super.afterEach()
    landTestActor ! PoisonPill
  }

  import Land._
  import LandSpec._

  implicit val random: Random = new Random()

  "A land actor" should {

    "add a land correctly" in {
      landTestActor ! generateRandomAddLand()
      val entity = expectMsgType[StatusReply[LandEntity]]
      assert(entity.isSuccess)
      assert(entity.getValue.id == 1)
    }

    "not be able to add two lands with the same name" in {
      val landName = "Some land name"
      landTestActor ! generateRandomAddLand(Some(landName))
      val entity = expectMsgType[StatusReply[LandEntity]]
      assert(entity.isSuccess)

      landTestActor ! generateRandomAddLand(Some(landName))
      expectMsg(Error(s"Land $landName already exists"))
    }

    "get a land registered before" in {
      val landName = random.nextString(10)
      landTestActor ! generateRandomAddLand(Some(landName))
      val entity = expectMsgType[StatusReply[LandEntity]]
      assert(entity.isSuccess)
      landTestActor ! GetLand(1)
      expectMsgType[Some[LandEntity]]
    }

    "get all lands registered before" in {
      landTestActor ! GetAllLands
      val lands = expectMsgType[List[LandEntity]]
      assert(lands.isEmpty)

      landTestActor ! generateRandomAddLand()
      landTestActor ! generateRandomAddLand()
      receiveN(2)
      landTestActor ! GetAllLands
      val landsAfterAdding = expectMsgType[List[LandEntity]]
      assert(landsAfterAdding.length == 2)
    }

    "not find a user that does not exist" in {

      landTestActor ! generateRandomAddLand()
      landTestActor ! generateRandomAddLand()
      receiveN(2)

      landTestActor ! GetLand(-1)
      expectMsg[Option[LandEntity]](None)
    }

    "recover previously added lands" in {
      val landName1 = "landName1"
      val landName2 = "landName2"
      landTestActor ! generateRandomAddLand(Some(landName1))
      landTestActor ! generateRandomAddLand(Some(landName2))
      receiveN(2)
      landTestActor ! Kill

      Thread.sleep(100)
      landTestActor = system.actorOf(Land.props(username))
      landTestActor ! GetAllLands
      val landsAfterAdding = expectMsgType[List[LandEntity]]
      assert(landsAfterAdding.length == 2)
    }

    "change only the description when receiving ChangeLandDescription" in {
      val landName = "landName"
      landTestActor ! generateRandomAddLand(Some(landName))
      val entity = expectMsgType[StatusReply[LandEntity]]
      assert(entity.isSuccess)
      landTestActor ! GetLand(1)
      val land = expectMsgType[Some[LandEntity]]

      val newDescription = "someDescription"
      landTestActor ! ChangeLandDescription(1, newDescription)
      landTestActor ! GetLand(1)
      receiveN(1)
      val landWithNewDescription = expectMsgType[Some[LandEntity]]
      assert(land.value.copy(description = newDescription) == landWithNewDescription.value)
    }

    "change all fields except name & description when receiving ChangePolygon" in {
      val land = generateRandomAddLand()
      landTestActor ! land
      val entity = expectMsgType[StatusReply[LandEntity]]
      assert(entity.isSuccess)

      val changePolygon = ChangePolygon(1, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextString(20))
      landTestActor ! changePolygon
      receiveN(1)
      landTestActor ! GetLand(1)

      expectMsgPF() {
        case Some(LandEntity(1, land.name, land.description, changePolygon.area, changePolygon.lat, changePolygon.lon, changePolygon.zoom, changePolygon.bearing, changePolygon.polygon)) =>
      }
    }

    "delete a land" in {
      val land = generateRandomAddLand()
      landTestActor ! land
      receiveN(1)

      landTestActor ! DeleteLand(1)
      expectMsg(Success())

      landTestActor ! GetLand(1)
      expectMsg(None)
    }

    "timeout after the specified inactivity duration" in {
      val landTestActor = system.actorOf(Props(new Land("test8", 100 milliseconds)))
      watch(landTestActor)
      expectTerminated(landTestActor, 1 second)
    }
  }

}
