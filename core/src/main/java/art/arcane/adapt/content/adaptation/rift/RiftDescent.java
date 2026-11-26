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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class RiftDescent extends SimpleAdaptation<RiftDescent.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", RiftMessages.RIFTDESCENT_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, RiftMessages.RIFTDESCENT_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.AUTOMATIC, RiftMessages.RIFTDESCENT_PREFERENCE_CONTROL_AUTOMATIC, Id.minecraft("feather"), 1))));
  public static final Id LEVITATION = Id.minecraft("levitation");
  private final RiftLevitation levitation = new RiftLevitation(this, cooldowns(),
      (PlayerRef p) -> addStat(p, "rift.descent.levitation-cancelled", 1));

  public RiftDescent() {
    super("rift-descent");
    needs(PlatformNeed.attribute(Attributes.FALL_DAMAGE_MULTIPLIER));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shulker_box"));
    setInterval(9544);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_descent_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("shulker_shell"))
            .key("challenge_rift_descent_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_descent_100", "rift.descent.levitation-cancelled", 100, 300);
    registerMilestone("challenge_rift_descent_1k", "rift.descent.levitation-cancelled", 1000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.YELLOW + AdaptLanguage.text(RiftMessages.DESCENT_LORE1));
    v.addLore(C.GREEN + AdaptLanguage.text(
        RiftMessages.DESCENT_COOLDOWN,
        trusted("duration", C.WHITE + Form.f(getConfig().cooldown, 1) + "s")
    ));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking()) {
      return;
    }
    cancelLevitation(p);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent event) {
    if (event.entity() instanceof PlayerRef player && event.newEffect() != null
        && event.newEffect().type().equals(LEVITATION)
        && preference(player, CONTROL) == Control.AUTOMATIC) {
      Tasks.runEntity(player, () -> cancelLevitation(player), 1);
    }
  }

  private void cancelLevitation(PlayerRef p) {
    levitation.cancel(p, getConfig().cooldown);
  }

  static int fallProtectionTicks(double cooldownSeconds) {
    return (int) (20D * cooldownSeconds);
  }

  /** The release effects, then fall protection for {@code cooldownSeconds} and a ready ping when it ends. */
  public static void release(Adaptation<?> owner, PlayerRef p, double cooldownSeconds) {
    Fx.now(owner, p, FxPriority.TRANSITION)
        .particle(Particles.REVERSE_PORTAL, 8, 0, 1.2, 0, 0.12, 0.02)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_BREAK, 0.5f, 1.7f);

    int protectionTicks = fallProtectionTicks(cooldownSeconds);
    if (protectionTicks > 0) {
      AdaptAttributeService.get().applyTimed(p, owner.getName(), "fall", Attributes.FALL_DAMAGE_MULTIPLIER,
          -1.0D, AttributeOperation.ADD_VALUE, protectionTicks);
    }

    Tasks.runEntity(p, () -> FxPresets.readyPing(owner, p), Math.max(1, (int) Math.round(cooldownSeconds * 20D)));

    Tasks.runEntity(p, () -> Fx.now(owner, p, FxPriority.TRANSITION)
        .particle(Particles.DRAGON_BREATH, 14, 0, -0.2, 0, 0.2, 0.04, new ParticleData.Power(1.0F))
        .particle(Particles.CLOUD, 6, 0, -0.1, 0, 0.25, 0.02)
        .chord(Sounds.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f, Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.2f));
  }

  @ConfigDescription("Sneak to descend and negate levitation effects.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Rift Descent adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 5.0;

    public Config() {
      permanent = true;
      baseCost = 1;
      costFactor = 1;
      initialCost = 1;
      maxLevel = 1;
    }
  }


  public enum Control { SNEAK, AUTOMATIC }
}
