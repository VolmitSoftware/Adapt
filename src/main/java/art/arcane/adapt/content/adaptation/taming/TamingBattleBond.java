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
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class TamingBattleBond extends SimpleAdaptation<TamingBattleBond.Config> {
  private static final int HARD_MAX_PACK = 24;
  private final Cooldowns fxCd = cooldowns();

  static int buffTicks(double levelPercent, double base, double factor) {
    return Math.max(20, (int) Math.round(base + (levelPercent * factor)));
  }

  public TamingBattleBond() {
    super("tame-battle-bond");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.battle_bond");
    setIcon(Material.DIAMOND_SWORD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_taming_bond_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_taming_bond_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_bond_250", "taming.battle-bond.kills", 250, 400);
    registerMilestone("challenge_taming_bond_2500", "taming.battle-bond.kills", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + (1 + getBuffTier(level)) + C.GRAY + " " + Localizer.dLocalize("taming.battle_bond.lore1"));
    statLore(v, C.YELLOW, "* ", Form.duration(getBuffTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingEntity dead = e.getEntity();
    if (dead.getKiller() != null) {
      return;
    }

    if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent cause)) {
      return;
    }

    if (!(cause.getDamager() instanceof Tameable pet) || !pet.isTamed()
        || !(pet.getOwner() instanceof Player owner)) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    int amplifier = getBuffTier(level);
    int duration = getBuffTicks(level);
    double radius = Math.max(1.0, getConfig().packRadius);
    J.runEntity(owner, () -> bondPack(owner, amplifier, duration, radius));
  }

  private void bondPack(Player owner, int amplifier, int duration, double radius) {
    if (!owner.isOnline()) {
      return;
    }

    applyBuffs(owner, amplifier, duration);

    UUID ownerId = owner.getUniqueId();
    int limit = getPackLimit();
    int buffed = 0;
    for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
      if (buffed >= limit) {
        break;
      }
      if (entity instanceof LivingEntity living && entity instanceof Tameable tameable
          && tameable.isTamed() && living.isValid() && !living.isDead() && isOwnedBy(tameable, ownerId)) {
        buffed++;
        J.runEntity(living, () -> applyBuffs(living, amplifier, duration));
      }
    }

    if (fxCd.isReady(ownerId, 800L)) {
      fxCd.mark(ownerId);
      fx(owner.getLocation().add(0, 1, 0), FxPriority.COMBAT)
          .dustRing(Color.fromRGB(0xFF5B4A), 1.6D, 10, 1.0F)
          .chord(Sound.ENTITY_WOLF_GROWL, 0.7F, 1.0F, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4F, 1.3F);
    }

    addStat(owner, "taming.battle-bond.kills", 1);
    xp(owner, getConfig().xpPerKill);
  }

  private void applyBuffs(LivingEntity entity, int amplifier, int duration) {
    if (!entity.isValid() || entity.isDead()) {
      return;
    }

    entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier, false, false));
    if (PotionEffectTypes.INCREASE_DAMAGE != null) {
      entity.addPotionEffect(new PotionEffect(PotionEffectTypes.INCREASE_DAMAGE, duration, amplifier, false, false));
    }
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer owner = tameable.getOwner();
    return owner != null && ownerId.equals(owner.getUniqueId());
  }

  private int getBuffTier(int level) {
    int max = Math.max(0, getConfig().maxBuffTier);
    return Math.min(max, (int) Math.floor(getLevelPercent(level) * (max + 1)));
  }

  private int getBuffTicks(int level) {
    return buffTicks(getLevelPercent(level), getConfig().buffTicksBase, getConfig().buffTicksFactor);
  }

  private int getPackLimit() {
    return Math.max(1, Math.min(getConfig().maxPack, HARD_MAX_PACK));
  }


  @ConfigDescription("When one of your pets lands a kill, you and the nearby pack gain brief strength and speed.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Highest buff amplifier (tier) reachable at max level; level 1 grants tier 1.", impact = "Higher values grant a stronger strength and speed tier at high levels.")
    int maxBuffTier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Buff Ticks Base for the Taming Battle Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double buffTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Buff Ticks Factor for the Taming Battle Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double buffTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched for pack members to buff on a pet kill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double packRadius = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time Battle Bond triggers on a pet kill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerKill = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pack members buffed per kill, capped internally at 24.", impact = "Lower values reduce entity scheduling in very large packs.")
    int maxPack = 12;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 3;
    }
  }
}
