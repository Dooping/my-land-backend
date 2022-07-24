import actors.UserManagement
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.Materializer
import routes.UserManagementRoute

object MyLand extends App {

  implicit val system: ActorSystem = ActorSystem("MyLand")
  implicit val materializer: Materializer = Materializer(system)

  val userManagerActor = system.actorOf(Props[UserManagement], "userManager")

  val routes = UserManagementRoute.route(userManagerActor)

  Http().newServerAt("localhost", 8080).bind(routes)

}
