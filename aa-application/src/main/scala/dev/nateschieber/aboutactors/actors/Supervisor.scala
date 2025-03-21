package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{AbtActMessage, FindRefs}

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
        .supervise(
          WebsocketController()
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val websocketController = context.spawn(supervisedWebsocketController, "websocket_controller")

      val supervisedRestController = Behaviors
        .supervise(
          RestController()
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val restController = context.spawn(supervisedRestController, "rest_controller")

      restController ! FindRefs()
      inventoryManager ! FindRefs()
      websocketController ! FindRefs()
      userSessionManager ! FindRefs()

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
