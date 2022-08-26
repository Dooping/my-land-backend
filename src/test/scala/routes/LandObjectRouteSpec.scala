package routes

import actors.UserManagement
import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply._
import akka.testkit.{TestActor, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protocols.ObjectManagerJsonProtocol

import java.util.Date

class LandObjectRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with ObjectManagerJsonProtocol {

  import actors.Land.LandObjectsCommand
  import actors.ObjectManager._
  import actors.ObjectManagerSpec._
  import actors.UserManagement.LandCommand

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val testUsername = "david gago"
  private val testLandId = 1


  private val testProbe = TestProbe("objectManagerProbe")

  "A land object route" should {
    val userManagement = system.actorOf(Props(new UserManagement() {
      override def receiveCommand: Receive = {
        case LandCommand(_, cmd) =>
          testProbe.ref.forward(cmd)
      }

      override def receiveRecover: Receive = Actor.emptyBehavior
    }), "user-management")

    "add a new object" in {
      val randomObject = genAddLandObject()
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandObjectsCommand(_, AddLandObject(element, status, typeId)) =>
          sender ! Success(LandObject(1, element, status, typeId, new Date, new Date))
          TestActor.KeepRunning
      })
      Post("/object", randomObject) ~> LandObjectRoute.route(userManagement, testUsername, testLandId) ~> check {
        val obj = responseAs[LandObject]
        status shouldBe StatusCodes.Created
        obj.element shouldBe randomObject.element
        obj.status shouldBe randomObject.status
        obj.typeId shouldBe randomObject.typeId
      }
    }

    "get all previously added objects" in {
      val listToReturn = List(genLandObjectEntity, genLandObjectEntity)
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandObjectsCommand(_, GetObjects) =>
          sender ! listToReturn
          TestActor.KeepRunning
      })
      Get("/object") ~> LandObjectRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objects = responseAs[List[LandObject]]

        contentType shouldBe ContentTypes.`application/json`
        status shouldBe StatusCodes.OK
        objects should have length listToReturn.length
      }
    }

    "change existing object" in {
      val payload = genAddLandObject()
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandObjectsCommand(_, ChangeLandObject(id, element, status, typeId)) =>
          sender ! Success(LandObject(id, element, status, typeId, new Date, new Date))
          TestActor.KeepRunning
      })
      Put("/object/1", payload) ~> LandObjectRoute.route(userManagement, testUsername, testLandId) ~> check {
        val obj = responseAs[LandObject]
        status shouldBe StatusCodes.OK
        obj.id shouldBe 1
        obj.element shouldBe payload.element
        obj.status shouldBe payload.status
        obj.typeId shouldBe payload.typeId
      }
    }

    "delete existing object" in {
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandObjectsCommand(_, DeleteLandObject(_)) =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete("/object/1") ~> LandObjectRoute.route(userManagement, testUsername, testLandId) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "delete all objects of some type" in {
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandObjectsCommand(_, DeleteByType(_)) =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete("/object?type=1") ~> LandObjectRoute.route(userManagement, testUsername, testLandId) ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }
}
