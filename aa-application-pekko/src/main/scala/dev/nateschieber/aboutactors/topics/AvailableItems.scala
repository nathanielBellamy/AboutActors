package dev.nateschieber.aboutactors.topics

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.pattern.StatusReply
import dev.nateschieber.aboutactors.{AbtActMessage, HydrateAvailableItems}

object AvailableItems {

  def getTopic(
                context: ActorContext[AbtActMessage] | ActorContext[AbtActMessage | StatusReply[AbtActMessage]]
              ): ActorRef[Topic.Command[HydrateAvailableItems]] = {
    context.spawn(Topic[HydrateAvailableItems]("available-items"), "AvailableItemsTopic")
  }

}
