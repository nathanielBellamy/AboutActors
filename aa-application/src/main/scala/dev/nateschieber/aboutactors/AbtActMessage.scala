package dev.nateschieber.aboutactors

import akka.actor.typed.ActorRef
import dev.nateschieber.aboutactors.dto.{AvailableItemsDto, UserSessionDto}

sealed trait AbtActMessage

final case class ProvideSelfRef(selfRef: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ProvideWebsocketControllerRef(websocketControllerRef: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ProvideInventoryManagerRef(inventoryManagerRef: ActorRef[AbtActMessage]) extends AbtActMessage

final case class WsInitUserSession(uuid: String, msg: String) extends AbtActMessage
final case class InitUserSession(uuid: String, msg: String, replyTo: ActorRef[InitUserSessionSuccess | InitUserSessionFailure]) extends AbtActMessage
final case class InitUserSessionSuccess(uuid: String) extends AbtActMessage
final case class InitUserSessionFailure(uuid: String) extends AbtActMessage

final case class HydrateUserSession(dto: UserSessionDto) extends AbtActMessage
final case class HydrateAvailableItemsRequest(uuid: Option[String]) extends AbtActMessage
final case class HydrateAvailableItems(uuid: Option[String], dto: AvailableItemsDto) extends AbtActMessage

final case class RequestToAddItemToCart(itemId: String, sessionId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class AddItemToCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ItemAddedToCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ItemNotAddedToCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCart(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCartSuccess(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCartFailure(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
