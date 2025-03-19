package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.dto.UserSessionDto
import dev.nateschieber.aboutactors.{AbtActMessage, AddItemToCart, HydrateUserSession, ItemAddedToCart, ItemNotAddedToCart, RequestToAddItemToCart, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

import scala.collection.mutable.ListBuffer

object UserSession {
  def apply(uuid: String, websocketController: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      println(s"starting UserSession with sessionId: $uuid")

      val self = new UserSession(context, uuid, websocketController)

      self
  }
}

class UserSession(context: ActorContext[AbtActMessage], uuid: String, websocketControllerIn: ActorRef[AbtActMessage]) extends AbstractBehavior[AbtActMessage](context) {
  private val sessionId: String = uuid
  private val itemIds: ListBuffer[String] = ListBuffer()
  private val websocketController: ActorRef[AbtActMessage] = websocketControllerIn
  
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

      case default =>
        Behaviors.same
    }
  }
}
