package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import routes.UserManagementRoute
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait UserManagementJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import UserManagementRoute._

  implicit val userCredentialsFormat: RootJsonFormat[UserCredentials] = jsonFormat2(UserCredentials)
}
