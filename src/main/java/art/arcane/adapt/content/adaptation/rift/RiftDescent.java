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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class RiftDescent extends SimpleAdaptation<RiftDescent.Config> {
  private final Cooldowns cooldown = cooldowns();

  public RiftDescent() {
    super("rift-descent");
    registerConfiguration(Config.class);
    setIcon(Material.SHULKER_BOX);
    setInterval(9544);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_descent_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SHULKER_SHELL)
            .key("challenge_rift_descent_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_descent_100", "rift.descent.levitation-cancelled", 100, 300);
    registerMilestone("challenge_rift_descent_1k", "rift.descent.levitation-cancelled", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.YELLOW + AdaptLanguage.text(RiftMessages.DESCENT_LORE1));
    v.addLore(C.GREEN + AdaptLanguage.text(
        RiftMessages.DESCENT_COOLDOWN,
        trusted("duration", C.WHITE + Form.f(getConfig().cooldown, 1) + "s")
    ));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking() || p.getPotionEffect(PotionEffectType.LEVITATION) == null) {
      return;
    }
    if (!hasActiveAdaptation(p)) {
      return;
    }
    UUID playerId = p.getUniqueId();
    long cooldownMs = Math.max(1L, (long) (getConfig().cooldown * 1000D));
    if (!cooldown.isReady(playerId, cooldownMs)) {
      return;
    }

    p.removePotionEffect(PotionEffectType.LEVITATION);
    addStat(p, "rift.descent.levitation-cancelled", 1);
    cooldown.mark(playerId);
    fx(p, FxPriority.TRANSITION)
        .particle(Particle.REVERSE_PORTAL, 8, 0, 1.2, 0, 0.12, 0.02)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.5f, 1.7f);

    int protectionTicks = fallProtectionTicks(getConfig().cooldown);
    if (protectionTicks > 0) {
      AdaptAttributeService.get().applyTimed(p, getName(), "fall", Attributes.FALL_DAMAGE_MULTIPLIER,
          -1.0D, AttributeModifier.Operation.ADD_NUMBER, protectionTicks);
    }

    J.runEntity(p, () -> FxPresets.readyPing(this, p), Math.max(1, (int) Math.round(getConfig().cooldown * 20D)));

    J.runEntity(p, () -> fx(p, FxPriority.TRANSITION)
        .particle(Particle.DRAGON_BREATH, 14, 0, -0.2, 0, 0.2, 0.04)
        .particle(Particle.CLOUD, 6, 0, -0.1, 0, 0.25, 0.02)
        .chord(Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.2f));
  }

  static int fallProtectionTicks(double cooldownSeconds) {
    return (int) (20D * cooldownSeconds);
  }

  @ConfigDescription("Sneak to descend and negate levitation effects.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Rift Descent adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 5.0;

    public Config() {
      permanent = true;
      baseCost = 1;
      costFactor = 0.95;
      initialCost = 3;
      maxLevel = 1;
    }
  }

}
