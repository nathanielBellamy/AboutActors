package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.dto.AvailableItemsDto
import dev.nateschieber.aboutactors.{AbtActMessage, HydrateAvailableItems, HydrateAvailableItemsRequest, InitUserSession, InitUserSessionFailure, InitUserSessionSuccess, ItemAddedToCart, ItemNotAddedToCart, ProvideWebsocketControllerRef, RequestToAddItemToCart, UserAddedItemToCart, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

object InventoryManager {
  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      println("Starting InventoryManager")

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
  ) // key: itemId, value: owned by userSessionId

  private var websocketController: ActorRef[AbtActMessage] = null

  private def getAvailableItemsDto: AvailableItemsDto = {
    val availableItems = items.keys.filter(k => items.get(k).get.isEmpty).toList
    AvailableItemsDto(availableItems)
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case RequestToAddItemToCart(itemId, userSessionUuid, userSessionRef) =>
        items.get(itemId).get match {
          case Some(_) =>
            // Item already taken
            userSessionRef ! ItemNotAddedToCart(itemId, context.self)
          case None =>
            items.update(itemId, Some(userSessionUuid))
            userSessionRef ! ItemAddedToCart(itemId, context.self)
            websocketController ! HydrateAvailableItems( None, getAvailableItemsDto )
        }
        Behaviors.same

      case HydrateAvailableItemsRequest(optUuid) =>
        websocketController ! HydrateAvailableItems( optUuid, getAvailableItemsDto )
        Behaviors.same

      case ProvideWebsocketControllerRef(websocketControllerRef) =>
        websocketController = websocketControllerRef
        Behaviors.same

      case default =>
        println("InventoryManager::UnMatchedMethod")
        Behaviors.same
    }
  }
}
