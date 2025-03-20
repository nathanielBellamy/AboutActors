package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors
import dev.nateschieber.aboutactors.dto.UserSessionDto
import dev.nateschieber.aboutactors.{AbtActMessage, AddItemToCart, CartEmptied, HydrateUserSession, ItemAddedToCart, ItemNotAddedToCart, ItemNotRemovedFromCart, ItemRemovedFromCart, RemoveItemFromCart, RequestToAddItemToCart, RequestToEmptyCart, RequestToRemoveItemFromCart, TerminateSession, TerminateSessionSuccess, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

import scala.collection.mutable.ListBuffer

object UserSession {
  def apply(uuid: String, websocketController: ActorRef[AbtActMessage], userSessionManager: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      println(s"starting UserSession with sessionId: $uuid")

      new UserSession(context, uuid, websocketController, userSessionManager)
  }
}

class UserSession(
                   context: ActorContext[AbtActMessage],
                   uuid: String,
                   websocketControllerIn: ActorRef[AbtActMessage],
                   userSessionManagerIn: ActorRef[AbtActMessage]
                 ) extends AbstractBehavior[AbtActMessage](context) {
  private val sessionId: String = uuid
  private var itemIds: ListBuffer[String] = ListBuffer()
  private val websocketController: ActorRef[AbtActMessage] = websocketControllerIn
  private val userSessionManager: ActorRef[AbtActMessage] = userSessionManagerIn

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case AddItemToCart(itemId, inventoryManager) =>
        inventoryManager ! RequestToAddItemToCart(itemId, sessionId, context.self)
        Behaviors.same

      case ItemAddedToCart(itemId, inventoryManager) =>
        itemIds.addOne(itemId)
        val dto = UserSessionDto(sessionId, itemIds.toList)
        websocketController ! HydrateUserSession(dto)
        Behaviors.same

      case ItemNotAddedToCart(itemId, inventoryManager) =>
        websocketController ! UserAddedItemToCartFailure(itemId, sessionId, context.self)
        Behaviors.same

      case RemoveItemFromCart(itemId, inventoryManager) =>
        inventoryManager ! RequestToRemoveItemFromCart(itemId, sessionId, context.self)
        Behaviors.same

      case ItemRemovedFromCart(itemId, inventoryManager) =>
        itemIds = itemIds.filter(id => id != itemId)
        val dto = UserSessionDto(sessionId, itemIds.toList)
        websocketController ! HydrateUserSession(dto)
        Behaviors.same

      case ItemNotRemovedFromCart(itemId, inventoryManager) =>
        Behaviors.same

      case TerminateSession(inventoryManager) =>
        inventoryManager ! RequestToEmptyCart(sessionId, context.self)
        Behaviors.same

      case CartEmptied(replyTo) =>
        userSessionManager ! TerminateSessionSuccess(sessionId)
        Behaviors.stopped

      case default =>
        Behaviors.same
    }
  }
}
