/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingCounterGuard extends SimpleAdaptation<BlockingCounterGuard.Config> {
  private final Map<UUID, Integer> reservedStackCosts = playerState();
  private final Set<ReflectionKey> pendingReflections = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean acceptingReflections = new AtomicBoolean(true);

  public BlockingCounterGuard() {
    super("blocking-counter-guard");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_BARS);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_counter_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_counter_500", "blocking.counter-guard.damage-reflected", 500, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_counter_max")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingReflections.set(true);
  }

  @Override
  public void unregister() {
    acceptingReflections.set(false);
    pendingReflections.clear();
    reservedStackCosts.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxStacks(level), 1);
    statLore(v, Form.pc(getReflectChance(level), 0), 2);
    statLore(v, Form.f(getReflectDamage(level)), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (consumeReflection(e.getDamager(), e.getEntity())) {
      return;
    }

    if (!(e.getEntity() instanceof Player defender)) {
      return;
    }

    if (!acceptingReflections.get() || !hasActiveAdaptation(defender) || !hasShield(defender)) {
      return;
    }

    int level = getActiveLevel(defender);
    int maxStacks = getMaxStacks(level);
    int stacks = getStorageInt(defender, "counterStacks", 0);

    if (defender.isBlocking()) {
      int before = stacks;
      stacks = Math.min(maxStacks, stacks + 1);
      setStorage(defender, "counterStacks", stacks);
      if (stacks != before) {
        boolean nowMaxed = stacks >= maxStacks;
        boolean wasMaxed = getStorageInt(defender, "counterMaxed", 0) == 1;
        stackGainCue(defender, stacks, maxStacks, nowMaxed);
        if (nowMaxed && !wasMaxed) {
          setStorage(defender, "counterMaxed", 1);
          fx(defender.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
              .sound(Sound.BLOCK_BEACON_ACTIVATE, 0.3F, 1.8F);
        }
      }
    }

    UUID defenderId = defender.getUniqueId();
    int availableStacks = availableStacks(
        stacks,
        reservedStackCosts.getOrDefault(defenderId, 0)
    );
    if (availableStacks <= 0 || !M.r(getReflectChance(level))) {
      return;
    }

    Entity source = e.getDamager();
    if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
      source = shooter;
    }

    if (!(source instanceof LivingEntity attacker)) {
      return;
    }

    int stackCost = Math.max(0, getConfig().stackCostOnReflect);
    reserveStackCost(defenderId, stackCost);
    double reflected = getReflectDamage(level) + (availableStacks * getConfig().damagePerStack);
    ReflectionOperation operation = new ReflectionOperation(
        defenderId,
        attacker.getUniqueId(),
        reflected,
        stackCost,
        availableStacks >= maxStacks,
        maxStacks
    );
    boolean scheduled = J.runEntity(attacker, () ->
        applyReflectionOnAttacker(defender, attacker, operation));
    if (!scheduled) {
      releaseStackCost(defenderId, stackCost);
    }
  }

  private void stackGainCue(Player defender, int stacks, int maxStacks, boolean maxed) {
    Location chest = defender.getLocation().add(0, 1.0D, 0);
    int points = Math.min(8, Math.max(1, stacks));
    if (maxed) {
      fx(chest, FxPriority.COMBAT)
          .ring(Particles.END_ROD, 0.5D, points, 0)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
      return;
    }
    float pitch = (float) (0.8D + (0.6D * ((double) stacks / maxStacks)));
    fx(chest, FxPriority.COMBAT)
        .ring(Particles.CRIT_MAGIC, 0.5D, points, 0)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, pitch);
  }

  private void applyReflectionOnAttacker(
      Player defender,
      LivingEntity attacker,
      ReflectionOperation operation
  ) {
    if (!acceptingReflections.get() || !attacker.isValid() || attacker.isDead()) {
      releaseStackCost(operation.defenderId(), operation.stackCost());
      return;
    }

    if (!canDamageTarget(defender, attacker)) {
      releaseStackCost(operation.defenderId(), operation.stackCost());
      return;
    }

    ReflectionKey key = new ReflectionKey(operation.defenderId(), operation.attackerId());
    pendingReflections.add(key);
    try {
      attacker.damage(operation.reflectedDamage(), defender);
    } catch (Throwable error) {
      Adapt.error("Counter Guard could not reflect damage from "
          + operation.defenderId() + " to " + operation.attackerId() + ".");
      error.printStackTrace();
      releaseStackCost(operation.defenderId(), operation.stackCost());
      return;
    } finally {
      pendingReflections.remove(key);
    }

    Location attackerLocation = attacker.getLocation().clone();
    boolean scheduled = J.runEntity(defender, () ->
        completeReflectionOnDefender(defender, attackerLocation, operation));
    if (!scheduled) {
      releaseStackCost(operation.defenderId(), operation.stackCost());
    }
  }

  private void completeReflectionOnDefender(
      Player defender,
      Location attackerLocation,
      ReflectionOperation operation
  ) {
    releaseStackCost(operation.defenderId(), operation.stackCost());
    if (!acceptingReflections.get() || !defender.isOnline() || defender.isDead()) {
      return;
    }

    int currentStacks = getStorageInt(defender, "counterStacks", 0);
    int newStacks = spentStacks(currentStacks, operation.stackCost());
    setStorage(defender, "counterStacks", newStacks);
    setStorage(defender, "counterMaxed", newStacks >= operation.maxStacks() ? 1 : 0);
    if (operation.maxed()) {
      grantOnce(defender, "challenge_blocking_counter_max");
    }
    reflectDischarge(defender, attackerLocation);
    xp(defender, operation.reflectedDamage() * getConfig().xpPerReflectedDamage);
    addStat(
        defender,
        "blocking.counter-guard.damage-reflected",
        operation.reflectedDamage()
    );
  }

  private boolean consumeReflection(Entity damager, Entity damaged) {
    return pendingReflections.remove(new ReflectionKey(
        damager.getUniqueId(),
        damaged.getUniqueId()
    ));
  }

  private void reserveStackCost(UUID defenderId, int cost) {
    if (cost > 0) {
      reservedStackCosts.merge(defenderId, cost, Integer::sum);
    }
  }

  private void releaseStackCost(UUID defenderId, int cost) {
    if (cost <= 0) {
      return;
    }
    reservedStackCosts.computeIfPresent(defenderId, (unused, reserved) -> {
      int remaining = reserved - cost;
      return remaining > 0 ? remaining : null;
    });
  }

  static int availableStacks(int stacks, int reserved) {
    return Math.max(0, stacks - Math.max(0, reserved));
  }

  static int spentStacks(int currentStacks, int cost) {
    return Math.max(0, currentStacks - Math.max(0, cost));
  }

  private void reflectDischarge(Player defender, Location attackerLocation) {
    Location from = defender.getEyeLocation();
    Location to = attackerLocation.add(0, 1.0D, 0);
    if (Objects.equals(from.getWorld(), to.getWorld())) {
      int points = (int) Math.min(8, Math.max(3, from.distance(to) * 2));
      fx(from, FxPriority.COMBAT)
          .line(Particles.CRIT_MAGIC, to.getX(), to.getY(), to.getZ(), points);
    } else {
      fx(from, FxPriority.COMBAT).burst(Particles.CRIT_MAGIC, 3, 0.2D);
    }
    fx(to, FxPriority.COMBAT)
        .burst(Particles.END_ROD, 6, 0.2D)
        .chord(Sound.ITEM_TRIDENT_RETURN, 0.7F, 1.3F, Sound.ENTITY_ARROW_HIT, 0.5F, 0.9F);
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
  }

  private double getReflectChance(int level) {
    return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
  }

  private int getMaxStacks(int level) {
    return Math.max(1, (int) Math.round(getConfig().baseStacks + (getLevelPercent(level) * getConfig().stackFactor)));
  }

  private double getReflectDamage(int level) {
    return getConfig().baseReflectDamage + (getLevelPercent(level) * getConfig().reflectDamageFactor);
  }

  record ReflectionKey(UUID defenderId, UUID attackerId) {
  }

  private record ReflectionOperation(
      UUID defenderId,
      UUID attackerId,
      double reflectedDamage,
      int stackCost,
      boolean maxed,
      int maxStacks
  ) {
  }


  @ConfigDescription("Blocking builds retaliation stacks that reflect damage back to attackers.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Stacks for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseStacks = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stackFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceFactor = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectChance = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Reflect Damage for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseReflectDamage = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Damage Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectDamageFactor = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerStack = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Cost On Reflect for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int stackCostOnReflect = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Reflected Damage for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerReflectedDamage = 5.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.75;
      initialCost = 4;
    }
  }
}
