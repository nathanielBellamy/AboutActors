package dev.nateschieber.aboutactors

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import dev.nateschieber.aboutactors.actors.Supervisor
import dev.nateschieber.aboutactors.enums.HttpPort

import java.awt.Desktop
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global

object AboutActorsApplication {

  @main def main(): Unit = {
    given system: ActorSystem[Nothing] = ActorSystem(Supervisor(), "aa_application")

    lazy val server = Http().newServerAt("localhost", HttpPort.AboutActorsApplication.port).bind(routes())

    server.map(_ => {
      //
    })

    if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE))
      Desktop.getDesktop.browse(new URI("http://localhost:" + HttpPort.RestController.port ))
  }

  private def routes(): Route = {
    concat(
      path("api" / "v1" / "hello") {
        get {
          complete("Welcome to About Actors.")
        }
      }
    )
  }
}
