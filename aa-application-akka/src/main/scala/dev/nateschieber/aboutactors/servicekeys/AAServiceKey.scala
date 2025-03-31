package dev.nateschieber.aboutactors.servicekeys

enum AAServiceKey(str: String):
  def key = str

  case InventoryManager extends AAServiceKey("inventory_manager")
  case RestController extends AAServiceKey("rest_controller")
  case UserSessionSupervisor extends AAServiceKey("user_session_supervisor")
  case WebsocketController extends AAServiceKey("websocket_controller")
