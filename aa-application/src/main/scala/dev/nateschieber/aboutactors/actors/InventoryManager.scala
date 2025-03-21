package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.dto.AvailableItemsDto
import dev.nateschieber.aboutactors.{AbtActMessage, CartEmptied, HydrateAvailableItems, HydrateAvailableItemsRequest, ItemAddedToCart, ItemNotAddedToCart, ItemNotRemovedFromCart, ItemRemovedFromCart, ProvideWebsocketControllerRef, RequestToAddItemToCart, RequestToEmptyCart, RequestToRemoveItemFromCart, TriggerError, UserAddedItemToCart, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

object InventoryManager {
  private val InventoryManagerServiceKey = ServiceKey[AbtActMessage]("inventory-manager")

  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system
      println("Starting InventoryManager")

      context.system.receptionist ! Receptionist.Register(InventoryManagerServiceKey, context.self)

      new InventoryManager(context)
  }
}

class InventoryManager(context: ActorContext[AbtActMessage]) extends AbstractBehavior[AbtActMessage](context) {

  private val items = scala.collection.mutable.Map[String, Option[String]](
    "001" -> None,
    "002" -> None,
    "003" -> None,
    "004" -> None,
    "005" -> None,
    "006" -> None,
    "007" -> None,
  ) // key: itemId, value: owned by userSessionId

  private var websocketController: ActorRef[AbtActMessage] = null

  private def getAvailableItemsDto: AvailableItemsDto = {
    val availableItems = items.keys.filter(k => items.get(k).get.isEmpty).toList
    AvailableItemsDto(availableItems)
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case RequestToAddItemToCart(itemId, userSessionUuid, userSessionRef) =>
        items(itemId) match {
          case Some(_) =>
            // Item already taken
            userSessionRef ! ItemNotAddedToCart(itemId, context.self)
          case None =>
            items.update(itemId, Some(userSessionUuid))
            userSessionRef ! ItemAddedToCart(itemId, context.self)
            websocketController ! HydrateAvailableItems( None, getAvailableItemsDto )
        }
        Behaviors.same

      case RequestToRemoveItemFromCart(itemId, userSessionUuid, userSessionRef) =>
        items(itemId) match {
          case Some(_) =>
            items.update(itemId, None)
            userSessionRef ! ItemRemovedFromCart(itemId, context.self)
            websocketController ! HydrateAvailableItems( None, getAvailableItemsDto )
          case None =>
            userSessionRef ! ItemNotRemovedFromCart(itemId, userSessionRef)
        }
        Behaviors.same

      case RequestToEmptyCart(sessionId, userSession) =>
        for ((itemId, optSessionId) <- items) {
          if (optSessionId.isDefined && optSessionId.get == sessionId) {
            items.update(itemId, None)
          }
        }
        websocketController ! HydrateAvailableItems( None , getAvailableItemsDto )
        userSession ! CartEmptied(context.self)
        Behaviors.same

      case HydrateAvailableItemsRequest(optUuid) =>
        websocketController ! HydrateAvailableItems( optUuid, getAvailableItemsDto )
        Behaviors.same

      case ProvideWebsocketControllerRef(websocketControllerRef) =>
        websocketController = websocketControllerRef
        Behaviors.same

      case TriggerError(_) =>
        println("Triggering error in Inventory Manager")
        throw Error("Error Triggered in Inventory Manager")

      case default =>
        println("InventoryManager::UnMatchedMethod")
        Behaviors.same
    }
  }
}
