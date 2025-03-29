package dev.nateschieber.aboutactors.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery}
import akka.persistence.typed.state.{RecoveryCompleted, RecoveryFailed}
import akka.persistence.typed.PersistenceId
import dev.nateschieber.aboutactors.{AbtActMessage, CborSerializable, InventoryItemAddedToCart, InventoryItemRemovedFromCart, ItemAddedToCart}
import scala.concurrent.duration.DurationInt

object Inventory {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Inventory")

  sealed trait Command extends CborSerializable
  final case class AddToCart(itemId: String, sessionId: String, replyTo: ActorRef[StatusReply[AbtActMessage]]) extends Command
  final case class RemoveFromCart(itemId: String, sessionId: String, replyTo: ActorRef[StatusReply[AbtActMessage]]) extends Command

  sealed trait Event extends CborSerializable
  private final case class AddedToCart(itemId: String, sessionId: String) extends Event
  private final case class RemovedFromCart(itemId: String, sessionId: String) extends Event

  final case class State(items: Map[String, Option[String]])

  val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    println(s"INVENTORY STATE: ${state.toString}")
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
      case RemoveFromCart(itemId, sessionId, replyTo) =>
        Effect.persist(RemovedFromCart(itemId, sessionId)).thenRun { _ =>
          replyTo ! StatusReply.success(InventoryItemRemovedFromCart(itemId, sessionId))
        }
    }
  }

  val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case AddedToCart(itemId, sessionId) =>
        State(state.items.updatedWith(itemId) {
          case Some(optString) => Some(Some(sessionId))
          case None => Some(Some(sessionId))
        })
      case RemovedFromCart(itemId, sessionId) =>
        State(state.items.updatedWith(itemId) {
          case Some(optString) => None
          case None => None
        })
    }
  }

  def apply(entityId: String, persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId, // PersistenceId("Inventory", "aa-inventory-state"),
        emptyState = State(
          Map[String, Option[String]](
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
      )
        .onPersistFailure(
          SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
        ).receiveSignal{
          case (state, RecoveryCompleted) =>
            println("Inventory Recovery Completed")
          case (state, RecoveryFailed(cause)) =>
            println(s"Inventory Recovery Failed $cause")
          case (state, signal) =>
            println(s"Inventory received signal $signal")
        }
    }

}
