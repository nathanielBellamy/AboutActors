package dev.nateschieber.aboutactors.dto

case class AvailableItemsDto(itemIds: List[String])

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait AvailableItemsJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val availableItemsFormat: RootJsonFormat[AvailableItemsDto] = jsonFormat1(AvailableItemsDto.apply)
}
