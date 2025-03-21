package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors.actors.WebsocketController.WebsocketControllerServiceKey
import dev.nateschieber.aboutactors.{
  AbtActMessage,
  AddItemToCart,
  FindRefs,
  InitUserSession,
  InitUserSessionFailure,
  InitUserSessionSuccess,
  ListingResponse,
  ProvideWebsocketControllerRef,
  RemoveItemFromCart,
  TerminateSession,
  TerminateSessionSuccess,
  TerminateUserSession,
  UserAddedItemToCart,
  UserRemovedItemFromCart
}

object UserSessionManager {
  val UserSessionManagerServiceKey = ServiceKey[AbtActMessage]("user-session-manager")

  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system
      println("Starting UserSessionManager")

      context.system.receptionist ! Receptionist.Register(UserSessionManagerServiceKey, context.self)

      new UserSessionManager(context)
  }
}

class UserSessionManager(context: ActorContext[AbtActMessage]) extends AbstractBehavior[AbtActMessage](context) {
  // TODO: inventory manager
  private var websocketController: Option[ActorRef[AbtActMessage]] = None
  private val userSessions = scala.collection.mutable.Map[String, ActorRef[AbtActMessage]]()

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
    websocketController match {
      case Some(ref) => ref ! msg
      case None =>
        println("UserSessionManager does not have a current ref to WebSocketController")
        context.self ! FindRefs()
    }
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(WebsocketControllerServiceKey, listingResponseAdapter)
        Behaviors.same

      case ListingResponse(WebsocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        Behaviors.same

      case ProvideWebsocketControllerRef(wscRef) =>
        websocketController = Some(wscRef)
        Behaviors.same

      case InitUserSession(uuid, msg, replyTo) =>
        if (msg == "fail") {
          replyTo ! InitUserSessionFailure(uuid)
          return Behaviors.same
        }
        val session = context.spawn(UserSession(uuid, context.self), s"user_session_$uuid")
        if (session == null) {
          replyTo ! InitUserSessionFailure(uuid)
        } else {
          userSessions.put(uuid, session)
          session ! FindRefs()
          replyTo ! InitUserSessionSuccess(uuid)
        }
        Behaviors.same

      case UserAddedItemToCart(itemId, sessionId, inventoryManager) =>
        if (userSessions.contains(sessionId)) {
          userSessions(sessionId) ! AddItemToCart(itemId, inventoryManager)
        }
        Behaviors.same

      case UserRemovedItemFromCart(itemId, sessionId, inventoryManager) =>
        if (userSessions.contains(sessionId)) {
          userSessions(sessionId) ! RemoveItemFromCart(itemId, inventoryManager)
        }
        Behaviors.same

      case TerminateUserSession(sessionId, inventoryManager) =>
        if (userSessions.contains(sessionId)) {
          userSessions(sessionId) ! TerminateSession(inventoryManager)
        }
        Behaviors.same

      case TerminateSessionSuccess(sessionId) =>
        if (userSessions.contains(sessionId)) {
          userSessions.remove(sessionId)
          println(s"Successfully terminated session with sessionId: $sessionId")
        }
        Behaviors.same

      case default =>
        println("UserSessionManager::UnMatchedMethod")
        Behaviors.same
    }
  }
}
