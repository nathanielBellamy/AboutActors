package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{AbtActMessage, InitUserSession, InitUserSessionFailure, InitUserSessionSuccess, UserAddedItemToCart, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

object InventoryManager {
  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      println("Starting InventoryManager")

      new InventoryManager(context)
  }
}

class InventoryManager(context: ActorContext[AbtActMessage]) extends AbstractBehavior[AbtActMessage](context) {

  private val items = scala.collection.mutable.Map[String, Option[String]]() // key: itemId, value: owned by userSessionId

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case UserAddedItemToCart(itemId, userSessionUuid, replyTo) =>
        if (items.get(itemId).isEmpty) {
          items.update(itemId, Some(userSessionUuid))
          replyTo ! UserAddedItemToCartSuccess(itemId, userSessionUuid, context.self)
        } else {
          replyTo ! UserAddedItemToCartFailure(itemId, userSessionUuid, context.self)
        }
        Behaviors.same

      case default =>
        println("InventoryManager::UnMatchedMethod")
        Behaviors.same
    }
  }
}
