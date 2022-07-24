package routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.StatusReply.{Error, Success}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
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
      Post("/api/user/register", user) ~> UserManagementRoute.route(userManagement.ref) ~> check {
        userManagement.receiveWhile() {
          case Register(user.username, user.password) => userManagement.reply(Success)
        }

        status shouldBe StatusCodes.Created
      }
    }

    "not allow duplicate users" in {
      val userManagement = TestProbe("userManagement")
      val user = UserCredentials("duplicateUsername", "testPassword")
      Post("/api/user/register", user) ~> UserManagementRoute.route(userManagement.ref) ~> check {
        userManagement.receiveWhile() {
          case Register(user.username, user.password) => userManagement.reply(Error(s"User ${user.username} does not exist"))
        }

        status shouldBe StatusCodes.Conflict
      }
    }

    "return jwt token when credentials are correct" in {
      val userManagement = TestProbe("userManagement")
      Post("/api/user/login") ~> addHeader(Authorization(BasicHttpCredentials("david", "p4ssw0rd"))) ~> UserManagementRoute.route(userManagement.ref) ~> check {

        userManagement.receiveWhile() {
          case GetPassword(username) =>
            userManagement.reply(Success("$2a$12$yXfCWErAGl2NLIVPE03Uj.onmZhW8HX7UaIGHg4OaSnQ.bGhtA9t2"))
        }

        val token = header("Access-Token").get.value()

        token shouldBe a[String]
        token should fullyMatch regex "(^[A-Za-z0-9-_]*\\.[A-Za-z0-9-_]*\\.[A-Za-z0-9-_]*$)"
        status shouldBe StatusCodes.OK
      }
    }
  }

}
