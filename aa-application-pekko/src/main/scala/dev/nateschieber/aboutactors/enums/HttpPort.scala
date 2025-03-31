package dev.nateschieber.aboutactors.enums

enum HttpPort(number: Int):
  def port = number
  
  case AboutActorsApplication extends HttpPort(8080)
  case RestController extends HttpPort(4200)
  case WebsocketController extends HttpPort(4201)