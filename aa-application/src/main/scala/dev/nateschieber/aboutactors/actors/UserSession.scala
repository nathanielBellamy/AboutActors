package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors
import dev.nateschieber.aboutactors.dto.UserSessionDto
import dev.nateschieber.aboutactors.servicekeys.AAServiceKey.WebsocketController
import dev.nateschieber.aboutactors.servicekeys.{AAServiceKey, ServiceKeyProvider}
import dev.nateschieber.aboutactors.{AbtActMessage, AddItemToCart, CartEmptied, FindRefs, HydrateUserSession, InitUserSessionSuccess, ItemAddedToCart, ItemNotAddedToCart, ItemNotRemovedFromCart, ItemRemovedFromCart, ListingResponse, ProvideInventoryManagerRef, RefreshItemsFromInventory, RefreshedSessionItems, RemoveItemFromCart, RequestRefreshSessionItems, RequestToAddItemToCart, RequestToEmptyCart, RequestToRemoveItemFromCart, TerminateSession, TerminateSessionSuccess, TriggerError, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

import scala.collection.mutable.ListBuffer

object UserSession {

  def apply(guardianId: Int, uuid: String, userSessionSupervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      val userSessionServiceKey = ServiceKey[AbtActMessage](s"user-session_$uuid")

      context.system.receptionist ! Receptionist.Register(userSessionServiceKey, context.self)

      println(s"starting UserSession with sessionId: $uuid")

      val self = new UserSession(context, guardianId, uuid, userSessionSupervisor)

      context.self ! FindRefs()
      userSessionSupervisor ! InitUserSessionSuccess(uuid, context.self)

      self
  }
}

class UserSession(
                   context: ActorContext[AbtActMessage],
                   guardianIdIn: Int,
                   uuid: String,
                   userSessionSupervisorIn: ActorRef[AbtActMessage]
                 ) extends AbstractBehavior[AbtActMessage](context) {
  private val guardianId: Int = guardianIdIn
  private val sessionId: String = uuid
  private var itemIds: ListBuffer[String] = ListBuffer()
  private val userSessionSupervisor: ActorRef[AbtActMessage] = userSessionSupervisorIn
  
  private val websocketControllerServiceKey: ServiceKey[AbtActMessage] =
    ServiceKeyProvider.forPair(WebsocketController, guardianId)
  private var websocketController: Option[ActorRef[AbtActMessage]] = None

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
    websocketController match {
      case Some(ref) => ref ! msg
      case None =>
        println(s"UserSession does not have a current ref to WebSocketController. uuid: $sessionId")
        context.self ! FindRefs()
    }
  }

  private def getSessionDto: UserSessionDto = {
    UserSessionDto(sessionId, itemIds.toList)
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(websocketControllerServiceKey, listingResponseAdapter)
        Behaviors.same

      case ListingResponse(websocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        Behaviors.same

      case AddItemToCart(itemId, inventoryManager) =>
        inventoryManager ! RequestToAddItemToCart(itemId, sessionId, context.self)
        Behaviors.same

      case ItemAddedToCart(itemId, inventoryManager) =>
        itemIds.addOne(itemId)
        sendWebsocketControllerMessage(
          HydrateUserSession(getSessionDto)
        )
        Behaviors.same

      case ItemNotAddedToCart(itemId, inventoryManager) =>
        sendWebsocketControllerMessage(
          UserAddedItemToCartFailure(itemId, sessionId, context.self)
        )
        Behaviors.same

      case RemoveItemFromCart(itemId, inventoryManager) =>
        inventoryManager ! RequestToRemoveItemFromCart(itemId, sessionId, context.self)
        Behaviors.same

      case ItemRemovedFromCart(itemId, inventoryManager) =>
        itemIds = itemIds.filter(id => id != itemId)
        sendWebsocketControllerMessage(
          HydrateUserSession(getSessionDto)
        )
        Behaviors.same

      case ItemNotRemovedFromCart(itemId, inventoryManager) =>
        Behaviors.same

      case TerminateSession(inventoryManager) =>
        inventoryManager ! RequestToEmptyCart(sessionId, context.self)
        Behaviors.same

      case CartEmptied(replyTo) =>
        userSessionSupervisor ! TerminateSessionSuccess(sessionId)
        sendWebsocketControllerMessage(
          TerminateSessionSuccess(sessionId)
        )
        Behaviors.stopped

      case RefreshItemsFromInventory(inventoryManager) =>
        inventoryManager ! RequestRefreshSessionItems(sessionId, context.self)
        Behaviors.same

      case RefreshedSessionItems(refreshedItemIds, replyTo) =>
        refreshedItemIds.foreach(id => itemIds.addOne(id))
        sendWebsocketControllerMessage(
          HydrateUserSession(getSessionDto)
        )
        Behaviors.same

      case TriggerError(_) =>
        println(s"Will trigger error in UserSession sessionId: $sessionId")
        throw Error(s"UserSession Error for sessionId $sessionId")

      case default =>
        Behaviors.same
    }
  }
}
