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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;


public class RiftEnderchest extends SimpleAdaptation<RiftEnderchest.Config> {
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", RiftMessages.RIFTENDERCHEST_PREFERENCE_GESTURE, Gesture.CURRENT, List.of(
          new PlayerPreference.Choice<>(Gesture.CURRENT, RiftMessages.RIFTENDERCHEST_PREFERENCE_GESTURE_CURRENT, Id.minecraft("ender_chest"), 1),
          new PlayerPreference.Choice<>(Gesture.LEFT, RiftMessages.RIFTENDERCHEST_PREFERENCE_GESTURE_LEFT, Id.minecraft("arrow"), 1),
          new PlayerPreference.Choice<>(Gesture.RIGHT_AIR, RiftMessages.RIFTENDERCHEST_PREFERENCE_GESTURE_RIGHT_AIR, Id.minecraft("feather"), 1))));
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", RiftMessages.RIFTENDERCHEST_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);
  private static final Id ENDER_CHEST = Id.minecraft("ender_chest");

  public RiftEnderchest() {
    super("rift-enderchest");
    setLocalizationKey("rift.chest");
    setIcon(Id.minecraft("ender_chest"));
    setInterval(9248);
    registerConfiguration(Config.class);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_chest"))
        .key("challenge_rift_enderchest_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_rift_enderchest_200", "rift.enderchest.opens", 200, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, GESTURE, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.CHEST_LORE1));
  }

  @Handle
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    Gesture gesture = preference(p, GESTURE);
    if ((preferenceEnabled(p, SNEAK) && !p.isSneaking())
        || (gesture == Gesture.LEFT && action == InteractAction.RIGHT_CLICK_AIR)
        || (gesture == Gesture.RIGHT_AIR && action != InteractAction.RIGHT_CLICK_AIR)) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();
    if (!hand.type().equals(ENDER_CHEST) || !hasActiveAdaptation(p)) {
      return;
    }

    if (p.hasCooldown(hand.type())) {
      e.setCancelled(true);
      fx(p.eyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.15)
          .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.6f);
      return;
    }

    p.setCooldown(ENDER_CHEST, 100);
    AbilityGlyphs.cooldown(p, this, 100 * 50L);
    if (getActiveSiblingLevel(p, "rift-resist") > 0) {
      RiftResist.riftResistStackAdd(this, p, 10, 2);
    }
    Loc origin = p.eyeLocation().add(p.location().direction().multiply(0.5));
    fx(origin, FxPriority.TRANSITION)
        .helix(Particles.PORTAL, 0.4, 0.8, 8, 0)
        .particle(Particles.REVERSE_PORTAL, 3, 0, 0, 0, 0.15, 0.02)
        .chord(Sounds.PARTICLE_SOUL_ESCAPE, 1f, 0.8f, Sounds.BLOCK_ENDER_CHEST_OPEN, 1f, 1.0f, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.4f);
    p.openEnderChest();
    addStat(p, "rift.enderchest.opens", 1);
  }



  @ConfigDescription("Open an enderchest by left-clicking it in your hand.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 0;
      costFactor = 0.0;
      maxLevel = 1;
      initialCost = 10;
    }
  }

  public enum Gesture { CURRENT, LEFT, RIGHT_AIR }
}
