package routes

import actors.UserManagement
import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply._
import akka.testkit.{TestActor, TestKit, TestProbe}
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
  with ObjectTypeJsonProtocol
  with SprayJsonSupport {

  import actors.ObjectTypeSpec._
  import actors.ObjectType._
  import actors.Land.LandObjectTypesCommand
  import actors.Land
  import actors.UserManagement.LandCommand

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val testUsername = "david gago"
  private val testLandId = 1


  private val testProbe = TestProbe("objectTypeProbe")

  "An object type route" should {
    Thread.sleep(500)

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

      override def receiveRecover: Receive = Actor.emptyBehavior
    }), "user-management")

    "register a new object type" in {
      val randomObjectType = generateObjectType
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case AddObjectType(obj) =>
          sender ! Success(generateObjectTypeEntity.sample.get.copy(name = obj.name, color = obj.color, icon = obj.icon))
          TestActor.KeepRunning
      })
      Post("/objectType", randomObjectType) ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objType = responseAs[ObjectTypeEntity]
        status shouldBe StatusCodes.Created
        objType.name shouldBe randomObjectType.name
        objType.icon shouldBe randomObjectType.icon
        objType.color shouldBe randomObjectType.color
      }
    }

    "register multiple object types" in {
      val randomObjectTypeList = generateObjectTypeList
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case BatchAddObjectType(objects) =>
          val result = objects.map(obj => generateObjectTypeEntity.sample.get.copy(name = obj.name, color = obj.color, icon = obj.icon))
          sender ! Success(result)
          TestActor.KeepRunning
      })
      Post("/objectType", randomObjectTypeList) ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objTypes = responseAs[List[ObjectTypeEntity]]
        status shouldBe StatusCodes.Created
        objTypes.length shouldBe randomObjectTypeList.length
      }
    }

    "get all previously registered lands" in {
      val listToReturn = generateObjectTypeListEntity
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetObjectTypes =>
          sender ! listToReturn
          TestActor.KeepRunning
      })
      Get("/objectType") ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objectTypes = responseAs[List[ObjectTypeEntity]]

        contentType shouldBe ContentTypes.`application/json`
        status shouldBe StatusCodes.OK
        objectTypes should have length listToReturn.length
      }
    }

    "change existing object type" in {
      val payload = generateObjectType
      val objTypeId = 1
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ChangeObjectType(id, obj) =>
          sender ! Success(ObjectTypeEntity(id, obj.name, obj.color, obj.icon, dateGen.sample.get, new Date))
          TestActor.KeepRunning
      })
      Put(s"/objectType/$objTypeId", payload) ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
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
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case DeleteObjectType(id) =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete(s"/objectType/$objTypeId") ~> ObjectTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }
}
