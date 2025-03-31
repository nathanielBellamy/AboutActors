package dev.nateschieber.aboutactors.dto

case class UserSessionDto(sessionId: String, itemIds: List[String])

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait UserSessionJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val userSessionFormat: RootJsonFormat[UserSessionDto] = jsonFormat2(UserSessionDto.apply)
}
