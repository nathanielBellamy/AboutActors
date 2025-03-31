package dev.nateschieber.aboutactors.topics

import akka.actor.typed.{ActorRef}
import akka.actor.typed.pubsub.{PubSub, Topic}
import akka.actor.typed.scaladsl.ActorContext
import akka.pattern.StatusReply
import dev.nateschieber.aboutactors.{AbtActMessage, HydrateAvailableItems}

object AvailableItems {

  def getTopic(
                context: ActorContext[AbtActMessage] | ActorContext[AbtActMessage | StatusReply[AbtActMessage]]
              ): ActorRef[Topic.Command[HydrateAvailableItems]] = {
    val pubSub = PubSub.get(context.system)
    pubSub.topic[HydrateAvailableItems]("available-items")
  }
}
