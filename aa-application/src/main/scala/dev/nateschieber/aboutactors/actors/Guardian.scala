package dev.nateschieber.aboutactors.actors

import akka.actor.typed.pubsub.PubSub
import akka.actor.typed.{Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{AbtActMessage, FindRefs, HydrateAvailableItems}

object Guardian {

  def apply(id: Int, guardianIds: List[Int], baseHttpPort: Int): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      println(s"Starting Guardian $id")

      val supervisedInventoryManager = Behaviors
        .supervise(
          InventoryManager(id, context.self)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val inventoryManager = context.spawn(supervisedInventoryManager, s"inventory_manager_$id")

      val supervisedUserSessionManager = Behaviors
        .supervise(
          UserSessionSupervisor(id, context.self)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val userSessionManager = context.spawn(supervisedUserSessionManager, s"user_session_manager_$id")

      val supervisedWebsocketController = Behaviors
        .supervise(
          WebsocketController(id, guardianIds, context.self, baseHttpPort + 100)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val websocketController = context.spawn(supervisedWebsocketController, s"websocket_controller_$id")

      val supervisedRestController = Behaviors
        .supervise(
          RestController(id, context.self, baseHttpPort + 200)
        )
        .onFailure[Throwable](SupervisorStrategy.restart)
      val restController = context.spawn(supervisedRestController, s"rest_controller_$id")

      new Guardian(context, id, baseHttpPort)
  }
}

class Guardian(context: ActorContext[AbtActMessage], idIn: Int, baseHttpPort: Int) extends AbstractBehavior[AbtActMessage](context) {

  private val id: Int = idIn

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[AbtActMessage]] = {
    case PostStop =>
      println("aa-application stopped")
      this
  }
}
