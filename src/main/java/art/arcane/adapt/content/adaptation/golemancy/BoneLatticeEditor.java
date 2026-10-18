package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.gui.GolemGuiItems;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class BoneLatticeEditor {
  private final GolemRoster roster;
  private final Runnable saveRoster;
  private final int slots;
  private final Set<GolemRuneType> allowed;

  BoneLatticeEditor(GolemRoster roster, Runnable saveRoster, int slots, Set<GolemRuneType> allowed) {
    this.roster = Objects.requireNonNull(roster);
    this.saveRoster = Objects.requireNonNull(saveRoster);
    this.slots = slots;
    this.allowed = Objects.requireNonNull(allowed);
  }

  Outcome insert(Player player) {
    ItemStack cursor = player.getItemOnCursor();
    Optional<GolemRune> held = GolemItems.runeOf(cursor);
    if (held.isEmpty()) {
      return new Outcome(Result.NO_RUNE, null);
    }
    GolemRune rune = held.get();
    Optional<BoneFrameRules.Refusal> refusal = BoneFrameRules.refuse(roster.lattice(), rune, slots, allowed);
    if (refusal.isPresent()) {
      return new Outcome(refused(refusal.get()), rune);
    }
    cursor.setAmount(cursor.getAmount() - 1);
    player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
    player.saveData();
    if (!roster.addLattice(rune)) {
      GolemGuiItems.toPlayer(player, GolemItems.rune(rune), player.getLocation());
      return new Outcome(Result.DUPLICATE, rune);
    }
    saveRoster.run();
    return new Outcome(Result.SLOTTED, rune);
  }

  Outcome take(Player player, int index, GolemRune rune) {
    if (!roster.removeLattice(index, rune)) {
      return new Outcome(Result.STALE, rune);
    }
    saveRoster.run();
    GolemGuiItems.toPlayer(player, GolemItems.rune(rune), player.getLocation());
    J.runEntity(player, player::saveData);
    return new Outcome(Result.TAKEN, rune);
  }

  private static Result refused(BoneFrameRules.Refusal refusal) {
    return switch (refusal) {
      case ASPECT -> Result.ASPECT;
      case DUPLICATE -> Result.DUPLICATE;
      case NO_SLOT -> Result.NO_SLOT;
    };
  }

  enum Result {
    SLOTTED,
    TAKEN,
    NO_RUNE,
    ASPECT,
    DUPLICATE,
    NO_SLOT,
    STALE
  }

  record Outcome(Result result, GolemRune rune) {
  }
}
