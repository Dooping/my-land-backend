package actors

import actors.TaskManagerSpec.{genCreateTask, genModifyTask}
import akka.actor.{Actor, ActorSystem, PoisonPill}
import akka.pattern.StatusReply._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.language.postfixOps

object TaskManagerSpec {
  import TaskManager._

  def genTaskModel(objectId: Option[Int] = None): Gen[TaskModel] = for {
    objId <- objectId.map(Gen.const).getOrElse(Gen.choose(1,5))
    taskType <- Gen.choose(1, 5)
    priority <- Gen.choose(1, 5)
    notes <- Gen.alphaStr
  } yield TaskModel(objId, taskType, priority, notes)
  def genTaskModelList: Gen[List[TaskModel]] = Gen.containerOfN[List, TaskModel](Gen.choose(1, 5).sample.get, genTaskModel())
  def genCreateTask(landId: Int, objectId: Option[Int] = None): CreateTask = CreateTask(landId, genTaskModel(objectId).sample.get)
  def genModifyTask(id: Int): ModifyTask = ModifyTask(id, genTaskModel().sample.get)
  def genCreateTasks(landId: Int): CreateTasks = CreateTasks(
    landId,
    genTaskModelList.sample.get
  )
}
class TaskManagerSpec
  extends TestKit(ActorSystem("TaskManagerPackage", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender
    with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
  }

  override def afterEach(): Unit = {
    taskManagerActor ! PoisonPill
    super.afterEach()
  }

  import TaskManager._

  private val username = "test"
  private var taskManagerActor = Actor.noSender

  private val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  "A CreateTask" should {
    "create a new task" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      val createTask = genCreateTask(1)
      taskManagerActor ! createTask
      expectMsgPF() {
        case Success(TaskEntity(1, 1, createTask.task.objectId, createTask.task.taskTypeId, createTask.task.priority, createTask.task.notes, _, _, None, None)) =>
      }
    }

    "not create an object with the same id as a previous one" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      val createTask = genCreateTask(1)
      taskManagerActor ! createTask
      receiveOne(1 second)

      val ct2 = genCreateTask(1)
      taskManagerActor ! ct2
      expectMsgPF() {
        case Success(TaskEntity(2, 1, ct2.task.objectId, ct2.task.taskTypeId, ct2.task.priority, ct2.task.notes, _, _, None, None)) =>
      }
    }
  }

  "A DeleteTask" should {
    "delete an existing object" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      receiveOne(1 second)

      taskManagerActor ! DeleteTask(1)
      expectMsg(Success())
    }

    "fail when the object does not exist" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      EventFilter.info(s"[task-manager-$username] task 1 not found", occurrences = 1) intercept {
        taskManagerActor ! DeleteTask(1)
        expectMsg(Error("No task found with id 1"))
      }
    }
  }

  "A ModifyTask" should {
    "modify an existing object" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      receiveOne(1 second)

      val ct = genModifyTask(1)
      taskManagerActor ! ct
      expectMsgPF() {
        case Success(TaskEntity(ct.id, _, ct.taskModel.objectId, ct.taskModel.taskTypeId, ct.taskModel.priority, ct.taskModel.notes, createdAt, modifiedAt, _, _)) =>
          assert(modifiedAt.after(createdAt))
      }
    }

    "fail when the task does not exist" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      EventFilter.info(s"[task-manager-$username] task 1 not found", occurrences = 1) intercept {
        taskManagerActor ! genModifyTask(1)
        expectMsg(Error("No task found with id 1"))
      }
    }
  }

  "An ArchiveTask" should {
    "archive an existing task" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      receiveOne(1 second)

      taskManagerActor ! ArchiveTask(1)
      expectMsgPF() {
        case Success(TaskEntity(_, _, _, _, _, _, _, _, _, Some(_))) =>
      }
    }

    "fail when the task does not exist" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      EventFilter.info(s"[task-manager-$username] task 1 not found", occurrences = 1) intercept {
        taskManagerActor ! ArchiveTask(1)
        expectMsg(Error("No task found with id 1"))
      }
    }
  }

  "A CompleteTask" should {
    "set a task as done" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      receiveOne(1 second)

      taskManagerActor ! CompleteTask(1)
      expectMsgPF() {
        case Success(TaskEntity(_, _, _, _, _, _, _, _, Some(_), _)) =>
      }
    }

    "fail when the task does not exist" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      EventFilter.info(s"[task-manager-$username] task 1 not found", occurrences = 1) intercept {
        taskManagerActor ! CompleteTask(1)
        expectMsg(Error("No task found with id 1"))
      }
    }
  }

  "A GetAllTasks" should {
    "get an empty array when no tasks exist" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! GetAllTasks
      val tasks = expectMsgType[List[TaskEntity]]
      assert(tasks.isEmpty)
    }

    "get all tasks from the user" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! genCreateTask(2)
      receiveN(3)

      taskManagerActor ! GetAllTasks
      val tasks = expectMsgType[List[TaskEntity]]
      assert(tasks.length == 3)
    }
  }

  "A GetSeasonTasks" should {
    "get only non archived tasks" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! ArchiveTask(1)
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! CompleteTask(2)
      taskManagerActor ! ArchiveTask(2)
      taskManagerActor ! genCreateTask(2)
      receiveN(6)

      taskManagerActor ! GetSeasonTasks
      val tasks = expectMsgType[List[TaskEntity]]
      assert(tasks.length == 1)

      assert(tasks.head.id == 3)
    }
  }

  "A GetOpenTasks" should {
    "get only non archived and non completed tasks" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! ArchiveTask(1)
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! CompleteTask(3)
      receiveN(5)

      taskManagerActor ! GetOpenTasks
      val tasks = expectMsgType[List[TaskEntity]]
      assert(tasks.length == 1)

      assert(tasks.head.id == 2)
    }
  }

  "A GetLandTasks" should {
    "get only non archived tasks for the selected land" in {
      taskManagerActor = system.actorOf(TaskManager.props(username))
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! ArchiveTask(1)
      taskManagerActor ! genCreateTask(1)
      taskManagerActor ! genCreateTask(2)
      taskManagerActor ! CompleteTask(3)
      taskManagerActor ! genCreateTask(2)
      receiveN(6)

      taskManagerActor ! GetLandTasks(2)
      val tasks = expectMsgType[List[TaskEntity]]

      tasks should have length 2
      tasks.map(_.id) should contain only (3, 4)
    }
  }

}
