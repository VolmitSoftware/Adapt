package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Wards the player: the next hit they take before the ward runs out is cut down, then the ward is gone. */
public class BlockingWorkStrike extends StrikeBlock<BlockingWorkStrike.Config> {
  private final Map<UUID, Ward> wards = playerState();

  public BlockingWorkStrike() {
    super(SkillBlocking.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_chestplate"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    double kept = 1D - use.scaled(getConfig().reductionAtFirstLevel, getConfig().reductionAtTopLevel);
    wards.put(player.uuid(), new Ward(kept, System.currentTimeMillis() + getConfig().wardMillis));
    return Outcome.lasting(player, (int) (getConfig().wardMillis / 50L));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef player) || e.damage() <= 0D) {
      return;
    }
    Ward ward = wards.remove(player.uuid());
    if (ward == null || System.currentTimeMillis() > ward.expiresAt()) {
      return;
    }
    e.setDamage(e.damage() * Math.max(0D, ward.kept()));
  }

  private record Ward(double kept, long expiresAt) {
  }

  @ConfigDescription("The next hit you take within 10 s is halved.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the ward waits for a hit, in milliseconds.", impact = "Higher values keep the ward up longer.")
    long wardMillis = 10_000L;
    @ConfigDoc(value = "Share of the warded hit taken off at level 1.", impact = "Higher values soften the hit more.")
    double reductionAtFirstLevel = 0.5D;
    @ConfigDoc(value = "Share of the warded hit taken off at the top level.", impact = "Higher values soften the hit more.")
    double reductionAtTopLevel = 0.65D;
  }
}
