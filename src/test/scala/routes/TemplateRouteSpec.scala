package routes


import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply._
import akka.testkit.{TestActor, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protocols.{ObjectTypeJsonProtocol, TemplateJsonProtocol}
import utils.JwtHelper.Payload
import utils.RejectionHandlers

import scala.language.postfixOps

class TemplateRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with ObjectTypeJsonProtocol
  with TemplateJsonProtocol
  with SprayJsonSupport {

  import actors.Template._
  import actors.ObjectTypeSpec.generateObjectTypeList
  import actors.TaskTypeSpec.generateTaskTypeList
  import actors.TemplateSpec.{localeGen, nameGen}

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val testAdminAuthPayload = Payload("david gago", isAdmin = true)
  private val testAuthPayload = Payload("david")
  private val testProbe = TestProbe("user-probe")

  "Any user" should {
    "get all default and user object templates" in {
      val landTemplateDefault = generateObjectTypeList
      val landTemplateFromLands = generateObjectTypeList
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetObjectTypeOptions(username, locale) =>
          assert(username == testAdminAuthPayload.username)
          assert(locale == "pt")
          sender ! ObjectTypeOptionsResponse(Map("Agriculture" -> landTemplateDefault), Set(landTemplateFromLands))
          TestActor.KeepRunning
      })
      Get("/template/object?locale=pt") ~> TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ObjectTypeOptionsResponse]
        assert(response.default("Agriculture") == landTemplateDefault)
        assert(response.fromLands.contains(landTemplateFromLands))
      }
    }
    "get all default and user task templates" in {
      val landTemplateDefault = generateTaskTypeList
      val landTemplateFromLands = generateTaskTypeList
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetTaskTypeOptions(username, locale) =>
          assert(username == testAdminAuthPayload.username)
          assert(locale == "pt")
          sender ! TaskTypeOptionsResponse(Map("Agriculture" -> landTemplateDefault), Set(landTemplateFromLands))
          TestActor.KeepRunning
      })
      Get("/template/task?locale=pt") ~> TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[TaskTypeOptionsResponse]
        assert(response.default("Agriculture") == landTemplateDefault)
        assert(response.fromLands.contains(landTemplateFromLands))
      }
    }

    "get object type results for default locale when no locale provided" in {
      val landTemplateDefault = generateObjectTypeList
      val landTemplateFromLands = generateObjectTypeList
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetObjectTypeOptions(username, locale) =>
          assert(username == testAdminAuthPayload.username)
          assert(locale == "en")
          sender ! ObjectTypeOptionsResponse(Map("Agriculture" -> landTemplateDefault), Set(landTemplateFromLands))
          TestActor.KeepRunning
      })
      Get("/template/object") ~> TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[ObjectTypeOptionsResponse]
        assert(response.default("Agriculture") == landTemplateDefault)
        assert(response.fromLands.contains(landTemplateFromLands))
      }
    }

    "get task type results for default locale when no locale provided" in {
      val landTemplateDefault = generateTaskTypeList
      val landTemplateFromLands = generateTaskTypeList
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetTaskTypeOptions(username, locale) =>
          assert(username == testAdminAuthPayload.username)
          assert(locale == "en")
          sender ! TaskTypeOptionsResponse(Map("Agriculture" -> landTemplateDefault), Set(landTemplateFromLands))
          TestActor.KeepRunning
      })
      Get("/template/task") ~> TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[TaskTypeOptionsResponse]
        assert(response.default("Agriculture") == landTemplateDefault)
        assert(response.fromLands.contains(landTemplateFromLands))
      }
    }

    "fail to do admin operation" in {
      Delete("/template/object?locale=en&name=Agriculture") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAuthPayload)} ~> check {
          status shouldBe StatusCodes.Forbidden
        }
      }
  }

  "Admin user" should {
    "register a new default object type template" in {
      val defaultTemplate = generateObjectTypeList
      val templateName = nameGen.sample.get
      val templateLocale = localeGen.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case RegisterNewLandObjectTemplate(locale, name, objTypes) =>
          assert(objTypes == defaultTemplate)
          assert(locale == templateLocale)
          assert(name == templateName)
          sender ! Success()
          TestActor.KeepRunning
      })
      Post("/template/object", RegisterNewLandObjectTemplate(templateLocale, templateName, defaultTemplate)) ~>
          TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "register a new default task type template" in {
      val defaultTemplate = generateTaskTypeList
      val templateName = nameGen.sample.get
      val templateLocale = localeGen.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case RegisterNewTaskTemplate(locale, name, objTypes) =>
          assert(objTypes == defaultTemplate)
          assert(locale == templateLocale)
          assert(name == templateName)
          sender ! Success()
          TestActor.KeepRunning
      })
      Post("/template/task", RegisterNewTaskTemplate(templateLocale, templateName, defaultTemplate)) ~>
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to register a default object type template if no entity provided" in {
      Post("/template/object") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "fail to register a default task type template if no entity provided" in {
      Post("/template/task") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "change a default object type template" in {
      val defaultTemplate = generateObjectTypeList
      val templateName = nameGen.sample.get
      val templateLocale = localeGen.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ChangeLandObjectTemplate(locale, name, objTypes) =>
          assert(objTypes == defaultTemplate)
          assert(locale == templateLocale)
          assert(name == templateName)
          sender ! Success()
          TestActor.KeepRunning
      })
      Put("/template/object", ChangeLandObjectTemplate(templateLocale, templateName, defaultTemplate)) ~>
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "change a default task type template" in {
      val defaultTemplate = generateTaskTypeList
      val templateName = nameGen.sample.get
      val templateLocale = localeGen.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case ChangeTaskTemplate(locale, name, taskTypes) =>
          assert(taskTypes == defaultTemplate)
          assert(locale == templateLocale)
          assert(name == templateName)
          sender ! Success()
          TestActor.KeepRunning
      })
      Put("/template/task", ChangeTaskTemplate(templateLocale, templateName, defaultTemplate)) ~>
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to change a default object type template if no entity provided" in {
      Put("/template/object") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "fail to change a default task type template if no entity provided" in {
      Put("/template/task") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "delete a default object type template" in {
      val templateName = nameGen.sample.get
      val templateLocale = localeGen.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case DeleteLandObjectTemplate(locale, name) =>
          assert(locale == templateLocale)
          assert(name == templateName)
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete(s"/template/object?locale=$templateLocale&name=$templateName") ~> TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "delete a default task type template" in {
      val templateName = nameGen.sample.get
      val templateLocale = localeGen.sample.get
      testProbe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case DeleteTaskTemplate(locale, name) =>
          assert(locale == templateLocale)
          assert(name == templateName)
          sender ! Success()
          TestActor.KeepRunning
      })
      Delete(s"/template/task?locale=$templateLocale&name=$templateName") ~> TemplateRoute.route(testProbe.ref, testAdminAuthPayload) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "fail to delete a default object type template if any parameter is missing" in {
      Delete("/template/object") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "fail to delete a default task type template if any parameter is missing" in {
      Delete("/template/task") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

}
