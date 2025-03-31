package dev.nateschieber.aboutactors

import org.apache.pekko.actor.AddressFromURIString
import org.apache.pekko.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import dev.nateschieber.aboutactors.actors.Guardian
import scala.jdk.CollectionConverters._

import java.awt.Desktop
import java.net.URI

object AboutActorsApplicationPekko {

  @main def main(): Unit = {
    // start up ActorSystem on shards
    val seedNodePorts = ConfigFactory.load().getStringList("pekko.cluster.seed-nodes").asScala.flatMap {
      case AddressFromURIString(s) => s.port
    }

    // NOTE:
    // - Following https://doc.org.apache.pekko.io/libraries/akka-core/current/attachments/akka-sample-sharding-scala.zip
    //   we init multiple ActorSystems in a single JVM for simplicity
    // NOTE:
    // - Actor systems communicate w/ eachover over seedNodePorts
    // - Users interact with ports based off baseHttpPort
    var guardianId: Int = 0
    var guardianIds: List[Int] = List()
    seedNodePorts.foreach { _ =>
      guardianIds = guardianIds ::: List(guardianId)
      guardianId += 1
    }

    var index: Int = 0
    seedNodePorts.foreach { port =>
      val baseHttpPort = 10000 + port // offset from org.apache.pekko port
      val config = configWithPort(port)
      ActorSystem[AbtActMessage](Guardian(guardianIds(index), baseHttpPort), "AboutActors", config)
      index += 1
    }

    Thread.sleep(3000) // give the system a moment to get init
    if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE))
      Desktop.getDesktop.browse(new URI("http://localhost:" + 12751))
  }

  private def configWithPort(port: Int): Config =
    ConfigFactory.parseString(s"""
       pekko.remote.artery.canonical.port = $port
        """).withFallback(ConfigFactory.load())

}
