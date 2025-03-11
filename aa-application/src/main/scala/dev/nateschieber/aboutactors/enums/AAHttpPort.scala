package dev.nateschieber.aboutactors.enums

enum AAHttpPort(number: Int):
  def port = number
  
  case AAApplication extends AAHttpPort(8080)
  case AARestController extends AAHttpPort(4200)
  case AAWebsocketController extends AAHttpPort(4201)