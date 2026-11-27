package art.arcane.adapt.api.integration;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

/**
 * A block of a HiddenOre vein and the item it reveals as.
 *
 * @param location where the hidden block is
 * @param display  the item it reveals as, such as {@code minecraft:diamond_ore}
 */
@ApiStatus.Experimental
public record HiddenVein(Loc location, Id display) {
}
