package dev.nateschieber.aboutactors

import akka.actor.typed.ActorRef

sealed trait AbtActMessage

final case class ProvideSelfRef(selfRef: ActorRef[AbtActMessage]) extends AbtActMessage
final case class WsInitUserSession(uuid: String, msg: String) extends AbtActMessage
final case class InitUserSession(uuid: String, msg: String, replyTo: ActorRef[InitUserSessionSuccess | InitUserSessionFailure]) extends AbtActMessage
final case class InitUserSessionSuccess(uuid: String) extends AbtActMessage
final case class InitUserSessionFailure(uuid: String) extends AbtActMessage
final case class UserAddedItemToCart(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCartSuccess(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCartFailure(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
