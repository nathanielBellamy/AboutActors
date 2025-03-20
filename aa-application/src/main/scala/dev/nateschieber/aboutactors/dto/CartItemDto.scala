package dev.nateschieber.aboutactors.dto

case class CartItemDto(sessionId: String, itemId: String)

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait CartItemJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val cartItemFormat: RootJsonFormat[CartItemDto] = jsonFormat2(CartItemDto.apply)
}