package routes

import actors.UserManagement.LandCommand
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protocols.LandJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class AuthorizationRouteSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with LandJsonProtocol {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import root.MyLand.combinedRoute
  import actors.Land._
  import utils.JwtHelper._

  val testUsername = "david gago"
  private val testToken = createToken(testUsername, 1)

  "jwtHelper" should {
    "create and validate a token" in {
      val generatedToken = createToken(testUsername, 1)
      assert(isTokenValid(generatedToken))
      assert(!isTokenExpired(generatedToken))
    }

    "detect an expired token" in {
      val generatedToken = createToken(testUsername, -1)
      assert(isTokenValid(generatedToken))
      assert(isTokenExpired(generatedToken))
    }

    "extract correctly the username from a token" in {
      val token = createToken(testUsername, 1)
      val extractedUsername = extractUsername(token)
      assert(extractedUsername == testUsername)
    }
  }

  "Any route" should {
    "have a token" in {
      val userManager = TestProbe("userManagement")
      Get("/api/land") ~> combinedRoute(userManager.ref) ~> check {
        status shouldBe StatusCodes.Unauthorized
        val strictEntityFuture = responseEntity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        strictEntity.data.utf8String shouldBe "missing token"
      }
    }

    "not accept invalid tokens" in {
      val userManager = TestProbe("userManagement")
      Get("/api/land") ~> addHeader(Authorization(OAuth2BearerToken("invalidToken"))) ~> combinedRoute(userManager.ref) ~> check {
        status shouldBe StatusCodes.Unauthorized
        val strictEntityFuture = responseEntity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        strictEntity.data.utf8String shouldBe "invalid token"
      }
    }

    "accept a valid token in header" in {
      val userManager = TestProbe("userManagement")
      Get("/api/land") ~> addHeader(Authorization(OAuth2BearerToken(testToken))) ~> combinedRoute(userManager.ref) ~> check {
        userManager.receiveWhile() {
          case LandCommand(_, GetAllLands) => userManager.reply(List[LandEntity]())
        }
        val result = entityAs[List[LandEntity]]
        status shouldBe StatusCodes.OK
        result.length shouldBe 0
      }
    }

    "accept a valid token as parameter" in {
      val userManager = TestProbe("userManagement")
      Get(s"/api/land?access_token=$testToken") ~> combinedRoute(userManager.ref) ~> check {
        userManager.receiveWhile() {
          case LandCommand(_, GetAllLands) => userManager.reply(List[LandEntity]())
        }
        status shouldBe StatusCodes.OK
      }
    }
  }

}
