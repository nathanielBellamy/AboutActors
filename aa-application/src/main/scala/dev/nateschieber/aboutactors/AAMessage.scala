package dev.nateschieber.aboutactors

sealed trait AAMessage

final case class UserAddedDevice() extends AAMessage