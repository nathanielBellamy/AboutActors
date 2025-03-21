package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import dev.nateschieber.aboutactors.actors.InventoryManager.InventoryManagerServiceKey
import dev.nateschieber.aboutactors.actors.UserSessionSupervisor.UserSessionSupervisorServiceKey
import dev.nateschieber.aboutactors.actors.WebsocketController.WebsocketControllerServiceKey
import dev.nateschieber.aboutactors.{AbtActMessage, FindRefs, ListingResponse, TerminateUserSession, TriggerError, UserAddedItemToCart, UserRemovedItemFromCart}
import dev.nateschieber.aboutactors.dto.{CartItemDto, CartItemJsonSupport, TriggerErrorDto, TriggerErrorJsonSupport, UserSessionIdDto, UserSessionIdJsonSupport}
import dev.nateschieber.aboutactors.enums.HttpPort

import scala.concurrent.ExecutionContext.Implicits.global
import spray.json.*

object RestController {
  private val RestControllerServiceKey = ServiceKey[AbtActMessage]("rest_controller")

  def apply(supervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      context.system.receptionist ! Receptionist.Register(RestControllerServiceKey, context.self)

      val restController = new RestController(context, supervisor)

      context.self ! FindRefs()

      lazy val server = Http()
        .newServerAt("localhost", HttpPort.RestController.port)
        .bind(restController.routes())

      server.map { _ =>
        println("RestControllerServer online at localhost:" + HttpPort.RestController.port)
      } recover { case ex =>
        println(ex.getMessage)
      }

      restController
  }
}

class RestController(
                        context: ActorContext[AbtActMessage],
                        supervisorIn: ActorRef[AbtActMessage]
                    )
  extends AbstractBehavior[AbtActMessage](context)
    with CartItemJsonSupport
    with UserSessionIdJsonSupport
    with TriggerErrorJsonSupport
  {

  private val supervisor: ActorRef[AbtActMessage] = supervisorIn
  private var inventoryManager: Option[ActorRef[AbtActMessage]] = None
  private var websocketController: Option[ActorRef[AbtActMessage]] = None
  private var userSessionManager: Option[ActorRef[AbtActMessage]] = None

  private def sendInventoryManagerMessage(msg: AbtActMessage): Unit = {
    inventoryManager match {
      case Some(ref) => ref ! msg
      case None =>
        println("RestController does not have a current ref to InventoryManager")
        context.self ! FindRefs()
    }
  }

  private def sendUserSessionSupervisorMessage(msg: AbtActMessage): Unit = {
    userSessionManager match {
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
        context.system.receptionist ! Receptionist.Find(InventoryManagerServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(UserSessionSupervisorServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(WebsocketControllerServiceKey, listingResponseAdapter)
        Behaviors.same

      case ListingResponse(InventoryManagerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => inventoryManager = Some(listing))
        Behaviors.same

      case ListingResponse(UserSessionSupervisorServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => userSessionManager = Some(listing))
        Behaviors.same

      case ListingResponse(WebsocketControllerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => websocketController = Some(listing))
        Behaviors.same


      case default =>
        println("RestController Unrecognized Message")
        Behaviors.same
    }
  }
}
