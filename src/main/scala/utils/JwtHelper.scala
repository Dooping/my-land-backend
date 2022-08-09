package utils

import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive0, Directives}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtOptions, JwtSprayJson}
import protocols.JwtPayloadProtocol
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object JwtHelper extends JwtPayloadProtocol {

  private val algorithm = JwtAlgorithm.HS256
  private val secretKey = "temporarysecret" //TODO: get it from somewhere safe

  case class Payload(username: String, isAdmin: Boolean = false)

  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("temporary.com"), //TODO: change to company domain
      content = Payload(username).toJson.prettyPrint
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string
  }

  def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) =>
      claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
    case Failure(_) => true
  }

  def extractPayload(token: String): (String, Boolean) = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) =>
      val payload = claims.content.parseJson.convertTo[Payload]
      (payload.username, payload.isAdmin)
    case _ => throw new RuntimeException("jwt token invalid")
  }

  def isTokenValid(token: String): Boolean =
    JwtSprayJson.isValid(
      token,
      secretKey,
      Seq(algorithm),
      JwtOptions(expiration = false)
    )

  def jwtAuthenticator(credentials: Credentials): Option[(String, Boolean)] = credentials match {
    case Credentials.Provided(token) =>
      if (isTokenValid(token) && !isTokenExpired(token))
        Some(extractPayload(token))
      else None
    case Credentials.Missing => None
  }

  def admin(authPayload: (String, Boolean)): Directive0 = authPayload match {
    case (_, true)  => pass
    case (_, false) => Directives.reject(AuthorizationFailedRejection)
  }
}
