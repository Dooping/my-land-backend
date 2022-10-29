package actors

import actors.LandSpec.{generateRandomAddLand, generateRandomLandEntity}
import actors.ObjectType.{BatchAddObjectType, GetObjectTypes, ObjectTypeEntity}
import actors.TaskType.{BatchAddTaskType, GetTaskTypes, TaskTypeEntity}
import actors.TaskTypeSpec.generateTaskTypeList
import actors.TemplateSpec.nameGen
import actors.UserManagement.{LandCommand, Register}
import akka.actor.{Actor, ActorRef, ActorSystem, Kill, PoisonPill, Props}
import akka.pattern.StatusReply
import akka.pattern.StatusReply._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalacheck.Gen
import org.scalacheck.Gen._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Date
import scala.concurrent.duration._
import scala.language.postfixOps

object TemplateSpec {
  def localeGen: Gen[String] = oneOf("pt", "pl", "es", "fr", "de")
  def nameGen: Gen[String] = Gen.alphaStr
}
class TemplateSpec
  extends TestKit(ActorSystem("TemplateSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.defaultApplication())))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private var templateActor = Actor.noSender
  private var userManagerActor = Actor.noSender

  override def beforeAll(): Unit = {
    super.beforeAll()
    userManagerActor = system.actorOf(Props[UserManagement], "user-manager")
    userManagerActor ! Register(username, "12345")
    receiveN(1)
  }


  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
    templateActor = system.actorOf(Template.props(userManagerActor))
  }

  override def afterEach(): Unit = {
    templateActor ! PoisonPill
    super.afterEach()
  }

  private val username = "test"

  private val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  import Template._
  import ObjectTypeSpec._
  import Land._

  private val testingLocale = "en"
  private val testingName = "Agriculture"

  "A template actor POST" should {
    "register a new object type template" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())
    }

    "not create a duplicate object type template" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      receiveN(1)
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Error(s"Template $testingName already exists for locale $testingLocale"))
    }

    "register a new task type template" in {
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      expectMsg(Success())
    }

    "not create a duplicate task type template" in {
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      receiveN(1)
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      expectMsg(Error(s"Template $testingName already exists for locale $testingLocale"))
    }
  }

  "A template actor PUT" should {
    "change an existing object template" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      receiveN(1)
      templateActor ! ChangeLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())
    }

    "fail changing an object template that doesn't exist" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      receiveN(1)
      val invalidTemplateName = "notATemplate"
      templateActor ! ChangeLandObjectTemplate(testingLocale, invalidTemplateName, generateObjectTypeList)
      expectMsg(Error(s"Template $invalidTemplateName for locale $testingLocale does not exist"))
    }

    "fail changing an object template in a locale that doesn't exist" in {
      val invalidLocale = "ot"
      templateActor ! ChangeLandObjectTemplate(invalidLocale, testingName, generateObjectTypeList)
      expectMsg(Error(s"Locale $invalidLocale does not exist"))
    }

    "change an existing task template" in {
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      receiveN(1)
      templateActor ! ChangeTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      expectMsg(Success())
    }

    "fail changing a task template that doesn't exist" in {
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      receiveN(1)
      val invalidTemplateName = "notATemplate"
      templateActor ! ChangeTaskTemplate(testingLocale, invalidTemplateName, generateTaskTypeList)
      expectMsg(Error(s"Template $invalidTemplateName for locale $testingLocale does not exist"))
    }

    "fail changing a task template in a locale that doesn't exist" in {
      val invalidLocale = "ot"
      templateActor ! ChangeTaskTemplate(invalidLocale, testingName, generateTaskTypeList)
      expectMsg(Error(s"Locale $invalidLocale does not exist"))
    }
  }

  "A template actor DELETE" should {
    "delete an existing object template" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      receiveN(1)
      templateActor ! DeleteLandObjectTemplate(testingLocale, testingName)
      expectMsg(Success())
    }

    "delete an existing task template" in {
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      receiveN(1)
      templateActor ! DeleteTaskTemplate(testingLocale, testingName)
      expectMsg(Success())
    }

    "be successful if the locale exists but the name doesn't" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      receiveN(2)
      val nonExistingTemplate = "someTemplate"
      templateActor ! DeleteLandObjectTemplate(testingLocale, nonExistingTemplate)
      templateActor ! DeleteTaskTemplate(testingLocale, nonExistingTemplate)
      expectMsg(Success())
      expectMsg(Success())
    }

    "fail if the locale doesn't exist" in {
      val nonExistingLocale = "ot"
      templateActor ! DeleteLandObjectTemplate(nonExistingLocale, testingName)
      templateActor ! DeleteTaskTemplate(nonExistingLocale, testingName)
      expectMsg(Error(s"Locale $nonExistingLocale does not exist"))
      expectMsg(Error(s"Locale $nonExistingLocale does not exist"))
    }
  }

  "A template actor GET" should {
    "fetch a previously registered object template" in {
      val newTemplate = generateObjectTypeList
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, newTemplate)
      expectMsg(Success())
      templateActor ! GetObjectTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.default(testingName) == newTemplate)
    }

    "fetch object template of the right localization" in {
      val newTemplate = generateObjectTypeList
      val newName = nameGen.sample.get
      templateActor ! RegisterNewLandObjectTemplate("ot", newName, newTemplate)
      expectMsg(Success())
      templateActor ! GetObjectTypeOptions(username, "ot")
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(newName))
      assert(response.default.size == 1)
      assert(response.default(newName) == newTemplate)
    }

    "fetch default object template plus any object templates in use by lands" in {
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())
      val landTemplate = generateObjectTypeList
      val landName = nameGen.sample
      userManagerActor ! LandCommand(username, generateRandomAddLand(landName))
      val landEntity = expectMsgType[StatusReply[LandEntity]]
      userManagerActor ! LandCommand(username, LandObjectTypesCommand(landEntity.getValue.id, BatchAddObjectType(landTemplate)))
      val registeredLandTemplate = expectMsgType[StatusReply[ObjectTypeEntity]]
      assert(registeredLandTemplate.isSuccess)

      templateActor ! GetObjectTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.fromLands.size == 1)
      assert(response.fromLands.head.forall(landTemplate.contains(_)))
    }

    "fetch only default object templates if land queries fail" in {
      val userManagerProbe = TestProbe("user-manager")
      userManagerProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandCommand(_, GetAllLands) =>
          sender ! List(generateRandomLandEntity(Some(1)), generateRandomLandEntity(Some(2)), generateRandomLandEntity(Some(3)))
          TestActor.KeepRunning
        case LandCommand(_, LandObjectTypesCommand(id, GetObjectTypes)) if id > 1 =>
          sender ! generateObjectTypeListEntity.sample.get
          TestActor.KeepRunning
        case _ => TestActor.KeepRunning
      })
      val templateActorTest = system.actorOf(Template.props(userManagerProbe.ref))

      templateActorTest ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())

      templateActorTest ! GetObjectTypeOptions(username, testingLocale)

      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.fromLands.size == 2)
    }

    "fetch a previously registered task template" in {
      val newTemplate = generateTaskTypeList
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, newTemplate)
      expectMsg(Success())
      templateActor ! GetTaskTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[TaskTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.default(testingName) == newTemplate)
    }

    "fetch task template of the right localization" in {
      val newTemplate = generateTaskTypeList
      val newName = nameGen.sample.get
      templateActor ! RegisterNewTaskTemplate("ot", newName, newTemplate)
      expectMsg(Success())
      templateActor ! GetTaskTypeOptions(username, "ot")
      val response = expectMsgType[StatusReply[TaskTypeOptionsResponse]].getValue

      assert(response.default.contains(newName))
      assert(response.default.size == 1)
      assert(response.default(newName) == newTemplate)
    }

    "fetch default task template plus any task templates in use by lands" in {
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      expectMsg(Success())
      val landTemplate = generateTaskTypeList
      val landName = nameGen.sample
      userManagerActor ! LandCommand(username, LandTaskTypesCommand(1, BatchAddTaskType(landTemplate)))
      val registeredLandTemplate = expectMsgType[StatusReply[TaskTypeEntity]]
      assert(registeredLandTemplate.isSuccess)

      templateActor ! GetTaskTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[TaskTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.fromLands.size == 1)
      assert(response.fromLands.head.forall(landTemplate.contains(_)))
    }

    "fetch only default task templates if land queries fail" in {
      val userManagerProbe = TestProbe("user-manager")
      userManagerProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case LandCommand(_, GetAllLands) =>
          sender ! List(generateRandomLandEntity(Some(1)), generateRandomLandEntity(Some(2)), generateRandomLandEntity(Some(3)))
          TestActor.KeepRunning
        case LandCommand(_, LandTaskTypesCommand(id, GetTaskTypes)) if id > 1 =>
          sender ! generateTaskTypeList.map(t => TaskTypeEntity(1, t.name, t.description, new Date, new Date))
          TestActor.KeepRunning
        case _ => TestActor.KeepRunning
      })
      val templateActorTest = system.actorOf(Template.props(userManagerProbe.ref))

      templateActorTest ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      expectMsg(Success())

      templateActorTest ! GetTaskTypeOptions(username, testingLocale)

      val response = expectMsgType[StatusReply[TaskTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.fromLands.size == 2)
    }
  }

  "A template actor state" should {
    "be the same after restarting" in {
      val userProbe = TestProbe("user")
      userProbe.setAutoPilot((sender: ActorRef, msg: Any) => {
        sender ! List()
        TestActor.KeepRunning
      })

      templateActor ! PoisonPill
      templateActor = system.actorOf(Template.props(userProbe.ref))
      templateActor ! RegisterNewLandObjectTemplate(testingLocale, testingName, generateObjectTypeList)
      templateActor ! RegisterNewTaskTemplate(testingLocale, testingName, generateTaskTypeList)
      receiveN(2)

      templateActor ! GetObjectTypeOptions(username, testingLocale)
      templateActor ! GetTaskTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue
      val response2 = expectMsgType[StatusReply[TaskTypeOptionsResponse]].getValue
      templateActor ! Kill

      templateActor = system.actorOf(Template.props(userProbe.ref))
      templateActor ! GetObjectTypeOptions(username, testingLocale)
      templateActor ! GetTaskTypeOptions(username, testingLocale)
      val responseAfterRestart = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue
      val responseAfterRestart2 = expectMsgType[StatusReply[TaskTypeOptionsResponse]].getValue

      assert(response == responseAfterRestart)
      assert(response2 == responseAfterRestart2)
    }
  }

}
