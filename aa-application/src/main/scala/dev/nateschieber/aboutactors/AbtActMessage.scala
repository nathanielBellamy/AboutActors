package dev.nateschieber.aboutactors

sealed trait AbtActMessage

final case class UserAddedDevice() extends AbtActMessage