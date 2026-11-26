package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.insight.InsightTier;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.discovery.InsightLens;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/**
 * Insight's readout of the target reaches the player's action bar, and the player's next melee hit on that target
 * deals more damage.
 */
public class DiscoveryFightStrike extends StrikeBlock<DiscoveryFightStrike.Config> {
  private final Map<UUID, Weakness> weaknesses = playerState();

  public DiscoveryFightStrike() {
    super("block-discovery-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)) {
      return Outcome.MISSED;
    }
    player.sendActionBar(InsightLens.actionBar(player, target, InsightTier.of(use.power(), true)));
    weaknesses.put(player.uuid(), new Weakness(target.uuid(),
        use.scaled(getConfig().bonusAtFirstLevel, getConfig().bonusAtTopLevel),
        System.currentTimeMillis() + getConfig().weaknessMillis));
    fx(target.location().add(0D, target.height() + 0.2D, 0D), FxPriority.COMBAT)
        .particle(Particles.ENCHANTMENT_TABLE, 12, 0D, 0D, 0D, 0.4D, 0.2D);
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef player)) {
      return;
    }
    Weakness weakness = weaknesses.get(player.uuid());
    if (weakness == null || !weakness.target().equals(e.entity().uuid())) {
      return;
    }
    weaknesses.remove(player.uuid());
    if (System.currentTimeMillis() <= weakness.until() && hasActiveAdaptation(player)) {
      e.setDamage(e.damage() * (1D + weakness.bonus()));
    }
  }

  private record Weakness(UUID target, double bonus, long until) {
  }

  @ConfigDescription("The target's stats are revealed and your next hit on it deals 20% more.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Extra damage of the next hit at level 1, as a fraction.", impact = "Higher values hit harder.")
    double bonusAtFirstLevel = 0.2D;
    @ConfigDoc(value = "Extra damage of the next hit at the top level, as a fraction.", impact = "Higher values hit harder.")
    double bonusAtTopLevel = 0.35D;
    @ConfigDoc(value = "How long the next hit stays marked, in milliseconds.", impact = "Higher values keep the opening longer.")
    long weaknessMillis = 30_000L;
  }
}
