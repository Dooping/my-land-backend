package protocols

import routes.UserManagementRoute
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait UserManagementJsonProtocol extends DefaultJsonProtocol {
  import UserManagementRoute._

  implicit val userCredentialsFormat: RootJsonFormat[UserCredentials] = jsonFormat2(UserCredentials)
}
