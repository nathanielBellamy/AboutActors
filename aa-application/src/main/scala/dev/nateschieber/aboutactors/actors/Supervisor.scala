package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{ProvideSelfRef, ProvideWebsocketControllerRef}

object Supervisor {
  def apply(): Behavior[Nothing] = Behaviors.setup {
    context =>
      println("starting Supervisor")

      val inventoryManager = context.spawn(InventoryManager(), "inventory_manager")
      val userSessionManager = context.spawn(UserSessionManager(), "user_session_manager")
      val websocketController = context.spawn(WebsocketController(userSessionManager), "websocket_controller")
      inventoryManager ! ProvideWebsocketControllerRef(websocketController)
      userSessionManager ! ProvideWebsocketControllerRef(websocketController)
      websocketController ! ProvideSelfRef(websocketController)
      val restController = context.spawn(RestController(websocketController, userSessionManager, inventoryManager), "rest_controller")
      new Supervisor(context)
  }
}

class Supervisor(context: ActorContext[Nothing]) extends AbstractBehavior[Nothing](context) {

  override def onMessage(msg: Nothing): Behavior[Nothing] = {
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Nothing]] = {
    case PostStop =>
      println("aa-application stopped")
      this
  }
}
