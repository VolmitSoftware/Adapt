package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Fires when the player enchants at a table, or picks up enough vanilla XP at once. XP pickups fire at most once per
 * cooldown, and count as farmed while a spawner, bred or otherwise farmed mob dropped XP nearby moments before, since
 * the pickup itself does not say where its orbs came from.
 */
public class EnchantingTrigger extends TriggerBlock<EnchantingTrigger.Config> {
  private static final int MAX_FARMED_DEATHS = 512;
  private final Map<UUID, Long> lastPickup = playerState();
  private final Deque<FarmedDeath> farmedDeaths = new ConcurrentLinkedDeque<>();

  public EnchantingTrigger() {
    super(SkillEnchanting.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("enchanting_table"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EnchantItemEvent e) {
    PlayerRef player = e.player();
    if (hasActiveAdaptation(player)) {
      fire(player, null, e.table().center(), false);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    PlayerRef killer = e.killer();
    if (e.droppedExp() <= 0 || victim instanceof PlayerRef
        || !(killer == null ? MobOrigin.isFarmed(victim) : Mutations.farmed(killer, victim))) {
      return;
    }
    farmedDeaths.addLast(new FarmedDeath(victim.location(), System.currentTimeMillis()));
    while (farmedDeaths.size() > MAX_FARMED_DEATHS) {
      farmedDeaths.pollFirst();
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    PlayerRef player = e.player();
    if (e.amount() < getConfig().experience || !hasActiveAdaptation(player)) {
      return;
    }
    long now = System.currentTimeMillis();
    Long last = lastPickup.get(player.uuid());
    if (last != null && now - last < getConfig().experienceCooldownMillis) {
      return;
    }
    lastPickup.put(player.uuid(), now);
    fire(player, null, player.location(), nearFarmedDeath(player.location(), now));
  }

  private boolean nearFarmedDeath(Loc at, long now) {
    long since = now - getConfig().farmWindowMillis;
    while (!farmedDeaths.isEmpty() && farmedDeaths.peekFirst().at() < since) {
      farmedDeaths.pollFirst();
    }
    double radiusSquared = getConfig().farmRadius * getConfig().farmRadius;
    for (FarmedDeath death : farmedDeaths) {
      if (death.where().sameWorld(at) && death.where().distanceSquared(at) <= radiusSquared) {
        return true;
      }
    }
    return false;
  }

  private record FarmedDeath(Loc where, long at) {
  }

  @ConfigDescription("Enchant an item, or collect 10 vanilla XP at once.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Vanilla experience points that must arrive at once.", impact = "Higher values need bigger pickups.")
    int experience = 10;
    @ConfigDoc(value = "Shortest time between two XP pickups that fire, in milliseconds.", impact = "Higher values let XP fire less often.")
    long experienceCooldownMillis = 30_000L;
    @ConfigDoc(value = "Distance from a farmed mob's death within which picked-up XP counts as farmed, in blocks.", impact = "Higher values mark pickups farther from a mob farm as farmed.")
    double farmRadius = 16D;
    @ConfigDoc(value = "How long after a farmed mob's death picked-up XP counts as farmed, in milliseconds.", impact = "Higher values mark later pickups as farmed.")
    long farmWindowMillis = 15_000L;
  }
}
