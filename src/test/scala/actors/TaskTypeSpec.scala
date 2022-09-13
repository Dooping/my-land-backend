package actors

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.StatusReply.{Error, Success}
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalacheck.Gen
import org.scalatest.wordspec._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.language.postfixOps

object TaskTypeSpec {
  import TaskType._

  private def genTaskType = for {
    name <- Gen.alphaStr
    description <- Gen.alphaStr
  } yield (name, description)
  def genTaskTypeModel: TaskTypeModel = TaskTypeModel tupled genTaskType.sample.get
  def genAddTaskType: AddTaskType = AddTaskType(genTaskTypeModel)
  def generateTaskTypeList: List[TaskTypeModel] = Gen.containerOfN[List, TaskTypeModel](Gen.choose(1, 10).sample.get, genTaskTypeModel).sample.get
}
class TaskTypeSpec
  extends TestKit(ActorSystem("TaskTypeSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)
  var taskTypeActor: ActorRef = Actor.noSender

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
    taskTypeActor = system.actorOf(Props(new TaskType(username, landId)))
  }

  override def afterEach(): Unit = {
    super.afterEach()
    taskTypeActor ! PoisonPill
  }

  import TaskType._
  import TaskTypeSpec._

  val username = "david"
  val landId = 3

  "A task type actor" should {
    "add a task type successfully" in {
      val addTaskType = genAddTaskType
      taskTypeActor ! addTaskType
      expectMsgPF() {
        case Success(TaskTypeEntity(_, name, description)) =>
          assert(name == addTaskType.taskType.name)
          assert(description == addTaskType.taskType.description)
      }
    }

    "change an existing task type's data" in {
      taskTypeActor ! genAddTaskType
      receiveOne(1 second)

      val newTaskType = genTaskTypeModel
      taskTypeActor ! ChangeTaskType(1, newTaskType)

      expectMsgPF() {
        case Success(TaskTypeEntity(_, name, description)) =>
          assert(name == newTaskType.name)
          assert(description == newTaskType.description)
        case msg =>
          fail
      }
    }

    "delete an existing object type" in {
      taskTypeActor ! genAddTaskType
      receiveOne(1 second)

      taskTypeActor ! DeleteTaskType(1)
      expectMsg(Success())
    }

    "delete a non existing object type" in {
      taskTypeActor ! DeleteTaskType(2)
      expectMsg(Error(s"No task type found with id 2"))
    }

    "add multiple object types" in {
      val batchList = generateTaskTypeList
      taskTypeActor ! BatchAddTaskType(batchList)

      expectMsgPF() {
        case Success(entities: List[TaskTypeEntity]) =>
          assert(entities.forall(o => batchList.exists(e => e.name == o.name && e.description == o.description)))
          assert(entities.length == batchList.length)
      }
    }

    "return all added object types" in {
      val batchList = generateTaskTypeList
      taskTypeActor ! BatchAddTaskType(batchList)
      receiveOne(1 second)

      taskTypeActor ! GetTaskTypes

      val taskTypes = expectMsgType[List[TaskTypeEntity]]
      assert(taskTypes.length == batchList.length)
    }

    "recover data when persistence fails" in {
      val taskType = genTaskTypeModel
      taskTypeActor ! AddTaskType(taskType)
      receiveN(1)

      persistenceTestKit.failNextPersisted()
      val taskType2 = genTaskTypeModel
      taskTypeActor ! AddTaskType(taskType2)

      Thread.sleep(200)

      taskTypeActor = system.actorOf(Props(new TaskType(username, landId)))

      taskTypeActor ! GetTaskTypes

      val allEntities = expectMsgType[List[TaskTypeEntity]]
      assert(allEntities.length == 1)
    }
  }

}
