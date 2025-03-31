package dev.nateschieber.aboutactors.dto

case class TriggerErrorDto(actorToError: String, sessionId: String)

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait TriggerErrorJsonSupport extends SprayJsonSupport with DefaultJsonProtocol   {
  implicit val triggerErrorDtoFormat: RootJsonFormat[TriggerErrorDto] = jsonFormat2(TriggerErrorDto.apply)
}
