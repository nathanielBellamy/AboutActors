package dev.nateschieber.aboutactors

import akka.actor.typed.ActorRef

sealed trait AbtActMessage

final case class ProvideSelfRef(selfRef: ActorRef[AbtActMessage]) extends AbtActMessage
final case class InitUserSession(uuid: String) extends AbtActMessage
final case class UserAddedDevice() extends AbtActMessage