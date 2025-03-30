package dev.nateschieber.aboutactors.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import dev.nateschieber.aboutactors.dto.AvailableItemsDto
import dev.nateschieber.aboutactors.{AbtActMessage, CartEmptied, FindRefs, HydrateAvailableItems, HydrateAvailableItemsRequest, InventoryAvailableItems, InventoryItemAddedToCart, InventoryItemNotAddedToCart, InventoryItemRemovedFromCart, ItemAddedToCart, ItemNotAddedToCart, ItemNotRemovedFromCart, ItemRemovedFromCart, ListingResponse, ProvideInventoryManagerRef, ProvideWebsocketControllerRef, RefreshedSessionItems, RequestRefreshSessionItems, RequestToAddItemToCart, RequestToEmptyCart, RequestToRemoveItemFromCart, TriggerError}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import dev.nateschieber.aboutactors.servicekeys.{AAServiceKey, ServiceKeyProvider}

import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt


object InventoryManager {
  
  def apply(id: Int, supervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage | StatusReply[AbtActMessage]] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system
      println("Starting InventoryManager")

      context.system.receptionist ! Receptionist.Register(
        ServiceKeyProvider.forPair(AAServiceKey.InventoryManager, id), 
        context.self
      )

      val sharding = ClusterSharding(system)
      sharding.init(
        Entity(Inventory.TypeKey) { entityContext =>
          Inventory.apply(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
        }
      )

      context.self ! FindRefs()

      new InventoryManager(context, id, supervisor, "my-inv-ent-id")
  }
}

class InventoryManager(
                        context: ActorContext[AbtActMessage | StatusReply[AbtActMessage]],
                        guardianIdIn: Int,
                        supervisorIn: ActorRef[AbtActMessage],
                        inventoryEntityId: String
                      ) extends AbstractBehavior[AbtActMessage | StatusReply[AbtActMessage]](context) {
  
  private val guardianId: Int = guardianIdIn
  private val supervisor: ActorRef[AbtActMessage] = supervisorIn
  
  private val userSessionSupervisorServiceKey: ServiceKey[AbtActMessage] =
    ServiceKeyProvider.forPair(AAServiceKey.UserSessionSupervisor, guardianId)
  private var userSessionSupervisor: Option[ActorRef[AbtActMessage]] = None
  private val websocketControllerServiceKey: ServiceKey[AbtActMessage] =
    ServiceKeyProvider.forPair(AAServiceKey.WebsocketController, guardianId)
  private var websocketController: Option[ActorRef[AbtActMessage]] = None

  private val sharding = ClusterSharding(context.system)

  private var inventory = sharding.entityRefFor(Inventory.TypeKey, inventoryEntityId)

  // NOTE:
  // - timeout for interactions with inventory
  implicit val timeout: Timeout = 5.seconds

  private val items = scala.collection.mutable.Map[String, Option[String]](
    "001" -> None,
    "002" -> None,
    "003" -> None,
    "004" -> None,
    "005" -> None,
    "006" -> None,
    "007" -> None,
  ) // key: itemId, value: owned by userSessionId

  private def getInventoryEntityRef: Unit = {
    inventory = sharding.entityRefFor(Inventory.TypeKey, inventoryEntityId)
  }

  private def hydrateAvailableItemsDto(optSessionId: Option[String]): Unit = {
    val res: Future[StatusReply[AbtActMessage]] = inventory.ask(
      manager => Inventory.GetAvailableItems(manager)
    )
    implicit val ec = context.system.executionContext

    res.onComplete {
      case Success(StatusReply.Success(InventoryAvailableItems(availableItems))) =>
        sendWebsocketControllerMessage(
          HydrateAvailableItems(optSessionId, AvailableItemsDto(availableItems) )
        )
        InventoryAvailableItems(availableItems)

      case Failure(msg) =>
        println("InventoryManager unable to contact Inventory for available items")
        getInventoryEntityRef
        hydrateAvailableItemsDto(optSessionId)
    }
  }

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
    websocketController match {
      case Some(ref) => ref ! msg
      case None =>
        println("InventoryManager does not have a current ref to WebSocketController")
        context.self ! FindRefs()
    }
  }

  private def sendUserSessionSupervisorMessage(msg: AbtActMessage): Unit = {
    userSessionSupervisor match {
      case Some(ref) => ref ! msg
      case None =>
        println("InventoryManager does not have a current ref to UserSessionSupervisor")
        context.self ! FindRefs()
    }
  }

  override def onMessage(msg: AbtActMessage | StatusReply[AbtActMessage]): Behavior[AbtActMessage | StatusReply[AbtActMessage]] = {
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(userSessionSupervisorServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(websocketControllerServiceKey, listingResponseAdapter)

        Behaviors.same

      case ListingResponse(userSessionSupervisorServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => userSessionSupervisor = Some(listing))
        sendUserSessionSupervisorMessage(
          ProvideInventoryManagerRef(context.self)
        )
        Behaviors.same

      case ListingResponse(websocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        sendWebsocketControllerMessage(
          ProvideInventoryManagerRef(context.self)
        )
        Behaviors.same

      case RequestToAddItemToCart(itemId, sessionId, userSessionRef) =>
        val res: Future[StatusReply[AbtActMessage]] = inventory.ask(
          manager => Inventory.AddToCart(itemId, sessionId, manager)
        )

        implicit val ec = context.system.executionContext

        res.onComplete {
            case Success(StatusReply.Success(InventoryItemAddedToCart(itemId, sessionId))) =>
              println(s"Added item $itemId to sessionId $sessionId")
              userSessionRef ! ItemAddedToCart(itemId, context.self)
              hydrateAvailableItemsDto(None)
              ItemAddedToCart(itemId, context.self)
            case Success(StatusReply.Error(msg)) =>
              println(s"Failed to add item to cart: $msg")
              ItemNotAddedToCart(itemId, context.self)
            case Failure(exception) =>
              println(s"InventoryManager unable to contact Inventory: $exception")
              getInventoryEntityRef
              ItemNotAddedToCart(itemId, context.self)
        }

        Behaviors.same

      case RequestToRemoveItemFromCart(itemId, sessionId, userSessionRef) =>
        val res: Future[StatusReply[AbtActMessage]] = inventory.ask(
          manager => Inventory.RemoveFromCart(itemId, sessionId, manager)
        )

        implicit val ec = context.system.executionContext

        res.onComplete {
          case Success(StatusReply.Success(InventoryItemRemovedFromCart(itemId, sessionId))) =>
            userSessionRef ! ItemRemovedFromCart(itemId, context.self)
            hydrateAvailableItemsDto(None)
            ItemRemovedFromCart(itemId, context.self)
          case Failure(exception) =>
            userSessionRef ! ItemNotRemovedFromCart(itemId, userSessionRef)
            println(s"InventoryManager unable to contact Inventory: $exception")
            getInventoryEntityRef
            ItemNotRemovedFromCart(itemId, context.self)
        }

        Behaviors.same

      case RequestToEmptyCart(sessionId, userSession) =>
        for ((itemId, optSessionId) <- items) {
          if (optSessionId.isDefined && optSessionId.get == sessionId) {
            items.update(itemId, None)
          }
        }
        hydrateAvailableItemsDto(None)
        userSession ! CartEmptied(context.self)
        Behaviors.same

      case HydrateAvailableItemsRequest(optUuid) =>
        hydrateAvailableItemsDto(optUuid)
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
