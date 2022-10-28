package routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply.{Error, Success}
import akka.testkit.{TestActor, TestKit, TestProbe}
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class UserManagementRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest {

  import routes.UserManagementRoute._
  import actors.UserManagement._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A user management route" should {
    "register a new user" in {
      val userManagement = TestProbe("userManagement")
      val user = UserCredentials("testUsername", "testPassword")
      userManagement.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case Register(user.username, user.password) =>
          sender ! Success()
          TestActor.KeepRunning
      })
      Post("/user", user) ~> UserManagementRoute.route(userManagement.ref) ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    "not allow duplicate users" in {
      val userManagement = TestProbe("userManagement")
      val user = UserCredentials("duplicateUsername", "testPassword")
      userManagement.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case Register(user.username, user.password) =>
          sender ! Error(s"User ${user.username} does not exist")
          TestActor.KeepRunning
      })
      Post("/user", user) ~> UserManagementRoute.route(userManagement.ref) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return jwt token when credentials are correct" in {
      val userManagement = TestProbe("userManagement")
      userManagement.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case GetPassword(_) =>
          sender ! Success("$2a$12$yXfCWErAGl2NLIVPE03Uj.onmZhW8HX7UaIGHg4OaSnQ.bGhtA9t2")
          TestActor.KeepRunning
      })
      Get("/user") ~> addHeader(Authorization(BasicHttpCredentials("david", "p4ssw0rd"))) ~> UserManagementRoute.route(userManagement.ref) ~> check {

        val token = header("Access-Token").get.value()

        token shouldBe a[String]
        token should fullyMatch regex "(^[A-Za-z0-9-_]*\\.[A-Za-z0-9-_]*\\.[A-Za-z0-9-_]*$)"
        status shouldBe StatusCodes.OK
      }
    }
  }

  "A deletion route" should {

    "delete a user when it exists" in {
      val userManagement = TestProbe("userManagement")
      userManagement.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
        case DeleteUser(_) =>
          sender ! Success()
          TestActor.KeepRunning
      })

      Delete("/user") ~> UserManagementRoute.deletionRoute(userManagement.ref, "username") ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

}
