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
import protocols.TaskJsonProtocol

import java.util.Date
import scala.language.postfixOps

class TaskRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with TaskJsonProtocol {

  import actors.TaskManager._
  import actors.TaskManagerSpec._
  import actors.UserManagement.TaskCommand

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val testUsername = "david gago"
  private val testLandId = 1


  private val testProbe = TestProbe("taskProbe")

  private val userManagement = system.actorOf(Props(new UserManagement() {
    override def receiveCommand: Receive = {
      case TaskCommand(_, cmd) =>
        testProbe.ref.forward(cmd)
    }

    override def receiveRecover: Receive = Actor.emptyBehavior
  }), "user-management")

  "A land task route" should {
    "create a new task" in {
      val randomTask = genTaskModel().sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case CreateTask(landId, task) =>
          sender ! Success(task.toEntity(1, landId))
          TestActor.KeepRunning
      })
      Post(s"/land/$testLandId/task", randomTask) ~> LandRoute.route(userManagement, testUsername) ~> check {
        val task = responseAs[TaskEntity]
        status shouldBe StatusCodes.Created
        task.landId shouldBe 1
        task.objectId shouldBe randomTask.objectId
        task.taskTypeId shouldBe randomTask.taskTypeId
        task.priority shouldBe randomTask.priority
        task.notes shouldBe randomTask.notes
      }
    }

    "register multiple tasks" in {
      val randomTaskList = genTaskModelList.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case CreateTasks(landId, tasks) =>
          val zippedList = tasks.zip(LazyList.from(1))
          val result = zippedList.map { case (task, id) => task.toEntity(id, landId) }
          sender ! Success(result)
          TestActor.KeepRunning
      })
      Post(s"/land/$testLandId/task", randomTaskList) ~> LandRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[List[TaskEntity]]
        status shouldBe StatusCodes.Created
        tasks.length shouldBe randomTaskList.length
      }
    }

    "get all tasks from land" in {
      val randomTaskList = genTaskModelList.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetLandTasks(landId) =>
          val zippedList = randomTaskList.zip(LazyList.from(1))
          val result = zippedList.map { case (task, id) => task.toEntity(id, landId) }
          sender ! result
          TestActor.KeepRunning
      })
      Get(s"/land/$testLandId/task") ~> LandRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[List[TaskEntity]]
        status shouldBe StatusCodes.OK
        tasks.length shouldBe randomTaskList.length
      }
    }

    "get all tasks from a land's object" in {
      val randomTaskList = genTaskModelList.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetLandObjectTasks(landId, _) =>
          val zippedList = randomTaskList.zip(LazyList.from(1))
          val result = zippedList.map { case (task, id) => task.toEntity(id, landId) }
          sender ! result
          TestActor.KeepRunning
      })
      Get(s"/land/$testLandId/task?object=1") ~> LandRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[List[TaskEntity]]
        status shouldBe StatusCodes.OK
        tasks.length shouldBe randomTaskList.length
      }
    }
  }

  "A task route" should {
    "get all tasks" in {
      val randomTaskList = genTaskModelList.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetAllTasks =>
          val zippedList = randomTaskList.zip(LazyList.from(1))
          val result = zippedList.map { case (task, id) => task.toEntity(id, testLandId) }
          sender ! result
          TestActor.KeepRunning
      })
      Get(s"/task") ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[List[TaskEntity]]
        status shouldBe StatusCodes.OK
        tasks.length shouldBe randomTaskList.length
      }
    }

    "get open tasks" in {
      val randomTaskList = genTaskModelList.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetOpenTasks =>
          val zippedList = randomTaskList.zip(LazyList.from(1))
          val result = zippedList.map { case (task, id) => task.toEntity(id, testLandId) }
          sender ! result
          TestActor.KeepRunning
      })
      Get(s"/task?query=open") ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[List[TaskEntity]]
        status shouldBe StatusCodes.OK
        tasks.length shouldBe randomTaskList.length
      }
    }

    "get season tasks" in {
      val randomTaskList = genTaskModelList.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetSeasonTasks =>
          val zippedList = randomTaskList.zip(LazyList.from(1))
          val result = zippedList.map { case (task, id) => task.toEntity(id, testLandId) }
          sender ! result
          TestActor.KeepRunning
      })
      Get(s"/task?query=season") ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[List[TaskEntity]]
        status shouldBe StatusCodes.OK
        tasks.length shouldBe randomTaskList.length
      }
    }

    "change a task" in {
      val randomTask = genTaskModel().sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ModifyTask(id, task) =>
          sender ! Success(task.toEntity(id, 1))
          TestActor.KeepRunning
      })
      Put(s"/task/1", randomTask) ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        val tasks = responseAs[TaskEntity]
        status shouldBe StatusCodes.OK
        randomTask.objectId shouldBe randomTask.objectId
        randomTask.taskTypeId shouldBe randomTask.taskTypeId
        randomTask.priority shouldBe randomTask.priority
        randomTask.notes shouldBe randomTask.notes
      }
    }

    "delete a task" in {
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case DeleteTask(id) =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete(s"/task/1") ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "complete a task" in {
      val randomTask = genTaskModel().sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case CompleteTask(id) =>
          sender ! Success(randomTask.toEntity(id, 1).copy(completedAt = Some(new Date)))
          TestActor.KeepRunning
      })
      Put(s"/task/1/complete", randomTask) ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        responseAs[TaskEntity]
        status shouldBe StatusCodes.OK
      }
    }

    "archive a task" in {
      val randomTask = genTaskModel().sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ArchiveTask(id) =>
          sender ! Success(randomTask.toEntity(id, 1).copy(archivedAt = Some(new Date)))
          TestActor.KeepRunning
      })
      Put(s"/task/1/archive", randomTask) ~> TaskManagerRoute.route(userManagement, testUsername) ~> check {
        responseAs[TaskEntity]
        status shouldBe StatusCodes.OK
      }
    }
  }
}
