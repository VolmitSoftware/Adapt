package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMastery;

import java.util.Optional;
import java.util.UUID;

/** Golem-side parts of {@link Mutations}: owners who may be away, and rune perks an upgrade adds to every golem. */
public final class GolemLoans {
  private GolemLoans() {
  }

  /** An upgrade that adds rune perks to every golem of the owners who hold it. */
  public interface RunePerks {
    /** The perks for {@code handle}'s owner, or for {@code owner} when no golem is at hand, on a masterwork or not. */
    GolemMastery.Perks runePerks(UUID owner, GolemHandle handle, boolean masterwork);
  }

  /** {@link Mutations#landed} for an owner who is online. */
  public static void landed(Adaptation<?> original, UUID owner) {
    if (Mutations.fused(original)) {
      AdaptApi.players().find(owner).ifPresent((PlayerProfile player) -> Mutations.landed(original, player.player()));
    }
  }

  /** {@link Mutations#strength} for an owner who is online; 1 otherwise. */
  public static double strength(Adaptation<?> original, UUID owner) {
    Optional<PlayerProfile> player = Mutations.fused(original) ? AdaptApi.players().find(owner) : Optional.empty();
    return player.map((PlayerProfile profile) -> Mutations.strength(original, profile.player())).orElse(1D);
  }

  /** {@code base} with the rune perks every upgrade of the skill adds for the owner. */
  public static GolemMastery.Perks runePerks(Adaptation<?> runic, UUID owner, GolemHandle handle, boolean masterwork,
                                             GolemMastery.Perks base) {
    if (runic.getSkill() == null) {
      return base;
    }
    GolemMastery.Perks merged = base;
    for (Adaptation<?> upgrade : runic.getSkill().getUpgrades()) {
      if (upgrade instanceof RunePerks perks) {
        merged = merge(merged, perks.runePerks(owner, handle, masterwork));
      }
    }
    return merged;
  }

  static GolemMastery.Perks merge(GolemMastery.Perks a, GolemMastery.Perks b) {
    if (b == GolemMastery.Perks.NONE) {
      return a;
    }
    return new GolemMastery.Perks(a.extraRuneSlots() + b.extraRuneSlots(), a.dualCore() || b.dualCore(),
        a.tierUp() || b.tierUp(), Math.max(a.tierThreeBoost(), b.tierThreeBoost()));
  }
}
