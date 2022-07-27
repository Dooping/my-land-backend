package routes

import actors.UserManagement.{GetPassword, Register}
import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges, RawHeader}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Route}
import akka.pattern.StatusReply._
import akka.pattern.ask
import akka.util.Timeout
import org.mindrot.jbcrypt.BCrypt
import protocols.UserManagementJsonProtocol
import utils.JwtHelper.createToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps


object UserManagementRoute extends UserManagementJsonProtocol with SprayJsonSupport{
  case class UserCredentials(username: String, password: String)

  def authenticateBasicAsync[T](realm: String,
                                authenticate: (String, String) => Future[Option[T]]): Directive1[T] = {
    def challenge = HttpChallenges.basic(realm)
    extractCredentials.flatMap {
      case Some(BasicHttpCredentials(username, password)) =>
        onSuccess(authenticate(username, password)).flatMap {
          case Some(client) => provide(client)
          case None => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
        }
      case _ => reject(AuthenticationFailedRejection(CredentialsMissing, challenge))
    }
  }

  implicit val timeout: Timeout = Timeout(5 seconds)

  def myAuthenticator(username: String, password: String)(authenticator: ActorRef): Future[Option[String]] = {
    (authenticator ? GetPassword(username)).map {
      case Error(_) => None
      case Success(storedPassword: String) =>
        if (BCrypt.checkpw(password, storedPassword)) Some(username) else None
    }
  }

  def route(authenticator: ActorRef): Route = {
    pathPrefix("api" / "user") {
      get {
          authenticateBasicAsync("MyLand", myAuthenticator(_, _)(authenticator)) { username =>
            val token = createToken(username, 30)
            respondWithHeader(RawHeader("Access-Token", token)) {
              complete(StatusCodes.OK)
            }
          }
      } ~
      (post & entity(as[UserCredentials])) { user =>
            val registrationFuture = (authenticator ? Register(user.username, user.password)).map {
              case Error(reason) =>
                HttpResponse(StatusCodes.Conflict, entity = HttpEntity(reason.getMessage))
              case Success =>
                HttpResponse(StatusCodes.Created)
            }
            complete(registrationFuture)
      }

    }
  }
}
