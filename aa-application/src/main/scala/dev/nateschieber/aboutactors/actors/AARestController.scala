package dev.nateschieber.aboutactors.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import dev.nateschieber.aboutactors.AAMessage
import dev.nateschieber.aboutactors.enums.AAHttpPort

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import spray.json.*

object AARestController {

  private var appStateCacheFile: String = "__GROOVE_SPRINGS__LAST_STATE__.json"

  val AARestControllerServiceKey = ServiceKey[AAMessage]("aa_rest_controller")

  def apply(): Behavior[AAMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      val aaRestController = new AARestController(context)

      lazy val server = Http()
        .newServerAt("localhost", AAHttpPort.AARestController.port)
        .bind(aaRestController.routes())

      server.map { _ =>
        println("AARestControllerServer online at localhost:" + AAHttpPort.AARestController.port)
      } recover { case ex =>
        println(ex.getMessage)
      }

      aaRestController
  }
}

class AARestController(
                        context: ActorContext[AAMessage])
  extends AbstractBehavior[AAMessage](context) {

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

  override def onMessage(msg: AAMessage): Behavior[AAMessage] = {
    Behaviors.same
  }
}
