package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Timed knockback resistance through the attribute service Bastion Stance braces with, and the same share taken off
 * projectile damage while it lasts.
 */
public class BlockingBoon extends BoonBlock<BlockingBoon.Config> {
  private static final String SLOT = "knockback";
  private final Map<UUID, Guard> guards = playerState();

  public BlockingBoon() {
    super(SkillBlocking.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_chestplate"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (getConfig().durationMillis / 50L);
    double resistance = use.scaled(getConfig().resistanceAtFirstLevel, getConfig().resistanceAtTopLevel);
    AdaptAttributeService.get().applyTimed(player, getId().toString(), SLOT, Attributes.KNOCKBACK_RESISTANCE, resistance,
        AttributeOperation.ADD_VALUE, ticks);
    guards.put(player.uuid(), new Guard(resistance, System.currentTimeMillis() + getConfig().durationMillis));
    return Outcome.lasting(player, ticks);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef player) || !(e.damager() instanceof ProjectileRef)) {
      return;
    }
    Guard guard = guards.get(player.uuid());
    if (guard == null) {
      return;
    }
    if (System.currentTimeMillis() > guard.expiresAt()) {
      guards.remove(player.uuid());
      return;
    }
    e.setDamage(e.damage() * (1D - Math.min(1D, guard.resistance())));
  }

  private record Guard(double resistance, long expiresAt) {
  }

  @ConfigDescription("30% knockback and projectile resistance for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the resistance lasts, in milliseconds.", impact = "Higher values keep the resistance longer.")
    long durationMillis = 5_000L;
    @ConfigDoc(value = "Knockback resistance and projectile damage taken off at level 1, as a fraction.", impact = "Higher values shrug off more.")
    double resistanceAtFirstLevel = 0.3D;
    @ConfigDoc(value = "Knockback resistance and projectile damage taken off at the top level, as a fraction.", impact = "Higher values shrug off more.")
    double resistanceAtTopLevel = 0.45D;
  }
}
