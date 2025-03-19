package dev.nateschieber.aboutactors.dto

case class UserSessionDto(sessionId: String, itemIds: List[String])

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait UserSessionJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val addItemToCartFormat: RootJsonFormat[UserSessionDto] = jsonFormat2(UserSessionDto.apply)
}
