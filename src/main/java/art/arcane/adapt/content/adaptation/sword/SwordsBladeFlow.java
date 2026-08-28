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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;

public class SwordsBladeFlow extends SimpleAdaptation<SwordsBladeFlow.Config> {
  private static final Color FLOW = Color.fromRGB(0xF4E27A);
  private static final double ATTACK_SPEED_PER_STACK = 0.10;
  private static final String FLOW_SLOT = "flow";
  private final Map<UUID, Integer> flowStacks = playerState();
  private final Map<UUID, Long> flowExpireAt = playerState();

  public SwordsBladeFlow() {
    super("sword-blade-flow");
    registerConfiguration(Config.class);
    setIcon(Material.GOLDEN_SWORD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_flow_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_swords_flow_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_flow_1k", "swords.blade-flow.stacks-built", 1000, 400);
    registerMilestone("challenge_swords_flow_10k", "swords.blade-flow.stacks-built", 10000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_SWORD)
        .key("challenge_swords_flow_max")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getStackCap(level), 1);
    statLore(v, Form.pc(ATTACK_SPEED_PER_STACK, 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    int current = flowStacks.getOrDefault(id, 0);
    if (now > flowExpireAt.getOrDefault(id, 0L)) {
      current = 0;
    }

    int cap = getStackCap(combat.level());
    int next = Math.min(cap, current + 1);
    long windowMillis = (long) getConfig().windowMillis;
    flowStacks.put(id, next);
    flowExpireAt.put(id, now + windowMillis);
    AdaptAttributeService.get().applyTimed(p, getName(), FLOW_SLOT, Attributes.ATTACK_SPEED,
        attackSpeedBonus(next), AttributeModifier.Operation.ADD_SCALAR, effectDurationTicks(windowMillis));

    addStat(p, "swords.blade-flow.stacks-built", 1);
    xp(p, getConfig().xpPerStack);
    if (next > current) {
      float pitch = 0.9F + (0.12F * next);
      fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
          .dustBurst(FLOW, Math.min(6, next + 1), 0.25D, 0.9F)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, Math.min(2.0F, pitch));
    }

    if (next >= cap && grantOnce(p, "challenge_swords_flow_max")) {
      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 10, 0.4D)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getFinalDamage() <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    int current = flowStacks.getOrDefault(id, 0);
    if (current <= 0) {
      return;
    }

    boolean expired = System.currentTimeMillis() > flowExpireAt.getOrDefault(id, 0L);
    clearFlow(p);
    if (expired) {
      return;
    }

    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.4F, 0.7F);
  }

  private int getStackCap(int level) {
    return Math.max(1, (int) Math.round(getConfig().stackCapBase + (getLevelPercent(level) * getConfig().stackCapFactor)));
  }

  static int effectDurationTicks(long windowMillis) {
    long durationTicks = Math.max(20L, (Math.max(0L, windowMillis) + 49L) / 50L);
    return (int) Math.min(Integer.MAX_VALUE, durationTicks);
  }

  static double attackSpeedBonus(int stacks) {
    return Math.max(0, stacks) * ATTACK_SPEED_PER_STACK;
  }

  private void clearFlow(Player player) {
    UUID id = player.getUniqueId();
    flowStacks.remove(id);
    flowExpireAt.remove(id);
    AdaptAttributeService.get().remove(player, getName(), FLOW_SLOT, Attributes.ATTACK_SPEED);
  }

  @ConfigDescription("Consecutive sword hits build attack-speed stacks; taking damage breaks the flow.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Cap Base for the Swords Blade Flow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stackCapBase = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Cap Factor for the Swords Blade Flow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stackCapFactor = 4.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How long a flow stack survives without a new hit, in milliseconds.", impact = "Higher values keep the flow alive longer between strikes.")
    double windowMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Stack for the Swords Blade Flow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerStack = 3;

    public Config() {
      baseCost = 5;
      costFactor = 0.62;
      maxLevel = 5;
      initialCost = 5;
    }
  }
}
