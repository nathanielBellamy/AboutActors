package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import dev.nateschieber.aboutactors.{AbtActMessage, UserAddedItemToCart, UserRemovedItemFromCart}
import dev.nateschieber.aboutactors.dto.{AddItemToCartDto, AddItemToCartJsonSupport}
import dev.nateschieber.aboutactors.enums.HttpPort

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import spray.json.*

object RestController {

  private val restControllerServiceKey = ServiceKey[AbtActMessage]("rest_controller")

  def apply(websocketController: ActorRef[AbtActMessage], userSessionManager: ActorRef[AbtActMessage], inventoryManager: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      val restController = new RestController(context, websocketController, userSessionManager, inventoryManager)

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
                        websocketControllerIn: ActorRef[AbtActMessage],
                        userSessionManagerIn: ActorRef[AbtActMessage],
                        inventoryManagerIn: ActorRef[AbtActMessage]
                    )
  extends AbstractBehavior[AbtActMessage](context)
    with AddItemToCartJsonSupport
  {

  private val websocketController: ActorRef[AbtActMessage] = websocketControllerIn
  private val userSessionManager: ActorRef[AbtActMessage] = userSessionManagerIn
  private val inventoryManager: ActorRef[AbtActMessage] = inventoryManagerIn

  def routes(): Route = {
    concat(
      path("add-item-to-cart") {
        post {
          entity(as[AddItemToCartDto]) { dto => {
            userSessionManager ! UserAddedItemToCart(dto.itemId, dto.sessionId, inventoryManager)
            complete("ok")
          }}
        }
      },
      path("remove-item-from-cart") {
        post {
          entity(as[AddItemToCartDto]) { dto => {
            userSessionManager ! UserRemovedItemFromCart(dto.itemId, dto.sessionId, inventoryManager)
            complete("ok")
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
    Behaviors.same
  }
}
