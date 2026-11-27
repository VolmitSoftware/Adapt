package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Holds while the player sneaks and no mob nearby targets them. Asked for a recent window, it also holds when the
 * player was seen sneaking unhunted within it, sampled as they move and when they stand up.
 */
public class StealthState extends StateBlock<StealthState.Config> {
  private final Map<UUID, Long> unseenAt = playerState();
  private final Map<UUID, Long> sampledAt = playerState();

  public StealthState() {
    super(SkillStealth.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_boots"));
  }

  /** Whether a mob within {@code radius} has the player as its target. */
  public static boolean hunted(PlayerRef player, double radius) {
    for (EntityRef entity : StealthCore.nearbyEntities(player.location(), radius,
        (EntityRef candidate) -> candidate instanceof MobRef)) {
      LivingRef target = ((MobRef) entity).target();
      if (target != null && target.uuid().equals(player.uuid())) {
        return true;
      }
    }
    return false;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef player = e.player();
    long now = System.currentTimeMillis();
    Long sampled = sampledAt.get(player.uuid());
    if (!player.isSneaking() || (sampled != null && now - sampled < getConfig().sampleMillis)) {
      return;
    }
    sampledAt.put(player.uuid(), now);
    sample(player, now);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (!e.isSneaking() && hasActiveAdaptation(player)) {
      sample(player, System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return player.isSneaking() && !hunted(player, getConfig().huntRadius);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long unseen = unseenAt.get(player.uuid());
    return holds(player) || (unseen != null && System.currentTimeMillis() - unseen <= windowMillis);
  }

  private void sample(PlayerRef player, long now) {
    if (!hunted(player, getConfig().huntRadius)) {
      unseenAt.put(player.uuid(), now);
    }
  }

  @ConfigDescription("You are sneaking and no mob targets you.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Reach in blocks within which a mob targeting you counts.", impact = "Higher values make the State harder to hold.")
    double huntRadius = 32D;
    @ConfigDoc(value = "Least time between two checks while you sneak and move, in milliseconds.", impact = "Lower values track the recent window more closely at more cost.")
    long sampleMillis = 250L;
  }
}
