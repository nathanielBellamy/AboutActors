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
import dev.nateschieber.aboutactors
import dev.nateschieber.aboutactors.actors.InventoryManager.InventoryManagerServiceKey
import dev.nateschieber.aboutactors.actors.UserSessionSupervisor.UserSessionSupervisorServiceKey
import dev.nateschieber.aboutactors.enums.HttpPort
import dev.nateschieber.aboutactors.{
  AbtActMessage,
  FindRefs,
  HydrateAvailableItems,
  HydrateAvailableItemsRequest,
  HydrateUserSession,
  InitUserSession,
  InitUserSessionFailure,
  InitUserSessionSuccess,
  ListingResponse,
  ProvideInventoryManagerRef,
  ProvideWebsocketControllerRef,
  TerminateSessionSuccess,
  UserAddedItemToCartFailure,
  WsInitUserSession
}

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit
import scala.language.postfixOps
import scala.util.matching.Regex

object WebsocketController {
  val WebsocketControllerServiceKey = ServiceKey[AbtActMessage]("aa_websocket_controller")

  def apply(supervisor: ActorRef[AbtActMessage]): Behavior[AbtActMessage] = Behaviors.setup {
    context =>
      given system: ActorSystem[Nothing] = context.system

      context.system.receptionist ! Receptionist.Register(WebsocketControllerServiceKey, context.self)

      context.self ! FindRefs()

      val aaWebsocketController = new WebsocketController(context, supervisor)

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

class WebsocketController(
                           context: ActorContext[AbtActMessage],
                           supervisorIn: ActorRef[AbtActMessage]
                         ) extends AbstractBehavior[AbtActMessage](context) {

  private val supervisor: ActorRef[AbtActMessage] = supervisorIn

  implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)
  private val cookieMessagePattern: Regex = """\"(?s)(.*)::(?s)(.*)\"""".r

  private val browserConnections = scala.collection.mutable.Map[String, TextMessage => Unit]()
  private var userSessionManager: Option[ActorRef[AbtActMessage]] = None
  private var inventoryManager: Option[ActorRef[AbtActMessage]] = None

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
    println(s"uuid $uuid :: msg $msg")
    if (uuid.isBlank || uuid.isEmpty) {
      println(s"WebsocketController::Received message with no uuid: $msg")
    }
    msg match {
      case "valid" =>
        println(s"WebsocketController::Received valid message from uuid: $uuid")
        try {
          context.self ! WsInitUserSession(uuid, "start")
        } catch {
          case e: Any =>  println(s"WebsocketController::An error occurred spawning UserSession sessionId $uuid : ${e.toString}")
          case default => println(s"WebsocketController::An error occurred spawning UserSession sessionId $uuid")
        }
      case "fail-user-session" =>
        context.self ! WsInitUserSession(uuid, "fail")
      case default =>
        println(s"WebsocketController::Unrecognized websocket message from user sessionId: $uuid")
    }
  }

  private def sendUserSessionSupervisorMessage(msg: AbtActMessage): Unit = {
    userSessionManager match {
      case Some(ref) => ref ! msg
      case None =>
        println("WebSocketController does not have a current ref to UserSessionSupervisor")
        context.self ! FindRefs()
    }
  }

  private def sendInventoryManagerMessage(msg: AbtActMessage): Unit = {
    inventoryManager match {
      case Some(ref) => ref ! msg
      case None =>
        println("WebSocketController does not have a current ref to InventoryManager")
        context.self ! FindRefs()
    }
  }

  override def onMessage(msg: AbtActMessage): Behavior[AbtActMessage] =
    msg match {
      case FindRefs() =>
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse.apply)
        context.system.receptionist ! Receptionist.Find(InventoryManagerServiceKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Find(UserSessionSupervisorServiceKey, listingResponseAdapter)
        Behaviors.same

      case ListingResponse(InventoryManagerServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => inventoryManager = Some(listing))
        sendInventoryManagerMessage(
          ProvideWebsocketControllerRef(context.self)
        )
        sendUserSessionSupervisorMessage(
          ProvideWebsocketControllerRef(context.self)
        )
        Behaviors.same

      case ListingResponse(UserSessionSupervisorServiceKey.Listing(listings)) =>
        // we expect only one listing
        listings.foreach(listing => userSessionManager = Some(listing))
        sendUserSessionSupervisorMessage(
          ProvideWebsocketControllerRef(context.self)
        )
        Behaviors.same

      case ProvideInventoryManagerRef(inventoryManagerRef) =>
        inventoryManager = Some(inventoryManagerRef)
        Behaviors.same

      case WsInitUserSession(uuid, msg) =>
        sendUserSessionSupervisorMessage(
          InitUserSession(uuid, msg, context.self)
        )
        Behaviors.same

      case InitUserSessionSuccess(uuid, session) =>
        sendWebsocketMsg(uuid, "Successfully initialized user session")
        sendInventoryManagerMessage(
          HydrateAvailableItemsRequest( Some(uuid) )
        )
        Behaviors.same

      case InitUserSessionFailure(uuid) =>
        sendWebsocketMsg(uuid, "Unable to initialize user session")
        Behaviors.same

      case HydrateUserSession(dto) =>
        sendWebsocketMsg(dto.sessionId, s"session-item-ids::${dto.itemIds.mkString(",")}")
        Behaviors.same

      case UserAddedItemToCartFailure(itemId, userSessionUuid, replyTo) =>
        sendWebsocketMsg(userSessionUuid, s"item-not-added::$itemId")
        Behaviors.same

      case HydrateAvailableItems(optUuid, dto) =>
        optUuid match {
          case Some(uuid) =>
            sendWebsocketMsg(uuid, s"available-item-ids::${dto.itemIds.mkString(",")}")
          case None =>
            pushWebsocketMsg(s"available-item-ids::${dto.itemIds.mkString(",")}")
        }
        Behaviors.same

      case TerminateSessionSuccess(sessionId) =>
        if (browserConnections.contains(sessionId)) {
          sendWebsocketMsg(sessionId, "session-terminated")
          browserConnections.remove(sessionId)
        }
        Behaviors.same

      case default =>
        println("WebsocketController::UnMatchedMethod")
        Behaviors.same
    }
}
