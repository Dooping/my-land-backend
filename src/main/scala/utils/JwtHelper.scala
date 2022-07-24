package utils

import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object JwtHelper {

  private val algorithm = JwtAlgorithm.HS256
  private val secretKey = "temporarysecret" //TODO: get it from somewhere safe

  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("temporary.com"), //TODO: change to company domain
      content = username
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string
  }

  def isTokenExpired(token: String, username: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) =>
      (claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000) && (claims.content == username)
    case Failure(_) => true
  }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

}
