package dev.nateschieber.aboutactors

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import dev.nateschieber.aboutactors.dto.{AvailableItemsDto, UserSessionDto}

sealed trait AbtActMessage extends CborSerializable

final case class FindRefs() extends AbtActMessage
final case class ListingResponse(listing: Receptionist.Listing) extends AbtActMessage

final case class ProvideWebsocketControllerRef(websocketControllerRef: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ProvideInventoryManagerRef(inventoryManagerRef: ActorRef[AbtActMessage]) extends AbtActMessage

final case class WsInitUserSession(uuid: String, msg: String) extends AbtActMessage
final case class InitUserSession(uuid: String, msg: String, replyTo: ActorRef[InitUserSessionSuccess | InitUserSessionFailure]) extends AbtActMessage
final case class InitUserSessionSuccess(uuid: String, session: ActorRef[AbtActMessage]) extends AbtActMessage
final case class InitUserSessionFailure(uuid: String) extends AbtActMessage

final case class HydrateUserSession(dto: UserSessionDto) extends AbtActMessage
final case class HydrateAvailableItemsRequest(uuid: Option[String]) extends AbtActMessage
final case class HydrateAvailableItems(uuid: Option[String], dto: AvailableItemsDto) extends AbtActMessage

final case class RequestToAddItemToCart(itemId: String, sessionId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class RequestToRemoveItemFromCart(itemId: String, sessionId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class RequestRefreshSessionItems(sessionId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class RefreshItemsFromInventory(inventoryManager: ActorRef[AbtActMessage]) extends AbtActMessage
final case class RefreshedSessionItems(itemIds: List[String], replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class AddItemToCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ItemAddedToCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ItemNotAddedToCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class UserAddedItemToCart(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCartSuccess(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserAddedItemToCartFailure(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class RemoveItemFromCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ItemRemovedFromCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class ItemNotRemovedFromCart(itemId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class RequestToEmptyCart(userSessionUuId: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class CartEmptied(replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class UserRemovedItemFromCart(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserRemovedItemFromCartSuccess(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage
final case class UserRemovedItemFromCartFailure(itemId: String, userSessionUuid: String, replyTo: ActorRef[AbtActMessage]) extends AbtActMessage

final case class InventoryItemAddedToCart(itemId: String, sessionId: String) extends AbtActMessage
final case class InventoryItemNotAddedToCart(itemId: String, sessionId: String, msg: String) extends AbtActMessage
final case class InventoryItemRemovedFromCart(itemId: String, sessionId: String) extends AbtActMessage
final case class InventoryAvailableItems(itemIds: List[String]) extends AbtActMessage

final case class TerminateUserSession(userSessionUuid: String, inventoryManager: ActorRef[AbtActMessage]) extends AbtActMessage
final case class TerminateSession(inventoryManager: ActorRef[AbtActMessage]) extends AbtActMessage
final case class TerminateSessionSuccess(userSessionUuid: String) extends AbtActMessage

final case class TriggerError(optSessionId: Option[String]) extends AbtActMessage