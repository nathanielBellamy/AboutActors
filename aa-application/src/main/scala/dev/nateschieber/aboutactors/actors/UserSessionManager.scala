package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.{AbtActMessage, AddItemToCart, InitUserSession, InitUserSessionFailure, InitUserSessionSuccess, ItemAddedToCart, ItemNotAddedToCart, ProvideWebsocketControllerRef, UserAddedItemToCart, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

object UserSessionManager {
  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      println("Starting UserSessionManager")

      new UserSessionManager(context)
  }
}

class UserSessionManager(context: ActorContext[AbtActMessage]) extends AbstractBehavior[AbtActMessage](context) {

  private val userSessions = scala.collection.mutable.Map[String, ActorRef[AbtActMessage]]()
  private var websocketController: ActorRef[AbtActMessage] = null

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case ProvideWebsocketControllerRef(wscRef) =>
        websocketController = wscRef
        Behaviors.same

      case InitUserSession(uuid, msg, replyTo) =>
        if (msg == "fail") {
          replyTo ! InitUserSessionFailure(uuid)
          return Behaviors.same
        }
        val session = context.spawn(UserSession(uuid, websocketController), s"user_session_$uuid")
        if (session == null) {
          replyTo ! InitUserSessionFailure(uuid)
        } else {
          userSessions.put(uuid, session)
          replyTo ! InitUserSessionSuccess(uuid)
        }
        Behaviors.same

      case UserAddedItemToCart(itemId, sessionId, inventoryManager) =>
        userSessions.get(sessionId).get ! AddItemToCart(itemId, inventoryManager)
        Behaviors.same

      case default =>
        println("UserSessionManager::UnMatchedMethod")
        Behaviors.same
    }
  }
}
