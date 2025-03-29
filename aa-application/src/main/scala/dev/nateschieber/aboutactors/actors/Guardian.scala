package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{AbtActMessage, FindRefs}

object Guardian {

  def apply(baseHttpPort: Int): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      println("starting Guardian")

      val supervisedInventoryManager = Behaviors
        .supervise(
          InventoryManager(context.self)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val inventoryManager = context.spawn(supervisedInventoryManager, "inventory_manager")

      val supervisedUserSessionManager = Behaviors
        .supervise(
          UserSessionSupervisor(context.self)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val userSessionManager = context.spawn(supervisedUserSessionManager, "user_session_manager")

      val supervisedWebsocketController = Behaviors
        .supervise(
          WebsocketController(context.self, baseHttpPort + 100)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val websocketController = context.spawn(supervisedWebsocketController, "websocket_controller")

      val supervisedRestController = Behaviors
        .supervise(
          RestController(context.self, baseHttpPort + 200)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val restController = context.spawn(supervisedRestController, "rest_controller")

      new Guardian(context, baseHttpPort)
  }
}

class Guardian(context: ActorContext[AbtActMessage], baseHttpPort: Int) extends AbstractBehavior[AbtActMessage](context) {

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[AbtActMessage]] = {
    case PostStop =>
      println("aa-application stopped")
      this
  }
}
