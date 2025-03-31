package dev.nateschieber.aboutactors.dto

case class UserSessionIdDto(sessionId: String)

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait UserSessionIdJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val userSessionIdFormat: RootJsonFormat[UserSessionIdDto] = jsonFormat1(UserSessionIdDto.apply)
}
