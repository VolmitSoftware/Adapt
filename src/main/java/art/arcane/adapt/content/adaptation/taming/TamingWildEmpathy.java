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

package art.arcane.adapt.content.adaptation.taming;

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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TamingWildEmpathy extends SimpleAdaptation<TamingWildEmpathy.Config> {
  private static final Map<org.bukkit.entity.EntityType, Set<Material>> TAME_FOODS = buildTameFoods();
  private final Cooldowns angerFxCd = cooldowns();

  private static Map<org.bukkit.entity.EntityType, Set<Material>> buildTameFoods() {
    Map<org.bukkit.entity.EntityType, Set<Material>> foods = new EnumMap<>(org.bukkit.entity.EntityType.class);
    foods.put(org.bukkit.entity.EntityType.WOLF, EnumSet.of(Material.BONE));
    foods.put(org.bukkit.entity.EntityType.CAT, EnumSet.of(Material.COD, Material.SALMON));
    foods.put(org.bukkit.entity.EntityType.OCELOT, EnumSet.of(Material.COD, Material.SALMON));
    foods.put(org.bukkit.entity.EntityType.PARROT, EnumSet.of(
        Material.WHEAT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
        Material.BEETROOT_SEEDS, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD));
    return foods;
  }

  public TamingWildEmpathy() {
    super("tame-wild-empathy");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.wild_empathy");
    setIcon(Material.DANDELION);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DANDELION)
        .key("challenge_taming_empathy_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.OXEYE_DAISY)
            .key("challenge_taming_empathy_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_empathy_100", "taming.wild-empathy.tames", 100, 400);
    registerMilestone("challenge_taming_empathy_1k", "taming.wild-empathy.tames", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getTamingOdds(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.pc(getAngerResistance(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!(e.getRightClicked() instanceof Tameable target) || target.isTamed()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isTameFood(target.getType(), hand.getType())) {
      return;
    }

    if (Math.random() > getTamingOdds(level)) {
      return;
    }

    e.setCancelled(true);
    UUID ownerId = p.getUniqueId();
    Material food = hand.getType();
    J.runEntity(target, () -> forceTame(p, ownerId, target, food));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getTarget() instanceof Player p) || !isNeutral(e.getEntity())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (Math.random() > getAngerResistance(level)) {
      return;
    }

    e.setCancelled(true);
    if (e.getEntity() instanceof Mob mob && mob.getTarget() == p) {
      mob.setTarget(null);
    }
    if (e.getEntity() instanceof Wolf wolf) {
      wolf.setAngry(false);
    }

    addStat(p, "taming.wild-empathy.angers-resisted", 1);
    if (angerFxCd.isReady(p.getUniqueId(), 1500L)) {
      angerFxCd.mark(p.getUniqueId());
      fx(e.getEntity().getLocation().add(0, 0.6, 0), FxPriority.AMBIENT)
          .ring(Particles.VILLAGER_HAPPY, 0.5D, 6, 0.2D)
          .sound(Sound.ENTITY_VILLAGER_YES, 0.35F, 1.4F);
    }
  }

  private void forceTame(Player p, UUID ownerId, Tameable target, Material food) {
    if (!target.isValid() || target.isDead() || target.isTamed()) {
      return;
    }

    EntityTameEvent tameEvent = new EntityTameEvent(target, p);
    Bukkit.getPluginManager().callEvent(tameEvent);
    if (tameEvent.isCancelled()) {
      return;
    }

    target.setTamed(true);
    target.setOwner(p);
    if (target instanceof Wolf wolf) {
      wolf.setAngry(false);
    }

    fx(target, FxPriority.TRANSITION)
        .ring(Particles.VILLAGER_HAPPY, 0.6D, 10, 0.4D)
        .burst(Particle.HEART, 4, 0.2D)
        .chord(Sound.ENTITY_WOLF_PANT, 0.7F, 1.2F, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.6F);

    J.runEntity(p, () -> completeForcedTame(p, food, ownerId));
  }

  private void completeForcedTame(Player p, Material food, UUID ownerId) {
    if (!p.isOnline() || !p.getUniqueId().equals(ownerId)) {
      return;
    }

    consumeOne(p, food);
    addStat(p, "taming.wild-empathy.tames", 1);
    xp(p, getConfig().xpPerTame);
  }

  private void consumeOne(Player p, Material food) {
    ItemStack current = p.getInventory().getItemInMainHand();
    if (current == null || current.getType() != food || current.getAmount() <= 0) {
      return;
    }

    int amount = current.getAmount() - 1;
    if (amount <= 0) {
      p.getInventory().setItemInMainHand(null);
    } else {
      current.setAmount(amount);
      p.getInventory().setItemInMainHand(current);
    }
  }

  private boolean isNeutral(Entity entity) {
    return entity instanceof Wolf
        || entity instanceof Bee
        || entity instanceof PolarBear
        || entity instanceof Llama
        || entity instanceof Panda
        || entity instanceof Goat;
  }

  private boolean isTameFood(org.bukkit.entity.EntityType type, Material material) {
    Set<Material> foods = TAME_FOODS.get(type);
    return foods != null && foods.contains(material);
  }

  private double getTamingOdds(int level) {
    return Math.min(getConfig().maxTamingOdds, getConfig().tamingOddsBase + (getLevelPercent(level) * getConfig().tamingOddsFactor));
  }

  private double getAngerResistance(int level) {
    return Math.min(getConfig().maxAngerResistance, getConfig().angerResistanceBase + (getLevelPercent(level) * getConfig().angerResistanceFactor));
  }


  @ConfigDescription("Taming succeeds more often, and neutral mobs are slower to anger at you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Taming Odds Base for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tamingOddsBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Taming Odds Factor for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tamingOddsFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Taming Odds for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxTamingOdds = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anger Resistance Base for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double angerResistanceBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anger Resistance Factor for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double angerResistanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Anger Resistance for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAngerResistance = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time Wild Empathy forces a successful tame.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTame = 60;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
