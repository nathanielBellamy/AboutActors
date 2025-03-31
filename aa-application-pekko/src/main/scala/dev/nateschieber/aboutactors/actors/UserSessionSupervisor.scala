package dev.nateschieber.aboutactors.actors

import org.apache.pekko.actor.typed.receptionist.{Receptionist, ServiceKey}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import org.apache.pekko.actor.typed.scaladsl.AbstractBehavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors
import dev.nateschieber.aboutactors.servicekeys.{AAServiceKey, ServiceKeyProvider}
import dev.nateschieber.aboutactors.{AbtActMessage, AddItemToCart, FindRefs, InitUserSession, InitUserSessionFailure, InitUserSessionSuccess, ListingResponse, ProvideInventoryManagerRef, ProvideWebsocketControllerRef, RefreshItemsFromInventory, RemoveItemFromCart, TerminateSession, TerminateSessionSuccess, TerminateUserSession, TriggerError, UserAddedItemToCart, UserRemovedItemFromCart}

object UserSessionSupervisor {

  def apply(guardianId: Int, supervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system
      println("Starting UserSessionSupervisor")

      context.system.receptionist ! Receptionist.Register(
        ServiceKeyProvider.forPair(AAServiceKey.UserSessionSupervisor, guardianId), 
        context.self
      )

      context.self ! FindRefs()

      new UserSessionSupervisor(context, guardianId, supervisor)
  }
}

class UserSessionSupervisor(
                          context: ActorContext[AbtActMessage],
                          guardianIdIn: Int,
                          supervisorIn: ActorRef[AbtActMessage]
                        ) extends AbstractBehavior[AbtActMessage](context) {
  private val guardianId: Int = guardianIdIn
  private val supervisor: ActorRef[AbtActMessage] = supervisorIn
  
  private val inventoryManagerServiceKey =
    ServiceKeyProvider.forPair(AAServiceKey.InventoryManager, guardianId)
  private var inventoryManager: Option[ActorRef[AbtActMessage]] = None
  private val websocketControllerServiceKey =
    ServiceKeyProvider.forPair(AAServiceKey.WebsocketController, guardianId)
  private var websocketController: Option[ActorRef[AbtActMessage]] = None
  private val userSessions = scala.collection.mutable.Map[String, ActorRef[AbtActMessage]]()

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
    websocketController match {
      case Some(ref) => ref ! msg
      case None =>
        println("UserSessionSupervisor does not have a current ref to WebSocketController")
        context.self ! FindRefs()
    }
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(inventoryManagerServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(websocketControllerServiceKey, listingResponseAdapter)
        Behaviors.same

      case ListingResponse(inventoryManagerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => inventoryManager = Some(listing))
        Behaviors.same

      case ProvideInventoryManagerRef(imRef) =>
        inventoryManager = Some(imRef)
        Behaviors.same

      case ListingResponse(websocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        Behaviors.same

      case ProvideWebsocketControllerRef(wscRef) =>
        websocketController = Some(wscRef)
        Behaviors.same

      case InitUserSession(uuid, msg, replyTo) =>
        // Here for example
        if (msg == "fail") {
          replyTo ! InitUserSessionFailure(uuid)
          return Behaviors.same
        }
        // End example

        try {
          val supervisedSession = Behaviors
            .supervise(
              UserSession(guardianId, uuid, context.self)
            )
            .onFailure[Throwable](SupervisorStrategy.restart)
          val session = context.spawn(supervisedSession, s"user_session_$uuid")
        } catch {
          case e:Exception => replyTo ! InitUserSessionFailure(uuid)
        }
        Behaviors.same

      case InitUserSessionSuccess(uuid, session) =>
        userSessions.put(uuid, session)
        inventoryManager match {
          case Some(ref) =>
            session ! RefreshItemsFromInventory(ref)
          case None =>
            println("UserSessionSupervisor does not have a current ref to InventoryManager")
            context.self ! FindRefs()
        }

        sendWebsocketControllerMessage(
          InitUserSessionSuccess(uuid, session)
        )
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

      case TriggerError(optSessionId) =>
        optSessionId match {
          case Some(sessionId) => userSessions(sessionId) ! TriggerError(None)
          case None =>
            println("Triggering Error in UserSessionSupervisor")
            throw Error("UserSessionSupervisor Error")
        }
        Behaviors.same

      case default =>
        println("UserSessionSupervisor::UnMatchedMethod")
        Behaviors.same
    }
  }
}
