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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeThrowingAxe extends SimpleAdaptation<AxeThrowingAxe.Config> {
  private static final int MAX_IN_FLIGHT = 512;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, ThrownAxe> inFlight = new ConcurrentHashMap<>();

  public AxeThrowingAxe() {
    super("axe-throwing-axe");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_AXE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_AXE)
        .key("challenge_axe_throw_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_AXE)
            .key("challenge_axe_throw_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_axe_throw_500", "axe.throwing-axe.hits", 500, 500);
    registerMilestone("challenge_axe_throw_5k", "axe.throwing-axe.hits", 5000, 1800);
  }

  @Override
  public void addStats(int level, Element v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getDamageMultiplier(f), 2), 1);
    statLore(v, C.GREEN, "+ ", Form.f(getThrowSpeed(f), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMs(f), 1), 3);
    if (returnsAtLevel(level)) {
      v.addLore(C.AQUA + "" + AdaptLanguage.text(AxeMessages.THROWING_AXE_LORE4));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.LEFT_CLICK_AIR) {
      return;
    }
    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    withAdaptedPlayer(p, () -> throwAxe(p));
  }

  private void throwAxe(Player p) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isAxe(hand)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    long cooldown = (long) getCooldownMs(getLevelPercent(level));
    if (!cooldowns.isReady(id, cooldown)) {
      return;
    }

    if (inFlight.size() >= MAX_IN_FLIGHT) {
      return;
    }

    ItemStack thrown = hand.clone();
    thrown.setAmount(1);
    boolean broken = applyThrowDurability(thrown, getConfig().durabilityCost);
    double damage = getThrowDamage(hand.getType(), getLevelPercent(level));
    boolean returns = returnsAtLevel(level);

    if (!payItemCost(p, "throw", new ItemStack(hand.getType()), 1, () -> {
      if (hand.getAmount() > 1) {
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
      } else {
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      }

      return true;
    })) {
      return;
    }

    cooldowns.mark(id);
    p.setCooldown(thrown.getType(), (int) Math.max(1L, cooldown / 50L));

    Location eye = p.getEyeLocation();
    Vector dir = eye.getDirection().clone().normalize();
    double speed = getThrowSpeed(getLevelPercent(level));
    Location spawn = eye.clone().add(dir.clone().multiply(0.6D));
    Snowball ball = p.getWorld().spawn(spawn, Snowball.class, s -> {
      s.setItem(thrown.clone());
      s.setShooter(p);
      s.setVelocity(dir.clone().multiply(speed));
    });

    UUID ballId = ball.getUniqueId();
    inFlight.put(ballId, new ThrownAxe(id, thrown, damage, returns, broken));
    addStat(p, "axe.throwing-axe.thrown", 1);

    fx(spawn, FxPriority.GAMEPLAY)
        .trail(Particles.CRIT_MAGIC, dir.getX(), dir.getY(), dir.getZ(), 1.3D, 6)
        .chord(Sound.ITEM_TRIDENT_THROW, 0.8F, 1.2F, Sound.ITEM_AXE_STRIP, 0.4F, 0.7F);
    timeline(ball)
        .duration(getConfig().maxFlightTicks)
        .priority(FxPriority.TRAIL)
        .cullRadius(40)
        .frame((f, tick, progress) -> {
          f.particle(Particles.CRIT_MAGIC, 2, 0D, 0D, 0D, 0.05D, 0.0D);
          if ((tick & 1) == 0) {
            f.particle(Particles.SMOKE, 1, 0D, 0D, 0D, 0.02D, 0.0D);
          }
        })
        .start();

    boolean scheduled = FoliaScheduler.runEntity(
        Adapt.instance,
        ball,
        () -> resolveThrow(ballId, ball, null),
        getConfig().maxFlightTicks,
        () -> retireThrow(ballId, ball)
    );
    if (!scheduled) {
      resolveThrow(ballId, ball, null);
    }
  }

  private void retireThrow(UUID ballId, Snowball ball) {
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null || thrown.broken()) {
      return;
    }

    Player owner = Bukkit.getPlayer(thrown.ownerId());
    if (owner != null && owner.isOnline()) {
      J.runEntity(owner, () -> returnAxe(owner, thrown.axe()));
      return;
    }

    dropAxe(lastKnownLocation(ball), thrown.axe());
  }

  private Location lastKnownLocation(Snowball ball) {
    try {
      return ball.getLocation();
    } catch (Throwable ignored) {
      return null;
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Snowball ball) || !inFlight.containsKey(ball.getUniqueId())) {
      return;
    }
    resolveThrow(ball.getUniqueId(), ball, e.getHitEntity());
  }

  private void resolveThrow(UUID ballId, Snowball ball, Entity hit) {
    ThrownAxe thrown = inFlight.remove(ballId);
    if (thrown == null) {
      return;
    }

    Location impact = ball.isValid() ? ball.getLocation().clone() : ball.getLocation();
    Player owner = Bukkit.getPlayer(thrown.ownerId());

    if (hit instanceof LivingEntity target && owner != null && owner.isOnline() && canDamageTarget(owner, target)) {
      J.runEntity(target, () -> {
        if (!target.isValid() || target.isDead()) {
          return;
        }
        target.damage(thrown.damage(), owner);
        fx(target.getLocation().add(0D, target.getHeight() * 0.6D, 0D), FxPriority.COMBAT)
            .burst(Particles.CRIT_MAGIC, 8, 0.35D)
            .chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.0F, Sound.ITEM_AXE_STRIP, 0.4F, 0.8F);
      });
      J.runEntity(owner, () -> rewardHit(owner));
    }

    if (ball.isValid()) {
      ball.remove();
    }

    if (thrown.broken()) {
      fx(impact, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.25D)
          .sound(Sound.ENTITY_ITEM_BREAK, 0.8F, 0.9F);
      return;
    }

    if (thrown.returns() && owner != null && owner.isOnline()) {
      J.runEntity(owner, () -> returnAxe(owner, thrown.axe()));
    } else {
      dropAxe(impact, thrown.axe());
    }
  }

  private void rewardHit(Player owner) {
    if (!owner.isOnline()) {
      return;
    }
    addStat(owner, "axe.throwing-axe.hits", 1);
    xp(owner, getConfig().xpPerHit);
  }

  private void returnAxe(Player owner, ItemStack axe) {
    if (!owner.isOnline()) {
      dropAxe(owner.getLocation(), axe);
      return;
    }

    ItemStack main = owner.getInventory().getItemInMainHand();
    if (main == null || main.getType() == Material.AIR) {
      owner.getInventory().setItemInMainHand(axe);
    } else if (!owner.getInventory().addItem(axe).isEmpty()) {
      dropAxe(owner.getLocation(), axe);
    }
    fx(owner.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.ENCHANTMENT_TABLE, 8, 0.3D)
        .chord(Sound.ITEM_TRIDENT_RETURN, 0.7F, 1.1F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
  }

  private void dropAxe(Location impact, ItemStack axe) {
    if (impact == null || impact.getWorld() == null) {
      return;
    }
    J.runAt(impact, () -> impact.getWorld().dropItem(impact, axe));
  }

  private boolean applyThrowDurability(ItemStack stack, int amount) {
    ItemMeta meta = stack.getItemMeta();
    if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
      return false;
    }

    int next = damageable.getDamage() + amount;
    if (next > stack.getType().getMaxDurability()) {
      return true;
    }
    damageable.setDamage(next);
    stack.setItemMeta(meta);
    return false;
  }

  private boolean returnsAtLevel(int level) {
    return getLevelPercent(level) >= getConfig().returnUnlockLevelPercent;
  }

  private double getThrowSpeed(double factor) {
    return getConfig().throwSpeedBase + (factor * getConfig().throwSpeedFactor);
  }

  private double getDamageMultiplier(double factor) {
    return getConfig().damageMultiplierBase + (factor * getConfig().damageMultiplierFactor);
  }

  private double getThrowDamage(Material axe, double factor) {
    return meleeBaseDamage(axe) * getDamageMultiplier(factor);
  }

  private double getCooldownMs(double factor) {
    return Math.max(250D, getConfig().cooldownMsBase - (factor * getConfig().cooldownMsLevelReduction));
  }

  static double meleeBaseDamage(Material axe) {
    return switch (axe) {
      case NETHERITE_AXE -> 10D;
      case DIAMOND_AXE, IRON_AXE, STONE_AXE -> 9D;
      case WOODEN_AXE, GOLDEN_AXE -> 7D;
      default -> 7D;
    };
  }

  @Override
  public void unregister() {
    inFlight.clear();
    super.unregister();
  }

  private record ThrownAxe(UUID ownerId, ItemStack axe, double damage, boolean returns, boolean broken) {
  }

  @ConfigDescription("Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of the axe's melee damage dealt on a throw hit.", impact = "Higher values make thrown axes hit harder at every level.")
    double damageMultiplierBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra melee-damage fraction added by leveling this adaptation.", impact = "Higher values reward leveling with stronger throws.")
    double damageMultiplierFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base flight velocity of a thrown axe in blocks per tick.", impact = "Higher values make thrown axes travel faster and flatter.")
    double throwSpeedBase = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra flight velocity added by leveling this adaptation.", impact = "Higher values increase throw speed scaling with level.")
    double throwSpeedFactor = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base throw cooldown in milliseconds before level scaling.", impact = "Higher values slow how often axes can be thrown.")
    double cooldownMsBase = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds of cooldown removed at maximum level.", impact = "Higher values let higher levels throw more frequently.")
    double cooldownMsLevelReduction = 600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability spent from the axe on each throw.", impact = "Higher values wear thrown axes out faster.")
    int durabilityCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a thrown axe stays airborne before it is recovered automatically.", impact = "Higher values let throws travel farther before auto-recovery.")
    int maxFlightTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level progress required before thrown axes return to your hand.", impact = "Lower values unlock the auto-return at an earlier level.")
    double returnUnlockLevelPercent = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per thrown-axe hit on an entity.", impact = "Higher values accelerate skill progression from throwing.")
    double xpPerHit = 6;

    public Config() {
      baseCost = 5;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 5;
    }
  }
}
