package dev.nateschieber.aboutactors.actors

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{get, handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import dev.nateschieber.aboutactors.enums.AAHttpPort
import dev.nateschieber.aboutactors.{AAMessage, UserAddedDevice}

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit
import scala.language.postfixOps
import scala.util.matching.Regex

object AAWebsocketController {

  val AAWebsocketControllerServiceKey = ServiceKey[AAMessage]("aa_websocket_controller")

  def apply(): Behavior[AAMessage] = Behaviors.setup {
    context =>
      context.system.receptionist ! Receptionist.Register(AAWebsocketControllerServiceKey, context.self)

      given system: ActorSystem[Nothing] = context.system

      val aaWebsocketController = new AAWebsocketController(context)

      lazy val server = Http()
        .newServerAt("localhost", AAHttpPort.AAWebsocketController.port)
        .adaptSettings(_.mapWebsocketSettings(_.withPeriodicKeepAliveMode("ping")))
        .bind(aaWebsocketController.route)

      server.map { _ =>
        println("AAWebsocketControllerServer online at localhost:" + AAHttpPort.AAWebsocketController.port)
      } recover { case ex =>
        println(ex.getMessage)
      }

      aaWebsocketController
  }
}

class AAWebsocketController(context: ActorContext[AAMessage]) extends AbstractBehavior[AAMessage](context) {

  implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

  private val browserConnections = scala.collection.mutable.Map[String, TextMessage => Unit]()

  private val cookieMessagePattern: Regex = """\"(?s)(.*)::(?s)(.*)\"""".r

  val route: Route =
    path("aa-websocket") {
      get {
        handleWebSocketMessages(websocketFlow)
      }
    }

  def websocketFlow: Flow[Message, Message, Any] = {
    // based on https://github.com/JannikArndt/simple-akka-websocket-server-push/blob/master/src/main/scala/WebSocket.scala
    val inbound: Sink[Message, Any] = Sink.foreach(msg => {
      println("received message")
      if (msg.isText) {
        val msgStr: String = msg.asTextMessage.getStrictText
        msgStr match {
          case cookieMessagePattern(uuid, mmsg) =>
            println(s"uuid $uuid :wowza: msg $mmsg")
            sendWebsocketMsg(uuid, "only to you")
          case default =>
            println("AAWebsocketController::Unrecognized incoming message:" + msgStr)
        }
      }
    }) // frontend pings to keep alive, but backend does not use pings
    val outbound: Source[Message, SourceQueueWithComplete[Message]] = Source.queue[Message](16, OverflowStrategy.fail)

    Flow.fromSinkAndSourceMat(inbound, outbound)((inboundMat, outboundMat) => {
      val uuid = java.util.UUID.randomUUID.toString
      browserConnections.put(uuid, outboundMat.offer)
      browserConnections(uuid)(TextMessage.Strict("\"cookie::" + uuid + "\""))
      NotUsed
    })
  }

  def sendWebsocketMsg(uuid: String, text: String): Unit = {
    browserConnections(uuid)(TextMessage.Strict(s"\"$text\""))
  }

  def pushWebsocketMsg(text: String): Unit = {
    for ((uuid,conn) <- browserConnections) conn(TextMessage.Strict(s"\"$text\""))
  }

  override def onMessage(msg: AAMessage): Behavior[AAMessage] = {
    msg match {
      case UserAddedDevice() =>
        println("AAWebsocketController UserAddedDevice")
        Behaviors.same

      case default =>
        println("AAWebsocketController::UnMatchedMethod")
        Behaviors.same
    }
  }
}
