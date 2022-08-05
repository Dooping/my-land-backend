package routes

import actors.{LandSpec, UserManagement}
import akka.actor.Props
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply._
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protocols.LandJsonProtocol

import scala.concurrent.Await
import scala.util.Random
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

  private val testUsername = "david gago"

  private val testProbe = TestProbe("userManagement")

  "A land route" should {

    val userManagement = system.actorOf(Props(new UserManagement() {
      override def receiveCommand: Receive = {
        case LandCommand(_, cmd: Command) =>
          testProbe.ref.forward(cmd)
      }
    }), "user-management")

    "register a new land" in {
      val randomLand = generateRandomAddLand(Some("some land name"))
      Post("/land", randomLand) ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case _: AddLand => testProbe.reply(Success())
        }
        status shouldBe StatusCodes.Created
      }
    }

    "not register a land with the same name" in {
      val randomLand = generateRandomAddLand(Some("some land name"))
      Post("/land", randomLand) ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case AddLand(name, _, _, _, _, _, _, _) => testProbe.reply(Error(s"Land $name already exists"))
        }
        val strictEntityFuture = responseEntity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        strictEntity.data.utf8String shouldBe s"Land ${randomLand.name} already exists"
        status shouldBe StatusCodes.BadRequest
      }
    }

    "get all previously registered lands" in {
      Get("/land") ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case GetAllLands =>
            testProbe.reply(List(generateRandomLandEntity(Some(1)), generateRandomLandEntity(Some(2))))
        }
        val lands = responseAs[List[LandEntity]]

        contentType shouldBe ContentTypes.`application/json`
        status shouldBe StatusCodes.OK
        lands should have length 2
      }
    }

    "get an existing land with parameter" in {
      Get("/land?id=1") ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case GetLand(id) => testProbe.reply(Some(generateRandomLandEntity(Some(id))))
        }
        val land = responseAs[Option[LandEntity]]
        status shouldBe StatusCodes.OK
        land.get.id shouldBe 1
      }
    }

    "get an existing land" in {
      Get("/land/1") ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case GetLand(id) => testProbe.reply(Some(generateRandomLandEntity(Some(id))))
        }
        val land = responseAs[Option[LandEntity]]
        status shouldBe StatusCodes.OK
        land.get.id shouldBe 1
      }
    }

    "change an existing land's description" in {
      val testLandId = 1
      val testLandNewDescription = "some hanged description"
      Patch("/land", ChangeLandDescription(testLandId, testLandNewDescription)) ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case ChangeLandDescription(id, description) => testProbe.reply(Success(generateRandomLandEntity(Some(id)).copy(description = description)))
        }
        val land = responseAs[LandEntity]
        status shouldBe StatusCodes.OK
        land.id shouldBe testLandId
        land.description shouldBe testLandNewDescription
      }
    }

    "change an existing land's polygon attributes" in {
      val testLandId = 1
      Patch("/land", ChangePolygon(testLandId, 1L, 2L, 3L, 4L, 5L, "changedPolygon")) ~> LandRoute.route(userManagement, testUsername) ~> check {
        testProbe.receiveWhile() {
          case ChangePolygon(id, area, lat, lon, zoom, bearing, polygon) =>
            testProbe.reply(Success(LandEntity(id, "land name", "should not matter", area, lat, lon, zoom, bearing, polygon)))
        }
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
