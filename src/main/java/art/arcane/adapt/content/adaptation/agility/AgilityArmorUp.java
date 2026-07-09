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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class AgilityArmorUp extends SimpleAdaptation<AgilityArmorUp.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-armor-up".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:armor-up");
  private final Map<UUID, RuntimeState> states = playerState();

  public AgilityArmorUp() {
    super("agility-armor-up");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_CHESTPLATE);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_agility_armor_up_30min")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_CHESTPLATE)
            .key("challenge_agility_armor_up_5hr")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_armor_up_30min", "agility.armor-up.ticks-armored", 36000, 500);
    registerMilestone("challenge_agility_armor_up_5hr", "agility.armor-up.ticks-armored", 360000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getWindupArmor(getLevelPercent(level)), 0), 1);
    v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + " " + C.GRAY + Localizer.dLocalize("agility.armor_up.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration(getDecaySeconds(getLevelPercent(level)) * 1000D, 1) + " " + C.GRAY + Localizer.dLocalize("agility.armor_up.lore3"));
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    scrubModifier(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearAndRemoveState(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    clearAndRemoveState(e.getEntity());
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }
      withPlayerThread(p, () -> updatePlayer(p));
    }
  }

  private double getWindupTicks(double factor) {
    return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
  }

  private double getWindupArmor(double factor) {
    return getConfig().windupArmorBase + (factor * getConfig().windupArmorLevelMultiplier);
  }

  private double getDecaySeconds(double factor) {
    return getConfig().decaySecondsBase + (factor * getConfig().decaySecondsMaxLevelBonus);
  }

  private void updatePlayer(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.getUniqueId();
    RuntimeState state = states.computeIfAbsent(id, key -> new RuntimeState());
    IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
    if (attribute == null) {
      return;
    }

    double factor = getLevelPercent(p);
    if (isPlatingActive(p)) {
      buildPlating(p, state, attribute, factor);
      return;
    }

    decayPlating(p, state, attribute, factor);
  }

  private boolean isPlatingActive(Player p) {
    if (!hasActiveAdaptation(p)) {
      return false;
    }
    if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSneaking()) {
      return false;
    }
    return p.isSprinting();
  }

  private void buildPlating(Player p, RuntimeState state, IAttribute attribute, double factor) {
    state.decaying = false;
    double ticksToMax = Math.max(1D, getWindupTicks(factor));
    state.plating = Math.min(1.0D, state.plating + (1.0D / ticksToMax));
    emitPlatingFeedback(p, state, state.plating);
    double armorInc = getWindupArmor(factor) * state.plating;
    attribute.setModifier(MODIFIER, MODIFIER_KEY, armorInc * 10, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    addStat(p, "agility.armor-up.ticks-armored", 1);
  }

  private void decayPlating(Player p, RuntimeState state, IAttribute attribute, double factor) {
    if (state.plating <= 0D) {
      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
      return;
    }

    if (!state.decaying) {
      state.decaying = true;
      double decayTicks = Math.max(1D, getDecaySeconds(factor) * 20D);
      state.decayPerTick = state.plating / decayTicks;
    }

    state.plating = Math.max(0D, state.plating - state.decayPerTick);
    if (state.plating <= 0D) {
      completeDecay(p, state, attribute);
      return;
    }

    double armorInc = getWindupArmor(factor) * state.plating;
    attribute.setModifier(MODIFIER, MODIFIER_KEY, armorInc * 10, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    emitDecayShimmer(p);
  }

  private void completeDecay(Player p, RuntimeState state, IAttribute attribute) {
    state.plating = 0D;
    state.decaying = false;
    state.lastBracket = 0;
    state.fullyArmored = false;
    attribute.removeModifier(MODIFIER, MODIFIER_KEY);
    fx(p.getLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.1D)
        .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.5F);
  }

  private void emitPlatingFeedback(Player p, RuntimeState state, double progress) {
    if (progress >= 1.0D && M.r(0.2)) {
      fx(p.getLocation(), FxPriority.AMBIENT).particle(Particle.END_ROD, 1, 0, 0.1D, 0, 0.05D, 0);
    }

    int bracket = (int) Math.floor(progress / 0.25D);
    if (bracket <= state.lastBracket) {
      return;
    }

    state.lastBracket = bracket;
    if (progress >= 1.0D) {
      if (state.fullyArmored) {
        return;
      }

      state.fullyArmored = true;
      fx(p, FxPriority.GAMEPLAY)
          .dome(Particle.WAX_ON, 0.8D, 10)
          .chord(Sound.BLOCK_ANVIL_LAND, 0.25F, 1.6F, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.4F, 1.0F);
      return;
    }

    if (bracket < 1) {
      return;
    }

    float pitch = switch (bracket) {
      case 1 -> 0.8F;
      case 2 -> 1.0F;
      default -> 1.3F;
    };
    fx(p, FxPriority.GAMEPLAY)
        .helix(Particle.END_ROD, 0.6D, 1.4D, 6, 0)
        .sound(Sound.ITEM_ARMOR_EQUIP_IRON, 0.4F, pitch);
  }

  private void emitDecayShimmer(Player p) {
    if (!M.r(0.1)) {
      return;
    }

    fx(p.getLocation(), FxPriority.AMBIENT).particle(Particle.END_ROD, 1, 0, 0.1D, 0, 0.02D, 0);
  }

  private void clearAndRemoveState(Player p) {
    if (p == null) {
      return;
    }

    states.remove(p.getUniqueId());
    scrubModifier(p);
  }

  private void scrubModifier(Player p) {
    if (p == null) {
      return;
    }

    withPlayerThread(p, () -> {
      IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
      if (attribute == null) {
        return;
      }

      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
    });
  }


  @ConfigDescription("Gain more armor the longer you sprint.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Slowest for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksSlowest = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Fastest for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksFastest = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Armor Base for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupArmorBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Armor Level Multiplier for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupArmorLevelMultiplier = 0.525;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Decay Seconds Base for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double decaySecondsBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Decay Seconds Max Level Bonus for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double decaySecondsMaxLevelBonus = 5.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.65;
      initialCost = 8;
    }
  }

  private static class RuntimeState {
    private double plating;
    private int lastBracket;
    private boolean fullyArmored;
    private boolean decaying;
    private double decayPerTick;
  }

}
