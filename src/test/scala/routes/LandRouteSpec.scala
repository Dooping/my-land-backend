package routes

import actors.{LandSpec, UserManagement}
import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply._
import akka.testkit.{TestActor, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protocols.LandJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class LandRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with LandJsonProtocol {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import actors.Land._
  import actors.UserManagement.LandCommand
  import LandSpec._

  private var userManagement: ActorRef = Actor.noSender

  override def beforeAll(): Unit = {
    super.beforeAll()
    userManagement = system.actorOf(Props(new UserManagement() {
      override def receiveCommand: Receive = {
        case LandCommand(_, cmd: Command) =>
          testProbe.ref.forward(cmd)
      }
    }), "user-management-2")
  }

  private val testUsername = "david gago"

  private val testProbe = TestProbe("userManagementProbe")

  "A land route" should {

    "register a new land" in {
      val randomLand = generateRandomAddLand(Some("some land name"))
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case _: AddLand =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Post("/land", randomLand) ~> LandRoute.route(userManagement, testUsername) ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    "not register a land with the same name" in {
      val randomLand = generateRandomAddLand(Some("some land name"))
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case AddLand(name, _, _, _, _, _, _, _) =>
          sender ! Error(s"Land $name already exists")
          TestActor.KeepRunning
      })
      Post("/land", randomLand) ~> LandRoute.route(userManagement, testUsername) ~> check {
        val strictEntityFuture = responseEntity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        strictEntity.data.utf8String shouldBe s"Land ${randomLand.name} already exists"
        status shouldBe StatusCodes.BadRequest
      }
    }

    "get all previously registered lands" in {
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetAllLands =>
          sender ! List(generateRandomLandEntity(Some(1)), generateRandomLandEntity(Some(2)))
          TestActor.KeepRunning
      })
      Get("/land") ~> LandRoute.route(userManagement, testUsername) ~> check {
        val lands = responseAs[List[LandEntity]]

        contentType shouldBe ContentTypes.`application/json`
        status shouldBe StatusCodes.OK
        lands should have length 2
      }
    }

    "get an existing land with parameter" in {
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetLand(id) =>
          sender ! Some(generateRandomLandEntity(Some(id)))
          TestActor.KeepRunning
      })
      Get("/land?id=1") ~> LandRoute.route(userManagement, testUsername) ~> check {
        val land = responseAs[Option[LandEntity]]
        status shouldBe StatusCodes.OK
        land.get.id shouldBe 1
      }
    }

    "get an existing land" in {
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetLand(id) =>
          sender ! Some(generateRandomLandEntity(Some(id)))
          TestActor.KeepRunning
      })
      Get("/land/1") ~> LandRoute.route(userManagement, testUsername) ~> check {
        val land = responseAs[Option[LandEntity]]
        status shouldBe StatusCodes.OK
        land.get.id shouldBe 1
      }
    }

    "change an existing land's description" in {
      val testLandId = 1
      val testLandNewDescription = "some hanged description"
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ChangeLandDescription(id, description) =>
          sender ! Success(generateRandomLandEntity(Some(id)).copy(description = description))
          TestActor.KeepRunning
      })
      Patch("/land", ChangeLandDescription(testLandId, testLandNewDescription)) ~> LandRoute.route(userManagement, testUsername) ~> check {
        val land = responseAs[LandEntity]
        status shouldBe StatusCodes.OK
        land.id shouldBe testLandId
        land.description shouldBe testLandNewDescription
      }
    }

    "change an existing land's polygon attributes" in {
      val testLandId = 1
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ChangePolygon(id, area, lat, lon, zoom, bearing, polygon) =>
          sender ! Success(LandEntity(id, "land name", "should not matter", area, lat, lon, zoom, bearing, polygon))
          TestActor.KeepRunning
      })
      Patch("/land", ChangePolygon(testLandId, 1L, 2L, 3L, 4L, 5L, "changedPolygon")) ~> LandRoute.route(userManagement, testUsername) ~> check {
        val land = responseAs[LandEntity]
        status shouldBe StatusCodes.OK
        land.id shouldBe testLandId
        land should matchPattern {
          case LandEntity(`testLandId`, _, _, 1L, 2L, 3L, 4L, 5L, "changedPolygon") =>
        }
      }
    }
  }
}
