package routes

import actors.UserManagement
import akka.actor.{Actor, Props}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply._
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protocols.ObjectTypeJsonProtocol

import java.util.Date
import scala.language.postfixOps

class ObjectTypeRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with ObjectTypeJsonProtocol {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import actors.ObjectTypeSpec._
  import actors.ObjectType._
  import actors.Land.LandObjectTypesCommand
  import actors.Land
  import actors.UserManagement.LandCommand

  private val testUsername = "david gago"
  private val testLandId = 1


  private val testProbe = TestProbe("objectTypeProbe")

  "An object type route" should {

    val landActor = system.actorOf(Props(new Land(testUsername) {
      override def receiveCommand: Receive = {
        case LandObjectTypesCommand(_, cmd) =>
          testProbe.ref.forward(cmd)
      }
      override def receiveRecover: Receive = Actor.emptyBehavior
    }), "land-1")

    val userManagement = system.actorOf(Props(new UserManagement() {
      override def receiveCommand: Receive = {
        case LandCommand(_, cmd) =>
          landActor.forward(cmd)
      }
    }), "user-management")

    "register a new object type" in {
      val randomObjectType = generateObjectType
      Post("/objectType", randomObjectType) ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        testProbe.receiveWhile() {
          case AddObjectType(obj) =>
            testProbe.reply(Success(generateObjectTypeEntity.sample.get.copy(name = obj.name, color = obj.color, icon = obj.icon)))
        }
        val objType = responseAs[ObjectTypeEntity]
        status shouldBe StatusCodes.Created
        objType.name shouldBe randomObjectType.name
        objType.icon shouldBe randomObjectType.icon
        objType.color shouldBe randomObjectType.color
      }
    }

    "get all previously registered lands" in {
      Get("/objectType") ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val listToReturn = generateObjectTypeListEntity
        testProbe.receiveWhile() {
          case GetObjectTypes =>
            testProbe.reply(listToReturn)
        }
        val objectTypes = responseAs[List[ObjectTypeEntity]]

        contentType shouldBe ContentTypes.`application/json`
        status shouldBe StatusCodes.OK
        objectTypes should have length listToReturn.length
      }
    }

    "change existing object type" in {
      val payload = generateObjectType
      val objTypeId = 1
      Put(s"/objectType/$objTypeId", payload) ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        testProbe.receiveWhile() {
          case ChangeObjectType(id, obj) =>
            testProbe.reply(Success(ObjectTypeEntity(id, obj.name, obj.color, obj.icon, dateGen.sample.get, new Date)))
        }
        val objType = responseAs[ObjectTypeEntity]
        status shouldBe StatusCodes.OK
        objType.id shouldBe objTypeId
        objType.name shouldBe payload.name
        objType.icon shouldBe payload.icon
        objType.color shouldBe payload.color
      }
    }

    "delete existing object type" in {
      val objTypeId = 1
      Delete(s"/objectType/$objTypeId") ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        testProbe.receiveWhile() {
          case DeleteObjectType(id) =>
            testProbe.reply(Success())
        }
        status shouldBe StatusCodes.OK
      }
    }
  }
}
