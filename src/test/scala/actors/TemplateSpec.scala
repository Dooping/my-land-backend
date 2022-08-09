package actors

import actors.LandSpec.{generateRandomAddLand, generateRandomLandEntity}
import actors.ObjectType.{BatchAddObjectType, GetObjectTypes, ObjectTypeEntity}
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
    templateActor = system.actorOf(Template.props(userManagerActor))
  }


  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
  }

  override def afterEach(): Unit = {
    super.afterEach()
  }

  private val username = "test"

  private val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  import Template._
  import ObjectTypeSpec._
  import Land._

  private val testingLocale = "en"
  private val testingName = "Agriculture"

  "A template actor create" should {

    "register a new template" in {
      templateActor ! RegisterNewLandTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())
    }

    "not create a duplicate template" in {
      templateActor ! RegisterNewLandTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Error(s"Template $testingName already exists for locale $testingLocale"))
    }
  }

  "A template actor put" should {
    "change an existing template" in {
      templateActor ! ChangeLandTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())
    }

    "fail changing a template that doesn't exist" in {
      val invalidTemplateName = "notATemplate"
      templateActor ! ChangeLandTemplate(testingLocale, invalidTemplateName, generateObjectTypeList)
      expectMsg(Error(s"Template $invalidTemplateName for locale $testingLocale does not exist"))
    }

    "fail changing a template in a locale that doesn't exist" in {
      val invalidLocale = "ot"
      templateActor ! ChangeLandTemplate(invalidLocale, testingName, generateObjectTypeList)
      expectMsg(Error(s"Locale $invalidLocale does not exist"))
    }
  }

  "A template actor delete" should {
    "delete an existing template" in {
      templateActor ! DeleteLandTemplate(testingLocale, testingName)
      expectMsg(Success())
    }

    "be successful if the locale exists but the name doesn't" in {
      templateActor ! RegisterNewLandTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())
      val nonExistingTemplate = "someTemplate"
      templateActor ! DeleteLandTemplate(testingLocale, nonExistingTemplate)
      expectMsg(Success())
    }

    "fail if the locale doesn't exist" in {
      val nonExistingLocale = "ot"
      templateActor ! DeleteLandTemplate(nonExistingLocale, testingName)
      expectMsg(Error(s"Locale $nonExistingLocale does not exist"))
    }
  }

  "A template actor get" should {
    "fetch a previously registered template" in {
      userManagerActor ! Register(username, "12345")
      expectMsg(Success())
      templateActor ! DeleteLandTemplate(testingLocale, testingName)
      expectMsg(Success())

      val newTemplate = generateObjectTypeList
      templateActor ! RegisterNewLandTemplate(testingLocale, testingName, newTemplate)
      expectMsg(Success())
      templateActor ! GetObjectTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.default(testingName) == newTemplate)
    }

    "fetch template of the right localization" in {
      val newTemplate = generateObjectTypeList
      val newName = nameGen.sample.get
      templateActor ! RegisterNewLandTemplate("ot", newName, newTemplate)
      expectMsg(Success())
      templateActor ! GetObjectTypeOptions(username, "ot")
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(newName))
      assert(response.default.size == 1)
      assert(response.default(newName) == newTemplate)
    }

    "fetch default template plus any templates in use by lands" in {
      val landTemplate = generateObjectTypeList
      val landName = nameGen.sample
      userManagerActor ! LandCommand(username, generateRandomAddLand(landName))
      val landEntity = expectMsgType[StatusReply[LandEntity]]
      userManagerActor ! LandCommand(username, LandObjectTypesCommand(landEntity.getValue.id, BatchAddObjectType(landTemplate)))
      val registeredLandTemplate = expectMsgType[StatusReply[ObjectTypeEntity]]

      templateActor ! GetObjectTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response.default.contains(testingName))
      assert(response.default.size == 1)
      assert(response.fromLands.size == 1)
      assert(response.fromLands.head.forall(landTemplate.contains(_)))
    }

    "fetch only default templates if land queries fail" in {
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

      templateActorTest ! RegisterNewLandTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())

      templateActorTest ! GetObjectTypeOptions(username, testingLocale)

      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

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
      templateActor ! RegisterNewLandTemplate(testingLocale, testingName, generateObjectTypeList)
      expectMsg(Success())

      templateActor ! GetObjectTypeOptions(username, testingLocale)
      val response = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue
      templateActor ! Kill

      templateActor = system.actorOf(Template.props(userProbe.ref))
      templateActor ! GetObjectTypeOptions(username, testingLocale)
      val responseAfterRestart = expectMsgType[StatusReply[ObjectTypeOptionsResponse]].getValue

      assert(response == responseAfterRestart)
    }
  }

}
