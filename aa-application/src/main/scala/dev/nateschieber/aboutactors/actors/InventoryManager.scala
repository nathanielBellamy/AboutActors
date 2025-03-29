package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import dev.nateschieber.aboutactors.actors.UserSessionSupervisor.UserSessionSupervisorServiceKey
import dev.nateschieber.aboutactors.actors.WebsocketController.WebsocketControllerServiceKey
import dev.nateschieber.aboutactors.dto.AvailableItemsDto
import dev.nateschieber.aboutactors.{AbtActMessage, CartEmptied, FindRefs, HydrateAvailableItems, HydrateAvailableItemsRequest, ItemAddedToCart, ItemNotAddedToCart, ItemNotRemovedFromCart, ItemRemovedFromCart, ListingResponse, ProvideInventoryManagerRef, ProvideWebsocketControllerRef, RefreshedSessionItems, RequestRefreshSessionItems, RequestToAddItemToCart, RequestToEmptyCart, RequestToRemoveItemFromCart, TriggerError}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId


object InventoryManager {
  val InventoryManagerServiceKey = ServiceKey[AbtActMessage]("inventory-manager")

  def apply(supervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage | StatusReply[AbtActMessage]] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system
      import system.executionContext
      println("Starting InventoryManager")

      context.system.receptionist ! Receptionist.Register(InventoryManagerServiceKey, context.self)

      val sharding = ClusterSharding(system)
      sharding.init(
        Entity(Inventory.TypeKey) { entityContext =>
          Inventory.apply(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
        }
      )

      try {
        val inventoryEntityId = "inventory-main-entity-id"
        val inventory = sharding.entityRefFor(Inventory.TypeKey, inventoryEntityId)
        inventory ! Inventory.AddToCart("001", "222", context.self)
      } catch {
        case e: Throwable => 
          println(s"Inv Mang errored sending entity cmd: $e")
      }
      

      context.self ! FindRefs()

      new InventoryManager(context, supervisor, "my-inv-ent-id")
  }
}

class InventoryManager(
                        context: ActorContext[AbtActMessage | StatusReply[AbtActMessage]],
                        supervisorIn: ActorRef[AbtActMessage],
                        inventoryEntityId: String
                      ) extends AbstractBehavior[AbtActMessage | StatusReply[AbtActMessage]](context) {
  private val supervisor: ActorRef[AbtActMessage] = supervisorIn
  private var userSessionSupervisor: Option[ActorRef[AbtActMessage]] = None
  private var websocketController: Option[ActorRef[AbtActMessage]] = None

  private val sharding = ClusterSharding(context.system)

  private val inventory = sharding.entityRefFor(Inventory.TypeKey, inventoryEntityId)

  private val items = scala.collection.mutable.Map[String, Option[String]](
    "001" -> None,
    "002" -> None,
    "003" -> None,
    "004" -> None,
    "005" -> None,
    "006" -> None,
    "007" -> None,
  ) // key: itemId, value: owned by userSessionId

  private def getAvailableItemsDto: AvailableItemsDto = {
    val availableItems = items.keys.filter(k => items.get(k).get.isEmpty).toList
    AvailableItemsDto(availableItems)
  }

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
//    websocketController match {
//      case Some(ref) => ref ! msg
//      case None =>
//        println("InventoryManager does not have a current ref to WebSocketController")
//        context.self ! FindRefs()
//    }
  }

  private def sendUserSessionSupervisorMessage(msg: AbtActMessage): Unit = {
//    userSessionSupervisor match {
//      case Some(ref) => ref ! msg
//      case None =>
//        println("InventoryManager does not have a current ref to UserSessionSupervisor")
//        context.self ! FindRefs()
//    }
  }

  override def onMessage(msg: AbtActMessage | StatusReply[AbtActMessage]): Behavior[AbtActMessage | StatusReply[AbtActMessage]] = {
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(UserSessionSupervisorServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(WebsocketControllerServiceKey, listingResponseAdapter)

        Behaviors.same

      case ListingResponse(UserSessionSupervisorServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => userSessionSupervisor = Some(listing))
        sendUserSessionSupervisorMessage(
          ProvideInventoryManagerRef(context.self)
        )
        Behaviors.same

      case ListingResponse(WebsocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        sendWebsocketControllerMessage(
          ProvideInventoryManagerRef(context.self)
        )
        Behaviors.same

      case RequestToAddItemToCart(itemId, sessionId, userSessionRef) =>

        println("Inventory Manager will send command to Inventory")
        inventory ! Inventory.AddToCart(itemId, sessionId, context.self)

        items(itemId) match {
          case Some(_) =>
            // Item already taken
            userSessionRef ! ItemNotAddedToCart(itemId, context.self)
          case None =>
            items.update(itemId, Some(sessionId))
            userSessionRef ! ItemAddedToCart(itemId, context.self)
            sendWebsocketControllerMessage(
              HydrateAvailableItems( None, getAvailableItemsDto )
            )
        }
        Behaviors.same

      case RequestToRemoveItemFromCart(itemId, userSessionUuid, userSessionRef) =>
        items(itemId) match {
          case Some(_) =>
            items.update(itemId, None)
            userSessionRef ! ItemRemovedFromCart(itemId, context.self)
            sendWebsocketControllerMessage(
              HydrateAvailableItems( None, getAvailableItemsDto )
            )
          case None =>
            userSessionRef ! ItemNotRemovedFromCart(itemId, userSessionRef)
        }
        Behaviors.same

      case RequestToEmptyCart(sessionId, userSession) =>
        for ((itemId, optSessionId) <- items) {
          if (optSessionId.isDefined && optSessionId.get == sessionId) {
            items.update(itemId, None)
          }
        }
        sendWebsocketControllerMessage(
          HydrateAvailableItems( None , getAvailableItemsDto )
        )
        userSession ! CartEmptied(context.self)
        Behaviors.same

      case HydrateAvailableItemsRequest(optUuid) =>
        sendWebsocketControllerMessage(
          HydrateAvailableItems( optUuid, getAvailableItemsDto )
        )
        Behaviors.same

      case ProvideWebsocketControllerRef(websocketControllerRef) =>
        websocketController = Some(websocketControllerRef)
        Behaviors.same

      case RequestRefreshSessionItems(sessionId, replyTo) =>
        val list = items.filter(pair => pair._2.isDefined && pair._2.get == sessionId).keys.toList
        replyTo ! RefreshedSessionItems(list, context.self)
        Behaviors.same

      case TriggerError(_) =>
        println("Triggering error in Inventory Manager")
        throw Error("Error Triggered in Inventory Manager")

      case default =>
        println("InventoryManager::UnMatchedMethod")
        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[AbtActMessage | StatusReply[AbtActMessage]]] = {
    case PostStop =>
      println("InventoryManager Stopped")
      Behaviors.same
  }
}
