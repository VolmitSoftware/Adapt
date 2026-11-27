package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.StealthSmoke;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every mob around the player drops its aggression on them, as in a Smoke Pellet cloud, and cannot take the player
 * as a target again until the time runs out.
 */
public class StealthFightStrike extends StrikeBlock<StealthFightStrike.Config> {
  private final Map<UUID, Lost> lost = playerState();

  public StealthFightStrike() {
    super(SkillStealth.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("gunpowder"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Set<UUID> mobs = shakeOff(player, getConfig().radius);
    if (mobs.isEmpty()) {
      return Outcome.MISSED;
    }
    int ticks = (int) (use.scaled(getConfig().millisAtFirstLevel, getConfig().millisAtTopLevel) / 50D);
    lost.put(player.uuid(), new Lost(mobs, System.currentTimeMillis() + (ticks * 50L)));
    fx(player.location().add(0D, 1D, 0D), FxPriority.COMBAT).burst(Particles.SMOKE, 12, 0.6D);
    return Outcome.lasting(player, ticks);
  }

  /** Mobs within {@code radius} that hunt the player drop their aggression, as in a Smoke Pellet cloud; returns every mob there. */
  public static Set<UUID> shakeOff(PlayerRef player, double radius) {
    Set<UUID> mobs = new HashSet<>();
    for (EntityRef entity : StealthCore.nearbyEntities(player.location(), radius,
        (EntityRef candidate) -> candidate instanceof MobRef)) {
      MobRef mob = (MobRef) entity;
      mobs.add(mob.uuid());
      AdaptApi.scheduler().runEntity(mob, () -> {
        LivingRef current = mob.target();
        if (current != null && current.uuid().equals(player.uuid())) {
          StealthSmoke.clearAggroOwned(mob, player);
        }
      });
    }
    return mobs;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef player)) {
      return;
    }
    Lost entry = lost.get(player.uuid());
    if (entry == null) {
      return;
    }
    if (System.currentTimeMillis() > entry.until()) {
      lost.remove(player.uuid(), entry);
      return;
    }
    if (entry.mobs().contains(e.entity().uuid())) {
      e.setCancelled(true);
    }
  }

  private record Lost(Set<UUID> mobs, long until) {
  }

  @ConfigDescription("Mobs within 8 blocks lose track of you for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Reach around you in blocks.", impact = "Higher values shake off mobs farther away.")
    double radius = 8D;
    @ConfigDoc(value = "How long the mobs cannot find you at level 1, in milliseconds.", impact = "Higher values keep you lost longer.")
    double millisAtFirstLevel = 3_000D;
    @ConfigDoc(value = "How long the mobs cannot find you at the top level, in milliseconds.", impact = "Higher values keep you lost longer.")
    double millisAtTopLevel = 4_000D;
  }
}
