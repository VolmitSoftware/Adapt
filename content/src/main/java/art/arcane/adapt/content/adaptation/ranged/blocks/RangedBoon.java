package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.ranged.RangedShots;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * The next bow shot within the window flies as if fully drawn, however briefly it was drawn: its speed is raised to
 * full draw through the shared shot helper and it is a critical arrow.
 */
public class RangedBoon extends BoonBlock<RangedBoon.Config> {
  private final Map<UUID, Long> primedUntil = playerState();

  public RangedBoon() {
    super(SkillRanged.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crossbow"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    primedUntil.put(use.player().uuid(), System.currentTimeMillis() + getConfig().windowMillis);
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityShootBowEvent e) {
    if (!(e.shooter() instanceof PlayerRef player) || !(e.projectile() instanceof ProjectileRef arrow)) {
      return;
    }
    Long until = primedUntil.remove(player.uuid());
    if (until == null || System.currentTimeMillis() > until) {
      return;
    }
    float force = e.force();
    if (force > 0F && force < 1F) {
      RangedShots.quicken(arrow, (1D / force) - 1D);
    }
    arrow.setCritical(true);
  }

  @ConfigDescription("An instant draw on your next shot.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the instant draw waits for a shot, in milliseconds.", impact = "Higher values keep it ready longer.")
    long windowMillis = 10_000L;
  }
}
