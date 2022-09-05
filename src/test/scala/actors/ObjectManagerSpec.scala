package actors

import actors.ObjectManagerSpec.{genAddLandObject, genChangeLandObject}
import actors.ObjectTypeSpec.dateGen
import akka.actor.{Actor, ActorSystem, PoisonPill}
import akka.pattern.StatusReply._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalacheck.Gen
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.wordspec.AnyWordSpecLike
import shapeless.syntax.std.tuple.productTupleOps

import scala.concurrent.duration._
import scala.language.postfixOps

object ObjectManagerSpec {
  import ObjectManager._

  def genTypeId: Gen[Int] = Gen.choose(1, 5)
  private def genLandObject(typeId: Option[Int] = None) = for {
    element <- Gen.alphaStr
    status <- Gen.alphaStr
    typeId <- typeId.map(Gen.const).getOrElse(genTypeId)
  } yield (element, status, typeId)
  def genAddLandObject(typeId: Option[Int] = None): AddLandObject = AddLandObject tupled genLandObject(typeId).sample.get
  def genChangeLandObject(id: Option[Int] = None): ChangeLandObject = {
    val changeLandObjectGen = for {
      id <- id.map(Gen.const).getOrElse(Gen.choose(1, 1000))
      obj <- genLandObject()
    } yield ChangeLandObject tupled id +: obj
    changeLandObjectGen.sample.get
  }
  def genLandObjectEntity: LandObject = {
    val landObjectGen = for {
      id <- Gen.choose(1, 1000)
      obj <- genLandObject()
      modifiedAt <- dateGen
      createdAt <- dateGen
    } yield LandObject tupled id +: obj :+ modifiedAt :+ createdAt
    landObjectGen.sample.get
  }

}
class ObjectManagerSpec
  extends TestKit(ActorSystem("ObjectManagerPackage", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender{

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
  }

  override def afterEach(): Unit = {
    objectManagerActor ! PoisonPill
    super.afterEach()
  }

  import ObjectManager._

  private val username = "test"
  private val testLandId = 1
  private var objectManagerActor = Actor.noSender

  private val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)

  "An AddLandObject" should {
    "create a new object" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      val addLandObject = genAddLandObject()
      objectManagerActor ! addLandObject
      expectMsgPF() {
        case Success(LandObject(1, addLandObject.element, addLandObject.status, addLandObject.typeId, _, _)) =>
      }
    }

    "not create an object with the same id as a previous one" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      val addLandObject = genAddLandObject()
      objectManagerActor ! addLandObject
      receiveOne(1 second)

      val anotherAddLandObject = genAddLandObject()
      objectManagerActor ! anotherAddLandObject
      expectMsgPF() {
        case Success(LandObject(2, anotherAddLandObject.element, anotherAddLandObject.status, anotherAddLandObject.typeId, _, _)) =>
      }
    }
  }

  "A DeleteLandObject" should {
    "delete an existing object" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      objectManagerActor ! genAddLandObject()
      receiveOne(1 second)

      objectManagerActor ! DeleteLandObject(1)
      expectMsg(Success())
    }

    "fail when the object does not exist" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      EventFilter.warning(s"[object-manager-$username-$testLandId] Tried to delete a non existing object: 1", occurrences = 1) intercept {
        objectManagerActor ! DeleteLandObject(1)
        expectMsg(Error("Object 1 does not exist"))
      }
    }
  }

  "A ChangeLandObject" should {
    "modify an existing object" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      objectManagerActor ! genAddLandObject()
      receiveOne(1 second)

      val changeLandObject = genChangeLandObject(Some(1))
      objectManagerActor ! changeLandObject
      expectMsgPF() {
        case Success(LandObject(changeLandObject.id, changeLandObject.element, changeLandObject.status, changeLandObject.typeId, _, _)) =>
      }
    }

    "fail when the object does not exist" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      EventFilter.warning(s"[object-manager-$username-$testLandId] Tried to modify a non existing object: 1", occurrences = 1) intercept {
        objectManagerActor ! genChangeLandObject(Some(1))
        expectMsg(Error("Object 1 does not exist"))
      }
    }
  }

  "A GetObjects" should {
    "get an empty array when no objects exist" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      objectManagerActor ! GetObjects
      val objects = expectMsgType[List[LandObject]]
      assert(objects.isEmpty)
    }

    "get all objects from the land" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      objectManagerActor ! genAddLandObject()
      objectManagerActor ! genAddLandObject()
      objectManagerActor ! genAddLandObject()
      receiveN(3)

      objectManagerActor ! GetObjects
      val objects = expectMsgType[List[LandObject]]
      assert(objects.length == 3)
    }
  }

  "A DeleteByType" should {
    "delete all objects of a type" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      objectManagerActor ! genAddLandObject(Some(2))
      objectManagerActor ! genAddLandObject(Some(2))
      objectManagerActor ! genAddLandObject(Some(2))
      objectManagerActor ! genAddLandObject(Some(1))
      receiveN(4)

      objectManagerActor ! DeleteByType(2)
      expectMsg(Success())

      objectManagerActor ! GetObjects
      val result = expectMsgType[List[LandObject]]
      assert(result.length == 1)
    }

    "fail if any object fails to persist" in {
      objectManagerActor = system.actorOf(ObjectManager.props(username, testLandId))
      objectManagerActor ! genAddLandObject(Some(2))
      objectManagerActor ! genAddLandObject(Some(2))
      objectManagerActor ! genAddLandObject(Some(2))
      objectManagerActor ! genAddLandObject(Some(1))
      receiveN(4)

      persistenceTestKit.failNextNPersisted(2)
      objectManagerActor ! DeleteByType(2)
      expectNoMessage()
    }
  }

}
