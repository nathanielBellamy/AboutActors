package dev.nateschieber.aboutactors.actors

import akka.NotUsed
import akka.actor.TypedActor.self
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
import dev.nateschieber.aboutactors.enums.HttpPort
import dev.nateschieber.aboutactors.{AbtActMessage, InitUserSession, ProvideSelfRef, UserAddedDevice}

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit
import scala.language.postfixOps
import scala.util.matching.Regex

object WebsocketController {

  private val WebsocketControllerServiceKey = ServiceKey[AbtActMessage]("aa_websocket_controller")

  def apply(): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      context.system.receptionist ! Receptionist.Register(WebsocketControllerServiceKey, context.self)

      given system: ActorSystem[Nothing] = context.system

      val aaWebsocketController = new WebsocketController(context)

      lazy val server = Http()
        .newServerAt("localhost", HttpPort.WebsocketController.port)
        .adaptSettings(_.mapWebsocketSettings(_.withPeriodicKeepAliveMode("ping")))
        .bind(aaWebsocketController.route)

      server.map { _ =>
        println("AAWebsocketControllerServer online at localhost:" + HttpPort.WebsocketController.port)
      } recover { case ex =>
        println(ex.getMessage)
      }

      aaWebsocketController
  }
}

class WebsocketController(context: ActorContext[AbtActMessage]) extends AbstractBehavior[AbtActMessage](context) {

  implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

  private val browserConnections = scala.collection.mutable.Map[String, TextMessage => Unit]()

  private val cookieMessagePattern: Regex = """\"(?s)(.*)::(?s)(.*)\"""".r

  var selfRef: ActorRef[AbtActMessage] = null

  val route: Route =
    path("aa-websocket") {
      get {
        handleWebSocketMessages(websocketFlow)
      }
    }

  private def websocketFlow: Flow[Message, Message, Any] = {
    // based on https://github.com/JannikArndt/simple-akka-websocket-server-push/blob/master/src/main/scala/WebSocket.scala
    val inbound: Sink[Message, Any] = Sink.foreach(msg => {
      println(s"received message ${msg.asTextMessage.getStrictText}")
      if (msg.isText) {
        val msgStr: String = msg.asTextMessage.getStrictText
        msgStr match {
          case cookieMessagePattern(uuid, mmsg) => handleCookieMessage(uuid, mmsg)
          case default => println("WebsocketController::Unrecognized incoming message:" + msgStr)
        }
      }
    }) // frontend pings to keep alive, but backend does not use pings
    val outbound: Source[Message, SourceQueueWithComplete[Message]] = Source.queue[Message](16, OverflowStrategy.fail)

    Flow.fromSinkAndSourceMat(inbound, outbound)((inboundMat, outboundMat) => {
      val uuid = java.util.UUID.randomUUID.toString
      browserConnections.put(uuid, outboundMat.offer)
      browserConnections(uuid)(toTextMessage("cookie::" + uuid))
      NotUsed
    })
  }

  private def sendWebsocketMsg(uuid: String, text: String): Unit = {
    val connOpt = browserConnections.get(uuid)
    if (connOpt.isDefined) {
      val conn = connOpt.get
      conn(toTextMessage(text))
    } else {
      println(s"Websocket connection not found for uuid: $uuid")
    }
  }

  private def pushWebsocketMsg(text: String): Unit = {
    for ((uuid,conn) <- browserConnections) conn(toTextMessage(text))
  }

  private def toTextMessage(str: String): TextMessage = {
    TextMessage.Strict(wrapQuotes(str))
  }

  private def wrapQuotes(str: String): String = {
    s"\"$str\""
  }

  private def handleCookieMessage(uuid: String, msg: String): Unit = {
    println(s"uuid $uuid :wowza: msg $msg")
    if (uuid.isBlank || uuid.isEmpty) {
      println(s"WebsocketController::Received message with no uuid: $msg")
    }
    msg match {
      case "valid" =>
        println(s"WebsocketController::Received valid message from uuid: $uuid")
        try {
          selfRef ! InitUserSession(uuid)
        } catch {
          case e: Any =>  println(s"WebsocketController::An error occurred spawning UserSession sessionId $uuid : ${e.toString}")
          case default => println(s"WebsocketController::An error occurred spawning UserSession sessionId $uuid")
        }
      case default =>
        println(s"WebsocketController::Unrecognized websocket message from user sessionId: $uuid")
    }
    sendWebsocketMsg(uuid, "only to you")
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] =
    msg match {
      case ProvideSelfRef(self) =>
        selfRef = self
        Behaviors.same

      case InitUserSession(uuid) =>
        val session = context.spawn(UserSession(uuid), s"user_session_$uuid")
        Behaviors.same

      case UserAddedDevice() =>
        println("WebsocketController::UserAddedDevice")
        Behaviors.same

      case default =>
        println("WebsocketController::UnMatchedMethod")
        Behaviors.same
    }
}
