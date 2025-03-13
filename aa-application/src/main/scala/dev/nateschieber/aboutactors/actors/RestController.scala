package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import dev.nateschieber.aboutactors.AbtActMessage
import dev.nateschieber.aboutactors.enums.HttpPort

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import spray.json.*

object RestController {

  private val restControllerServiceKey = ServiceKey[AbtActMessage]("rest_controller")

  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      val aaRestController = new RestController(context)

      lazy val server = Http()
        .newServerAt("localhost", HttpPort.RestController.port)
        .bind(aaRestController.routes())

      server.map { _ =>
        println("AARestControllerServer online at localhost:" + HttpPort.RestController.port)
      } recover { case ex =>
        println(ex.getMessage)
      }

      aaRestController
  }
}

class RestController(
                        context: ActorContext[AbtActMessage])
  extends AbstractBehavior[AbtActMessage](context) {

  def routes(): Route = {
    concat(
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
