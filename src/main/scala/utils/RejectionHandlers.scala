package utils

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{AuthenticationFailedRejection, AuthorizationFailedRejection, MalformedRequestContentRejection, MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}

object RejectionHandlers {

  def authorizationFailedHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case AuthenticationFailedRejection(CredentialsRejected, _) =>
        complete(StatusCodes.Unauthorized, "invalid token")
      case AuthenticationFailedRejection(CredentialsMissing, _) =>
        complete(StatusCodes.Unauthorized, "missing token")
      case AuthorizationFailedRejection =>
        complete(StatusCodes.Forbidden)
      case MalformedRequestContentRejection(message, _) =>
        complete(StatusCodes.BadRequest, message)
      case MissingQueryParamRejection(parameterName) =>
        complete(StatusCodes.BadRequest, s"missing parameter: $parameterName")
    }
//    .handleAll {
//      case list: Seq[Rejection] if list.contains(AuthorizationFailedRejection) =>
//        complete(StatusCodes.Forbidden)
//    }
    .result()

}
