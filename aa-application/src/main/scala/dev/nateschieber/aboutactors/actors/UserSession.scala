package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object UserSession {
  def apply(uuid: String): Behavior[Nothing] = Behaviors.setup {
    context =>
      println(s"starting UserSession with sessionId: $uuid")

      new UserSession(context, uuid)
  }
}

class UserSession(context: ActorContext[Nothing], uuid: String) extends AbstractBehavior[Nothing](context) {

  private val sessionId: String = uuid

  override def onMessage(msg: Nothing): Behavior[Nothing] = {
    Behaviors.unhandled
  }
}
