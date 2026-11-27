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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.UUID;

public class SwordsBladeFlow extends SimpleAdaptation<SwordsBladeFlow.Config> {
  public static final Id ID = Id.adapt("sword/blade_flow");
  private static final Rgb FLOW = Rgb.of(0xF4E27A);
  private final SwordFlow flow = new SwordFlow(this, playerState(), playerState());

  public SwordsBladeFlow() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_sword"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_flow_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_swords_flow_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_flow_1k", "swords.blade-flow.stacks-built", 1000, 400);
    registerMilestone("challenge_swords_flow_10k", "swords.blade-flow.stacks-built", 10000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_sword"))
        .key("challenge_swords_flow_max")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getStackCap(level), 1);
    statLore(v, Format.pc(SwordFlow.ATTACK_SPEED_PER_STACK, 0), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    int cap = getStackCap(combat.level());
    SwordFlow.Step step = flow.build(p, cap, (long) getConfig().windowMillis, System.currentTimeMillis());
    int current = step.previous();
    int next = step.stacks();

    addStat(p, "swords.blade-flow.stacks-built", 1);
    xp(p, getConfig().xpPerStack);
    if (next > current) {
      float pitch = 0.9F + (0.12F * next);
      fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
          .dustBurst(FLOW, Math.min(6, next + 1), 0.25D, 0.9F)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, Math.min(2.0F, pitch));
    }

    if (next >= cap && grantOnce(p, "challenge_swords_flow_max")) {
      fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 10, 0.4D)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.finalDamage() <= 0) {
      return;
    }

    UUID id = p.uuid();
    if (!flow.holds(id)) {
      return;
    }

    boolean expired = flow.expired(id, System.currentTimeMillis());
    flow.clear(p);
    if (expired) {
      return;
    }

    fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_BREAK, 0.4F, 0.7F);
  }

  private int getStackCap(int level) {
    return Math.max(1, (int) Math.round(getConfig().stackCapBase + (getLevelPercent(level) * getConfig().stackCapFactor)));
  }

  @ConfigDescription("Consecutive sword hits build attack-speed stacks; taking damage breaks the flow.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Stack Cap Base for the Swords Blade Flow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stackCapBase = 1.5;
    @ConfigDoc(value = "Controls Stack Cap Factor for the Swords Blade Flow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stackCapFactor = 4.5;
    @ConfigDoc(value = "How long a flow stack survives without a new hit, in milliseconds.", impact = "Higher values keep the flow alive longer between strikes.")
    double windowMillis = 4000;
    @ConfigDoc(value = "Controls Xp Per Stack for the Swords Blade Flow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerStack = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
