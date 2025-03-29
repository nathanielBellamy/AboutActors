package dev.nateschieber.aboutactors

import akka.actor.AddressFromURIString
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import dev.nateschieber.aboutactors.actors.Guardian
import dev.nateschieber.aboutactors.enums.HttpPort
import scala.jdk.CollectionConverters._

import java.awt.Desktop
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global

object AboutActorsApplication {

  @main def main(): Unit = {
    // start up ActorSystem on shards
    val seedNodePorts = ConfigFactory.load().getStringList("akka.cluster.seed-nodes").asScala.flatMap {
      case AddressFromURIString(s) => s.port
    }

    // NOTE:
    // - Following https://doc.akka.io/libraries/akka-core/current/attachments/akka-sample-sharding-scala.zip
    //   we init multiple ActorSystems in a single JVM for simplicity
    // NOTE:
    // - Actor systems communicate w/ eachover over seedNodePorts
    // - Users interact with ports based off baseHttpPort
    seedNodePorts.foreach { port =>
      val baseHttpPort = 10000 + port // offset from akka port
      val config = configWithPort(port)
      ActorSystem[AbtActMessage](Guardian(baseHttpPort), "AboutActors", config)
    }

    if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE))
      Desktop.getDesktop.browse(new URI("http://localhost:" + 12751))
  }

  private def configWithPort(port: Int): Config =
    ConfigFactory.parseString(s"""
       akka.remote.artery.canonical.port = $port
        """).withFallback(ConfigFactory.load())

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
