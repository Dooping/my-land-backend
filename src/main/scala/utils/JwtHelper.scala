package utils

import akka.http.scaladsl.server.directives.Credentials
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtOptions, JwtSprayJson}
import protocols.JwtPayloadProtocol
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object JwtHelper extends JwtPayloadProtocol {

  private val algorithm = JwtAlgorithm.HS256
  private val secretKey = "temporarysecret" //TODO: get it from somewhere safe

  case class Payload(username: String)

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

  def extractUsername(token: String): String = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) => claims.content.parseJson.convertTo[Payload].username
    case _ => throw new RuntimeException("jwt token invalid")
  }

  def isTokenValid(token: String): Boolean =
    JwtSprayJson.isValid(
      token,
      secretKey,
      Seq(algorithm),
      JwtOptions(expiration = false)
    )

  def jwtAuthenticator(credentials: Credentials): Option[String] = credentials match {
    case Credentials.Provided(token) =>
      if (isTokenValid(token) && !isTokenExpired(token))
        Some(extractUsername(token))
      else None
    case Credentials.Missing => None
  }
}
