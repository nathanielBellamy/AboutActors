package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object AASupervisor {
  def apply(): Behavior[Nothing] = Behaviors.setup {
    context =>
      println("starting AASupervisor")

      val aaWebsocketController = context.spawn(AAWebsocketController(), "aa_websocket_controller")
      val aaRestController = context.spawn(AARestController(), "aa_rest_controller")
      new AASupervisor(context)
  }
}

class AASupervisor(context: ActorContext[Nothing]) extends AbstractBehavior[Nothing](context) {

  override def onMessage(msg: Nothing): Behavior[Nothing] = {
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Nothing]] = {
    case PostStop =>
      println("aa-application stopped")
      this
  }
}
