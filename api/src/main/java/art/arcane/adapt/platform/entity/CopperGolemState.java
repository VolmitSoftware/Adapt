package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/** Copper golem interaction state, in vanilla order. */
@ApiStatus.AvailableSince("1.0.0")
public enum CopperGolemState {
  IDLE,
  GETTING_ITEM,
  GETTING_NO_ITEM,
  DROPPING_ITEM,
  DROPPING_NO_ITEM
}
