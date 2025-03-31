package dev.nateschieber.aboutactors.dto

import dev.nateschieber.aboutactors.CborSerializable

final case class InventoryAvailableItems(itemIds: List[String]) extends CborSerializable
