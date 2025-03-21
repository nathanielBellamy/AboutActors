package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import dev.nateschieber.aboutactors
import dev.nateschieber.aboutactors.actors.WebsocketController.WebsocketControllerServiceKey
import dev.nateschieber.aboutactors.dto.UserSessionDto
import dev.nateschieber.aboutactors.{AbtActMessage, AddItemToCart, CartEmptied, FindRefs, HydrateUserSession, ItemAddedToCart, ItemNotAddedToCart, ItemNotRemovedFromCart, ItemRemovedFromCart, ListingResponse, ProvideInventoryManagerRef, RemoveItemFromCart, RequestToAddItemToCart, RequestToEmptyCart, RequestToRemoveItemFromCart, TerminateSession, TerminateSessionSuccess, UserAddedItemToCartFailure, UserAddedItemToCartSuccess}

import scala.collection.mutable.ListBuffer

object UserSession {

  def apply(uuid: String, userSessionSupervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      val userSessionServiceKey = ServiceKey[AbtActMessage](s"user-session_$uuid")

      context.system.receptionist ! Receptionist.Register(userSessionServiceKey, context.self)

      println(s"starting UserSession with sessionId: $uuid")

      new UserSession(context, uuid, userSessionSupervisor)
  }
}

class UserSession(
                   context: ActorContext[AbtActMessage],
                   uuid: String,
                   userSessionSupervisorIn: ActorRef[AbtActMessage]
                 ) extends AbstractBehavior[AbtActMessage](context) {
  private val sessionId: String = uuid
  private var itemIds: ListBuffer[String] = ListBuffer()
  private val userSessionSupervisor: ActorRef[AbtActMessage] = userSessionSupervisorIn
  private var websocketController: Option[ActorRef[AbtActMessage]] = None

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
    websocketController match {
      case Some(ref) => ref ! msg
      case None =>
        println(s"UserSession does not have a current ref to WebSocketController. uuid: $sessionId")
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

      case AddItemToCart(itemId, inventoryManager) =>
        inventoryManager ! RequestToAddItemToCart(itemId, sessionId, context.self)
        Behaviors.same

      case ItemAddedToCart(itemId, inventoryManager) =>
        itemIds.addOne(itemId)
        val dto = UserSessionDto(sessionId, itemIds.toList)
        sendWebsocketControllerMessage(
          HydrateUserSession(dto)
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
        val dto = UserSessionDto(sessionId, itemIds.toList)
        sendWebsocketControllerMessage(
          HydrateUserSession(dto)
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

      case default =>
        Behaviors.same
    }
  }
}
