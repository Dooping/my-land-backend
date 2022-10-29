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
import protocols.TaskTypeJsonProtocol
import shapeless.syntax.std.tuple.productTupleOps

import java.util.Date
import scala.language.postfixOps

class TaskTypeRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with TaskTypeJsonProtocol
  with SprayJsonSupport {

  import actors.Land
  import actors.Land.LandTaskTypesCommand
  import actors.TaskType._
  import actors.TaskTypeSpec._
  import actors.UserManagement.LandCommand

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val testUsername = "david gago"
  private val testLandId = 1


  private val testProbe = TestProbe("taskTypeProbe")

  "A task type route" should {
    Thread.sleep(500)

    val landActor = system.actorOf(Props(new Land(testUsername) {
      override def receiveCommand: Receive = {
        case LandTaskTypesCommand(_, cmd) =>
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

    "register a new task type" in {
      val randomTaskType = genTaskTypeModel
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case AddTaskType(taskType) =>
          sender ! Success(TaskTypeEntity tupled 1 +: TaskTypeModel.unapply(taskType).get :+ new Date :+ new Date)
          TestActor.KeepRunning
      })
      Post("/taskType", randomTaskType) ~> TaskTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objType = responseAs[TaskTypeEntity]
        status shouldBe StatusCodes.Created
        objType.name shouldBe randomTaskType.name
        objType.description shouldBe randomTaskType.description
      }
    }

    "register multiple task types" in {
      val randomTaskTypeList = generateTaskTypeList
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case BatchAddTaskType(tasks) =>
          val zippedList = tasks.zip(LazyList.from(1))
          val now = new Date
          val result = zippedList.map { case (task, id) => TaskTypeEntity(id, task.name, task.description, now, now) }
          sender ! Success(result)
          TestActor.KeepRunning
      })
      Post("/taskType", randomTaskTypeList) ~> TaskTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objTypes = responseAs[List[TaskTypeEntity]]
        status shouldBe StatusCodes.Created
        objTypes.length shouldBe randomTaskTypeList.length
      }
    }

    "get all previously registered lands" in {
      val zippedList = generateTaskTypeList.zip(LazyList.from(1))
      val listToReturn = zippedList.map { case (task, id) => TaskTypeEntity(id, task.name, task.description, new Date, new Date) }
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetTaskTypes =>
          sender ! listToReturn
          TestActor.KeepRunning
      })
      Get("/taskType") ~> TaskTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val taskTypes = responseAs[List[TaskTypeEntity]]

        contentType shouldBe ContentTypes.`application/json`
        status shouldBe StatusCodes.OK
        taskTypes should have length listToReturn.length
      }
    }

    "change existing task type" in {
      val payload = genTaskTypeModel
      val taskTypeId = 1
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ChangeTaskType(id, task) =>
          sender ! Success(TaskTypeEntity(id, task.name, task.description, new Date, new Date))
          TestActor.KeepRunning
      })
      Put(s"/taskType/$taskTypeId", payload) ~> TaskTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        val objType = responseAs[TaskTypeEntity]
        status shouldBe StatusCodes.OK
        objType.id shouldBe taskTypeId
        objType.name shouldBe payload.name
        objType.description shouldBe payload.description
      }
    }

    "delete existing task type" in {
      val objTypeId = 1
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case DeleteTaskType(id) =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete(s"/taskType/$objTypeId") ~> TaskTypeRoute.route(userManagement, testUsername, testLandId) ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }
}
