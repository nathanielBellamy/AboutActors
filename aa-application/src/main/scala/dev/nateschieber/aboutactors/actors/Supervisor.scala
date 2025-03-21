package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{AbtActMessage, ProvideInventoryManagerRef, ProvideSelfRef, ProvideWebsocketControllerRef}

object Supervisor {

  def apply(): Behavior[Nothing] = Behaviors.setup {
    context =>
      println("starting Supervisor")

      val supervisedInventoryManager = Behaviors
        .supervise(
          InventoryManager()
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val inventoryManager = context.spawn(supervisedInventoryManager, "inventory_manager")

      val supervisedUserSessionManager = Behaviors
        .supervise(
          UserSessionManager()
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val userSessionManager = context.spawn(supervisedUserSessionManager, "user_session_manager")

      val supervisedWebsocketController = Behaviors
        .supervise(WebsocketController(userSessionManager))
        .onFailure[Throwable](SupervisorStrategy.restart)
      val websocketController = context.spawn(supervisedWebsocketController, "websocket_controller")

      inventoryManager ! ProvideWebsocketControllerRef(websocketController)
      userSessionManager ! ProvideWebsocketControllerRef(websocketController)
      websocketController ! ProvideSelfRef(websocketController)
      websocketController ! ProvideInventoryManagerRef(inventoryManager)

      val supervisedRestController = Behaviors
        .supervise(RestController(websocketController, userSessionManager, inventoryManager))
        .onFailure[Throwable](SupervisorStrategy.restart)
      val restController = context.spawn(supervisedRestController, "rest_controller")
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
