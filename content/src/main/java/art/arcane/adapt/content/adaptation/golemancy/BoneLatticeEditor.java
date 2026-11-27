package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.gui.GolemGuiItems;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class BoneLatticeEditor {
  private final PlayerProfile owner;
  private final GolemRoster roster;
  private final int slots;
  private final Set<GolemRuneType> allowed;

  BoneLatticeEditor(PlayerProfile owner, int slots, Set<GolemRuneType> allowed) {
    this.owner = Objects.requireNonNull(owner);
    this.roster = owner.section(GolemRoster.section());
    this.slots = slots;
    this.allowed = Objects.requireNonNull(allowed);
  }

  Outcome insert(PlayerRef player) {
    ItemRef cursor = player.inventory().cursor();
    Optional<GolemRune> held = GolemItems.runeOf(cursor);
    if (held.isEmpty()) {
      return new Outcome(Result.NO_RUNE, null);
    }
    GolemRune rune = held.get();
    Optional<BoneFrameRules.Refusal> refusal = BoneFrameRules.refuse(roster.lattice(), rune, slots, allowed);
    if (refusal.isPresent()) {
      return new Outcome(refused(refusal.get()), rune);
    }
    cursor.setAmount(cursor.amount() - 1);
    player.inventory().setCursor(cursor.amount() > 0 ? cursor : AdaptApi.platform().host().items().empty());
    player.saveData();
    if (!roster.addLattice(rune)) {
      GolemGuiItems.toPlayer(player, GolemItems.rune(rune), player.location());
      return new Outcome(Result.DUPLICATE, rune);
    }
    owner.saveNow();
    return new Outcome(Result.SLOTTED, rune);
  }

  Outcome take(PlayerRef player, int index, GolemRune rune) {
    if (!roster.removeLattice(index, rune)) {
      return new Outcome(Result.STALE, rune);
    }
    owner.saveNow();
    GolemGuiItems.toPlayer(player, GolemItems.rune(rune), player.location());
    AdaptApi.scheduler().runEntity(player, player::saveData);
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
