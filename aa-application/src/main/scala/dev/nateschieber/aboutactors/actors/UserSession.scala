package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.AbtActMessage

object UserSession {
  def apply(uuid: String): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      println(s"starting UserSession with sessionId: $uuid")

      val self = new UserSession(context, uuid)

      self
  }
}

class UserSession(context: ActorContext[AbtActMessage], uuid: String) extends AbstractBehavior[AbtActMessage](context) {
  private val sessionId: String = uuid
  
  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    Behaviors.unhandled
  }
}
