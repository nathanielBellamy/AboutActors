package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import dev.nateschieber.aboutactors.{AbtActMessage, FindRefs, ListingResponse, TerminateUserSession, TriggerError, UserAddedItemToCart, UserRemovedItemFromCart}
import dev.nateschieber.aboutactors.dto.{CartItemDto, CartItemJsonSupport, TriggerErrorDto, TriggerErrorJsonSupport, UserSessionIdDto, UserSessionIdJsonSupport}
import dev.nateschieber.aboutactors.servicekeys.{AAServiceKey, ServiceKeyProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import spray.json.*

object RestController {

  def apply(guardianId: Int, supervisor: ActorRef[AbtActMessage], port: Int): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      context.system.receptionist ! Receptionist.Register(
        ServiceKeyProvider.forPair(AAServiceKey.RestController, guardianId),
        context.self
      )

      val restController = new RestController(context, guardianId, supervisor)

      context.self ! FindRefs()

      println(s"RestController port: $port")
      lazy val server = Http()
        .newServerAt("localhost", port)
        .bind(restController.routes())

      server.map { _ =>
        println("RestControllerServer online at localhost:" + port)
      } recover { case ex =>
        println(ex.getMessage)
      }

      restController
  }
}

class RestController(
                        context: ActorContext[AbtActMessage],
                        guardianIdIn: Int,
                        supervisorIn: ActorRef[AbtActMessage]
                    )
  extends AbstractBehavior[AbtActMessage](context)
    with CartItemJsonSupport
    with UserSessionIdJsonSupport
    with TriggerErrorJsonSupport
  {

  private val guardianId: Int = guardianIdIn
  private val supervisor: ActorRef[AbtActMessage] = supervisorIn

  private val inventoryManagerServiceKey: ServiceKey[AbtActMessage] =
    ServiceKeyProvider.forPair(AAServiceKey.InventoryManager, guardianId)
  private var inventoryManager: Option[ActorRef[AbtActMessage]] = None
  private val websocketControllerServiceKey: ServiceKey[AbtActMessage] =
    ServiceKeyProvider.forPair(AAServiceKey.WebsocketController, guardianId)
  private var websocketController: Option[ActorRef[AbtActMessage]] = None
  private val userSessionSupervisorServiceKey: ServiceKey[AbtActMessage] =
    ServiceKeyProvider.forPair(AAServiceKey.UserSessionSupervisor, guardianId)
  private var userSessionSupervisor: Option[ActorRef[AbtActMessage]] = None

  private def sendInventoryManagerMessage(msg: AbtActMessage): Unit = {
    inventoryManager match {
      case Some(ref) => ref ! msg
      case None =>
        println("RestController does not have a current ref to InventoryManager")
        context.self ! FindRefs()
    }
  }

  private def sendUserSessionSupervisorMessage(msg: AbtActMessage): Unit = {
    userSessionSupervisor match {
      case Some(ref) => ref ! msg
      case None =>
        println("RestController does not have a current ref to UserSessionSupervisor")
        context.self ! FindRefs()
    }
  }

  private def sendWebsocketControllerMessage(msg: AbtActMessage): Unit = {
    websocketController match {
      case Some(ref) => ref ! msg
      case None =>
        println("RestController does not have a current ref to WebSocketController")
        context.self ! FindRefs()
    }
  }

  def routes(): Route = {
    concat(
      path("add-item-to-cart") {
        post {
          entity(as[CartItemDto]) { dto => {
            inventoryManager match {
              case Some(ref) =>
                sendUserSessionSupervisorMessage(
                  UserAddedItemToCart(dto.itemId, dto.sessionId, ref)
                )
              case None =>
                println("RestController does not have a current ref to InventoryManager")
                context.self ! FindRefs()
            }
            complete("ok")
          }}
        }
      },
      path("remove-item-from-cart") {
        post {
          entity(as[CartItemDto]) { dto => {
            inventoryManager match {
              case Some(ref) =>
                sendUserSessionSupervisorMessage(
                  UserRemovedItemFromCart(dto.itemId, dto.sessionId, ref)
                )
              case None =>
                println("RestController does not have a current ref to InventoryManager")
                context.self ! FindRefs()
            }
            complete("ok")
          }}
        }
      },
      path("terminate-user-session") {
        post {
          entity(as[UserSessionIdDto]) { dto => {
            inventoryManager match {
              case Some(ref) =>
                sendUserSessionSupervisorMessage(
                  TerminateUserSession(dto.sessionId, ref)
                )
              case None =>
                println("RestController does not have a current ref to InventoryManager")
                context.self ! FindRefs()
            }
            complete("ok")
          }}
        }
      },
      path("trigger-error") {
        post {
          entity(as[TriggerErrorDto]) { dto => {
            dto.actorToError match {
              case "websocket-controller" =>
                sendWebsocketControllerMessage(
                  TriggerError(None)
                )
                complete("ok")
              case "inventory-manager" =>
                sendInventoryManagerMessage(
                  TriggerError(None)
                )
                complete("ok")
              case "user-session-manager" =>
                sendUserSessionSupervisorMessage(
                  TriggerError(None)
                )
                complete("ok")
              case "user-session" =>
                sendUserSessionSupervisorMessage(
                  TriggerError( Some(dto.sessionId) )
                )
                complete("ok")
            }
          }}
        }
      },
      path("") { //the same prefix must be set as base href in index.html
        getFromResource("frontend-dist/browser/index.html")
      } ~ pathPrefix("") {
        getFromResourceDirectory("frontend-dist/browser/") ~
          getFromResource("frontend-dist/browser/index.html")
      },
    )
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] = {
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(inventoryManagerServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(userSessionSupervisorServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(websocketControllerServiceKey, listingResponseAdapter)
        Behaviors.same

      case ListingResponse(inventoryManagerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => inventoryManager = Some(listing))
        Behaviors.same

      case ListingResponse(userSessionSupervisorServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => userSessionSupervisor = Some(listing))
        Behaviors.same

      case ListingResponse(websocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        Behaviors.same

      case default =>
        println("RestController Unrecognized Message")
        Behaviors.same
    }
  }
}
