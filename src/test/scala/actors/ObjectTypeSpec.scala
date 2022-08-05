package actors

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.StatusReply.{Error, Success}
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.wordspec._

import java.time.{LocalDateTime, ZoneId}
import java.util.Date
import scala.concurrent.duration._
import scala.language.postfixOps


object ObjectTypeSpec {
  import ObjectType._

  private val genStringLazyList =  Gen.containerOfN[LazyList,Char](6, Gen.hexChar)
  private def objectTypeGen = for {
    name <- Gen.alphaStr
    color <- genStringLazyList
    icon <- Gen.oneOf("tree", "bush", "stone")
  } yield ObjType(name, "#" + color.mkString, icon)
  def generateObjectType: ObjType = objectTypeGen.sample.get
  def generateObjectTypeList: List[ObjType] = Gen.containerOfN[List, ObjType](Gen.choose(1, 10).sample.get,objectTypeGen).sample.get

  def dateGen: Gen[Date] =
    Gen.choose(
      min = LocalDateTime.of(2000,1, 1, 0, 0,0),
      max = LocalDateTime.now()
    ).map(_.atZone(ZoneId.systemDefault()).toInstant).map(Date.from)
  implicit val dateArb: Arbitrary[Date] = Arbitrary(dateGen)
  def generateObjectTypeEntity: Gen[ObjectTypeEntity] = {
    for {
      id <- Gen.choose(1, 1000)
      modifiedAt <- dateGen
      createdAt <- dateGen
      objType <- objectTypeGen
    } yield ObjectTypeEntity(id, objType.name, objType.color, objType.icon, createdAt, modifiedAt)
  }
  def generateObjectTypeListEntity: List[ObjectTypeEntity] = Gen.containerOfN[List, ObjectTypeEntity](Gen.choose(1, 10).sample.get,generateObjectTypeEntity).sample.get
}
class ObjectTypeSpec
  extends TestKit(ActorSystem("ObjectTypeSpec", PersistenceTestKitPlugin.config.withFallback(ConfigFactory.load().getConfig("interceptingLogMessages"))))
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val persistenceTestKit: PersistenceTestKit = PersistenceTestKit(system)
  var objectTypeActor: ActorRef = Actor.noSender

  override def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
    objectTypeActor = system.actorOf(Props(new ObjectType(username, landId)))
  }

  override def afterEach(): Unit = {
    super.afterEach()
    objectTypeActor ! PoisonPill
  }

  import ObjectType._
  import ObjectTypeSpec._

  val username = "david"
  val landId = 2

  "And object type actor" should {
    "add an object type successfully" in {
      val objectType = generateObjectType
      objectTypeActor ! AddObjectType(objectType)
      expectMsgPF() {
        case Success(ObjectTypeEntity(_, name, color, icon, _, _)) =>
          assert(name == objectType.name)
          assert(color == objectType.color)
          assert(icon == objectType.icon)
      }
    }

    "change an existing object type's data" in {
      objectTypeActor ! AddObjectType(generateObjectType)
      receiveOne(1 second)

      val newObjectType = generateObjectType
      objectTypeActor ! ChangeObjectType(1, newObjectType)

      expectMsgPF() {
        case Success(ObjectTypeEntity(_, name, color, icon, _, _)) =>
          assert(name == newObjectType.name)
          assert(color == newObjectType.color)
          assert(icon == newObjectType.icon)
      }
    }

    "delete an existing object type" in {
      objectTypeActor ! AddObjectType(generateObjectType)
      receiveOne(1 second)

      objectTypeActor ! DeleteObjectType(1)
      expectMsg(Success)
    }

    "delete a non existing object type" in {
      objectTypeActor ! DeleteObjectType(1)
      expectMsg(Error(s"No object type found with id 1"))
    }

    "add multiple object types" in {
      val batchList = generateObjectTypeList
      objectTypeActor ! BatchAddObjectType(batchList)

      expectMsgPF() {
        case Success(entities: List[ObjectTypeEntity]) =>
          assert(entities.forall(o => batchList.exists(e => e.color == o.color && e.icon == o.icon && e.name == o.name)))
          assert(entities.length == batchList.length)
      }
    }

    "return all added object types" in {
      val batchList = generateObjectTypeList
      objectTypeActor ! BatchAddObjectType(batchList)
      receiveOne(1 second)

      objectTypeActor ! GetObjectTypes

      val objectTypes = expectMsgType[List[ObjectTypeEntity]]
      assert(objectTypes.length == batchList.length)
    }

    "recover data when persistence fails" in {
      val objectType = generateObjectType
      objectTypeActor ! AddObjectType(objectType)
      receiveN(1)

      persistenceTestKit.failNextPersisted()
      val objectType2 = generateObjectType
      objectTypeActor ! AddObjectType(objectType2)

      Thread.sleep(200)

      objectTypeActor = system.actorOf(Props(new ObjectType(username, landId)))

      objectTypeActor ! GetObjectTypes

      val allEntities = expectMsgType[List[ObjectTypeEntity]]
      assert(allEntities.length == 1)
    }
  }

}
