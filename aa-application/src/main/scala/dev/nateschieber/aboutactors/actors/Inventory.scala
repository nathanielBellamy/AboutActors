package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery}
import akka.persistence.typed.PersistenceId
import dev.nateschieber.aboutactors.{AbtActMessage, InventoryItemAddedToCart, ItemAddedToCart}

object Inventory {
  sealed trait Command
  final case class AddToCart(itemId: String, sessionId: String, replyTo: ActorRef[StatusReply[AbtActMessage]]) extends Command
  final case class RemoveFromCart(itemId: String, sessionId: String) extends Command

  sealed trait Event
  private final case class AddedToCart(itemId: String, sessionId: String) extends Event
  private final case class RemovedFromCart(itemId: String, sessionId: String) extends Event

  final case class State(items: scala.collection.mutable.Map[String, Option[String]])

  private val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
      case AddToCart(itemId, sessionId, replyTo) =>
        state.items(itemId) match {
          case Some(sessionId) =>
            Effect.none.thenRun { _ =>
              replyTo ! StatusReply.error("Item already in another cart.")
            }
          case None =>
            Effect.persist(AddedToCart(itemId, sessionId)).thenRun { _ =>
              replyTo ! StatusReply.success(InventoryItemAddedToCart(itemId, sessionId))
            }
        }
      case RemoveFromCart(itemId, sessionId) => Effect.persist(RemovedFromCart(itemId, sessionId))
    }
  }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case AddedToCart(itemId, sessionId) =>
        state.items.update(itemId, Some(sessionId))
        state
      case RemovedFromCart(itemId, sessionId) =>
        state.items.update(itemId, None)
        state
    }
  }

  def apply(): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("aa-inventory-state"),
      emptyState = State(
        scala.collection.mutable.Map[String, Option[String]](
          "001" -> None,
          "002" -> None,
          "003" -> None,
          "004" -> None,
          "005" -> None,
          "006" -> None,
          "007" -> None,
        ) // key: itemId, value: owned by userSessionId
      ),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    ).withRecovery(Recovery.default)
}