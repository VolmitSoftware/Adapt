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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;


public class RiftResist extends SimpleAdaptation<RiftResist.Config> {
  public static final Id ID = Id.adapt("rift/resist");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id ENDER_EYE = Id.minecraft("ender_eye");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private final Cooldowns activationThrottle = cooldowns();

  public RiftResist() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sculk_vein"));
    setInterval(10288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_resist_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_rift_resist_200", "rift.resist.activations", 200, 300);
  }

  public static void riftResistStackAdd(Adaptation<?> source, PlayerRef p, int duration, int amplifier) {
    Loc location = p.location();

    boolean refresh = p.effect(RESISTANCE) != null;
    p.addEffect(EffectInstance.of(RESISTANCE, duration, amplifier).withAmbient(true).withParticles(false).withIcon(false));

    if (refresh) {
      Fx.now(source, location, FxPriority.TRANSITION)
          .particle(Particles.REVERSE_PORTAL, 4, 0, 1.1, 0, 0.2, 0.02)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
      return;
    }

    Fx.now(source, location, FxPriority.TRANSITION)
        .dome(Particles.ENCHANT, 0.9, 14)
        .particle(Particles.WITCH, 8, 0, 1.0, 0, 0.4, 0.01)
        .chord(Sounds.ITEM_ARMOR_EQUIP_IRON, 1f, 1.24f, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f, Sounds.BLOCK_CONDUIT_ACTIVATE, 0.5f, 0.8f);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.ITALIC + Text.key("adapt.rift.resist.lore1").resolve());
    v.addLore(TextFormat.UNDERLINE + Text.key("adapt.rift.resist.lore2").resolve());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();

    if (e.action() == InteractAction.RIGHT_CLICK_AIR) {
      if (hand.type().equals(ENDER_EYE) || hand.type().equals(ENDER_PEARL)) {
        if (!activationThrottle.isReady(p.uuid(), Math.max(0L, getConfig().activationCooldownMillis))) {
          return;
        }
        activationThrottle.mark(p.uuid(), Math.max(0L, getConfig().activationCooldownMillis));
        xp(p, 3);
        riftResistStackAdd(this, p, getConfig().duration, getConfig().amplitude);
        addStat(p, "rift.resist.activations", 1);
      }
    }

  }


  @ConfigDescription("Gain resistance when using Ender items and abilities.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Amplitude for the Rift Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int amplitude = 1;
    @ConfigDoc(value = "Controls Duration for the Rift Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int duration = 80;
    @ConfigDoc(value = "Cooldown between manual right-click-air activations in milliseconds.", impact = "Higher values slow repeated activations and the XP they grant; lower values allow faster reuse.")
    long activationCooldownMillis = 4000;

    public Config() {
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 2;
    }
  }
}
