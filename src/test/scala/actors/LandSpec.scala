package actors

import akka.actor.{ActorSystem, Kill}
import akka.pattern.StatusReply._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.language.postfixOps
import scala.util.Random

class LandSpec
  extends TestKit(ActorSystem("LandSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication())))
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import Land._

  implicit val random: Random = new Random()

  "A land actor" should {

    "add a land correctly" in {
      val landActor = system.actorOf(Land.props("test1"))
      landActor ! generateRandomAddLand()
      expectMsg(Success)
    }

    "not be able to add two lands with the same name" in {
      val landActor = system.actorOf(Land.props("test2"))
      val landName = "Some land name"
      landActor ! generateRandomAddLand(landName)
      expectMsg(Success)

      landActor ! generateRandomAddLand(landName)
      expectMsg(Error(s"Land $landName already exists"))
    }

    "get a land registered before" in {
      val landActor = system.actorOf(Land.props("test3"))
      val landName = random.nextString(10)
      landActor ! generateRandomAddLand(landName)
      expectMsg(Success)
      landActor ! GetLand(landName)
      expectMsgType[Some[LandEntity]]
    }

    "get all lands registered before" in {
      val landActor = system.actorOf(Land.props("test4"))
      landActor ! GetAllLands
      val lands = expectMsgType[List[LandEntity]]
      assert(lands.isEmpty)

      landActor ! generateRandomAddLand()
      landActor ! generateRandomAddLand()
      receiveN(2)
      landActor ! GetAllLands
      val landsAfterAdding = expectMsgType[List[LandEntity]]
      assert(landsAfterAdding.length == 2)
    }

    "not find a user that does not exist" in {
      val landActor = system.actorOf(Land.props("test5"))

      landActor ! generateRandomAddLand()
      landActor ! generateRandomAddLand()
      receiveN(2)

      landActor ! GetLand("notALand")
      expectMsg[Option[LandEntity]](None)
    }

    "recover previously added lands" in {
      val landActor = system.actorOf(Land.props("test6"))
      val landName1 = "landName1"
      val landName2 = "landName2"
      landActor ! generateRandomAddLand(landName1)
      landActor ! generateRandomAddLand(landName2)
      receiveN(2)
      landActor ! Kill

      val anotherLandActor = system.actorOf(Land.props("test6"))
      anotherLandActor ! GetAllLands
      val landsAfterAdding = expectMsgType[List[LandEntity]]
      assert(landsAfterAdding.length == 2)
    }

    "change only the description when receiving ChangeLandDescription" in {
      val landActor = system.actorOf(Land.props("test7"))
      val landName = "landName"
      landActor ! generateRandomAddLand(landName)
      expectMsg(Success)
      landActor ! GetLand(landName)
      val land = expectMsgType[Some[LandEntity]]

      val newDescription = "someDescription"
      landActor ! ChangeLandDescription(landName, newDescription)
      landActor ! GetLand(landName)
      expectMsg(Success)
      val landWithNewDescription = expectMsgType[Some[LandEntity]]
      assert(land.value.copy(description = newDescription) == landWithNewDescription.value)
    }

    "change all fields except name & description when receiving ChangePolygon" in {
      val landActor = system.actorOf(Land.props("test7"))
      val land = generateRandomAddLand()
      landActor ! land
      expectMsg(Success)

      val changePolygon = ChangePolygon(land.name, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextString(20))
      landActor ! changePolygon
      expectMsg(Success)
      landActor ! GetLand(land.name)

      expectMsgPF() {
        case Some(LandEntity(land.name, land.description, changePolygon.area, changePolygon.lat, changePolygon.lon, changePolygon.zoom, changePolygon.bearing, changePolygon.polygon)) =>
      }
    }
  }

  def generateRandomAddLand(name: String = Random.nextString(10))(implicit random: Random): AddLand = AddLand(
    name,
    random.nextString(50),
    random.nextDouble(),
    random.nextDouble(),
    random.nextDouble(),
    random.nextDouble(),
    random.nextDouble(),
    ""
  )

}
