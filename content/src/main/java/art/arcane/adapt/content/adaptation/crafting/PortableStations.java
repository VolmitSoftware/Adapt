package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/** Opens a workstation without placing it, as Portable Tables does, leaving the effects to the owner. */
public final class PortableStations {
  private static final long MILLIS_PER_TICK = 50L;

  private final Adaptation<?> owner;

  public PortableStations(Adaptation<?> owner) {
    this.owner = owner;
  }

  /** What the owner shows when a station is refused for hunger or opens. */
  public interface Feedback {
    void hungry(Loc handTip);

    void opened(Loc handTip);
  }

  public static CraftingStation station(Id type) {
    if (!type.isMinecraft()) {
      return null;
    }
    return switch (type.path()) {
      case "crafting_table" -> CraftingStation.WORKBENCH;
      case "grindstone" -> CraftingStation.GRINDSTONE;
      case "anvil" -> CraftingStation.ANVIL;
      case "stonecutter" -> CraftingStation.STONECUTTER;
      case "cartography_table" -> CraftingStation.CARTOGRAPHY;
      case "loom" -> CraftingStation.LOOM;
      default -> null;
    };
  }

  /**
   * Opens {@code station}, taking {@code hungerCost} food and putting {@code item} on a {@code cooldownTicks} cooldown.
   * Returns false when the player is too hungry.
   */
  public boolean open(PlayerRef p, Id item, CraftingStation station, int cooldownTicks, int hungerCost, Feedback feedback) {
    Loc handTip = p.eyeLocation().add(p.location().direction().multiply(0.8D));
    if (hungerCost > 0 && p.foodLevel() < hungerCost) {
      feedback.hungry(handTip);
      return false;
    }

    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    }

    p.setCooldown(item, cooldownTicks);
    HudStatus.cooldown(p, owner, cooldownTicks * MILLIS_PER_TICK);
    feedback.opened(handTip);
    p.openStation(station);
    return true;
  }
}
