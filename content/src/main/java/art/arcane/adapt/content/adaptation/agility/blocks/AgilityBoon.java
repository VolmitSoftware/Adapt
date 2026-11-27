package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.agility.AgilityRollLanding;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Jump boost, and any fall damage taken while it lasts is cancelled with Roll Landing's roll. */
public class AgilityBoon extends BoonBlock<AgilityBoon.Config> {
  private static final Id JUMP_BOOST = Id.minecraft("jump_boost");
  private static final int ROLL_DUST = 8;

  private final Map<UUID, Long> safeUntil = playerState();

  public AgilityBoon() {
    super(SkillAgility.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("slime_ball"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    long durationMillis = getConfig().durationMillis;
    int ticks = (int) (durationMillis / 50L);
    int amplifier = (int) Math.round(use.scaled(getConfig().amplifierAtFirstLevel, getConfig().amplifierAtTopLevel));
    if (ticks <= 0 || !player.addEffect(EffectInstance.of(JUMP_BOOST, ticks, Math.max(0, amplifier)))) {
      return Outcome.MISSED;
    }
    safeUntil.put(player.uuid(), System.currentTimeMillis() + durationMillis);
    return Outcome.lasting(player, ticks);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || !(e.entity() instanceof PlayerRef player)) {
      return;
    }
    Long until = safeUntil.get(player.uuid());
    if (until == null) {
      return;
    }
    if (System.currentTimeMillis() > until) {
      safeUntil.remove(player.uuid(), until);
      return;
    }
    e.setCancelled(true);
    player.setFallDistance(0F);
    AgilityRollLanding.rollFx(this, player.location(), ROLL_DUST);
  }

  @ConfigDescription("Jump boost and a safe landing for 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the jump boost and the safe landing last, in milliseconds.", impact = "Higher values keep both longer.")
    long durationMillis = 4_000L;
    @ConfigDoc(value = "Jump boost amplifier at level 1; 0 is Jump Boost I.", impact = "Higher values jump higher.")
    double amplifierAtFirstLevel = 0D;
    @ConfigDoc(value = "Jump boost amplifier at the top level.", impact = "Higher values jump higher.")
    double amplifierAtTopLevel = 1D;
  }
}
