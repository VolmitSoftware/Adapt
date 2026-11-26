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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;


public class RiftResist extends SimpleAdaptation<RiftResist.Config> {
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id ENDER_EYE = Id.minecraft("ender_eye");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private final Cooldowns activationThrottle = cooldowns();

  public RiftResist() {
    super("rift-resist");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sculk_vein"));
    setInterval(10288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_resist_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_rift_resist_200", "rift.resist.activations", 200, 300);
  }

  public static void riftResistStackAdd(Adaptation<?> source, PlayerRef p, int duration, int amplifier) {
    Loc location = p.location();

    boolean refresh = p.effect(RESISTANCE) != null;
    p.addEffect(new EffectInstance(RESISTANCE, duration, amplifier, true, false, false));

    if (refresh) {
      Fx.now(source, location, FxPriority.TRANSITION)
          .particle(Particles.REVERSE_PORTAL, 4, 0, 1.1, 0, 0.2, 0.02)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
      return;
    }

    Fx.now(source, location, FxPriority.TRANSITION)
        .dome(Particles.ENCHANTMENT_TABLE, 0.9, 14)
        .particle(Particles.WITCH, 8, 0, 1.0, 0, 0.4, 0.01)
        .chord(Sounds.ITEM_ARMOR_EQUIP_IRON, 1f, 1.24f, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f, Sounds.BLOCK_CONDUIT_ACTIVATE, 0.5f, 0.8f);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.RESIST_LORE1));
    v.addLore(C.UNDERLINE + AdaptLanguage.text(RiftMessages.RESIST_LORE2));
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplitude for the Rift Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int amplitude = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration for the Rift Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int duration = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between manual right-click-air activations in milliseconds.", impact = "Higher values slow repeated activations and the XP they grant; lower values allow faster reuse.")
    long activationCooldownMillis = 4000;

    public Config() {
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 2;
    }
  }
}
