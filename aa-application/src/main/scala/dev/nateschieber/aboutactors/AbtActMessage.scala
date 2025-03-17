package dev.nateschieber.aboutactors

import akka.actor.typed.ActorRef

sealed trait AbtActMessage

final case class ProvideSelfRef(selfRef: ActorRef[AbtActMessage]) extends AbtActMessage
final case class WsInitUserSession(uuid: String, msg: String) extends AbtActMessage
final case class InitUserSession(uuid: String, msg: String, replyTo: ActorRef[InitUserSessionSuccess | InitUserSessionFailure]) extends AbtActMessage
final case class InitUserSessionSuccess(uuid: String) extends AbtActMessage
final case class InitUserSessionFailure(uuid: String) extends AbtActMessage
final case class UserAddedDevice() extends AbtActMessage