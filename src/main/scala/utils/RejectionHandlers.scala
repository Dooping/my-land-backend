package utils

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{AuthenticationFailedRejection, RejectionHandler}

object RejectionHandlers {

  def authorizationFailedHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle {
    case AuthenticationFailedRejection(CredentialsRejected, _) =>
      complete(StatusCodes.Unauthorized, "invalid token")
    case AuthenticationFailedRejection(CredentialsMissing, _) =>
      complete(StatusCodes.Unauthorized, "missing token")
  }.result()

}
