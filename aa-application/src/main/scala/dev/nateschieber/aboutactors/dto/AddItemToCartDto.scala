package dev.nateschieber.aboutactors.dto

case class AddItemToCartDto(sessionId: String, itemId: String)

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait AddItemToCartJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val addItemToCartFormat: RootJsonFormat[AddItemToCartDto] = jsonFormat2(AddItemToCartDto.apply)
}