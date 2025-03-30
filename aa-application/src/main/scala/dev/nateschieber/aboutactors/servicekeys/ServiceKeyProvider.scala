package dev.nateschieber.aboutactors.servicekeys

import akka.actor.typed.receptionist.ServiceKey
import dev.nateschieber.aboutactors.AbtActMessage

object ServiceKeyProvider {

  def forPair(serviceKey: AAServiceKey, guardianId: Int): ServiceKey[AbtActMessage] = {
    ServiceKey[AbtActMessage](s"${serviceKey.key}_guardian_$guardianId")
  }

}
