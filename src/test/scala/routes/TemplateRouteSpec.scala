package routes

import actors.ObjectType.ObjType
import actors.ObjectTypeSpec.{generateObjectTypeList, generateObjectTypeListEntity}
import actors.TemplateSpec.{localeGen, nameGen}
import actors.UserManagement
import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
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

import java.util.Date
import scala.language.postfixOps

class TemplateRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with ObjectTypeJsonProtocol
  with TemplateJsonProtocol {

  import actors.Template
  import actors.Template._
  import actors.UserManagement.LandCommand

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

    "get results for default locale when no locale provided" in {
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

    "fail to do admin operation" in {
      Delete("/template/object?locale=en&name=Agriculture") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAuthPayload)} ~> check {
          status shouldBe StatusCodes.Forbidden
        }
      }
  }

  "Admin user" should {
    "register a new default template" in {
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

    "fail to register a default template if no entity provided" in {
      Post("/template/object") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "change a default template" in {
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

    "fail to change a default template if no entity provided" in {
      Put("/template/object") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "delete a default template" in {
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

    "fail to delete a default template if any parameter is missing" in {
      Delete("/template/object") ~> handleRejections(RejectionHandlers.authorizationFailedHandler){
        TemplateRoute.route(testProbe.ref, testAdminAuthPayload)} ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

}
