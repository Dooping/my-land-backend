package root

import actors.UserManagement
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import routes.{LandRoute, UserManagementRoute}
import utils.JwtHelper.jwtAuthenticator
import utils.RejectionHandlers

object MyLand extends App {

  implicit val system: ActorSystem = ActorSystem("MyLand")
  implicit val materializer: Materializer = Materializer(system)

  val userManagerActor = system.actorOf(Props[UserManagement], "userManager")

  def combinedRoute(userManagerActor: ActorRef) = {
    pathPrefix("api") {
      UserManagementRoute.route(userManagerActor) ~
      handleRejections(RejectionHandlers.authorizationFailedHandler) {
        authenticateOAuth2("MyLand", jwtAuthenticator) { username =>
          LandRoute.route(userManagerActor, username)
        }
      }
    }
  }

  Http().newServerAt("localhost", 8080).bind(combinedRoute(userManagerActor))

}
