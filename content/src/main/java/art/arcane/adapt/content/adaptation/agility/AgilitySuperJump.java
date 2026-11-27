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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.UUID;

public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
  public static final Id ID = Id.adapt("agility/super_jump");
  public static final PlayerPreference<Control> CONTROL = PlayerPreference.of("control", Control.class, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, Text.key("adapt.agilitysuperjump.preferences.preference_control_sneak"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.EVERY_JUMP, Text.key("adapt.agilitysuperjump.preferences.preference_control_every_jump"), Id.minecraft("feather"), 1))).labelled(Text.key("adapt.agilitysuperjump.preferences.preference_control"));
  public static final PlayerPreference<CommonPreferences.Scale> HEIGHT = CommonPreferences.scale("height", Text.key("adapt.agilitysuperjump.preferences.preference_height"));

  private static final int SUPER_JUMP_LEVELS = 4;

  private final Cooldowns cooldowns = cooldowns();

  public AgilitySuperJump() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.JUMP_STRENGTH));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_boots"));
    setInterval(500);
    pulseOwners(this::getInterval, this::reconcileJump);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_agility_super_jump_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_boots"))
            .key("challenge_agility_super_jump_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_super_jump_100", "agility.super-jump.jumps", 100, 300);
    registerMilestone("challenge_agility_super_jump_5k", "agility.super-jump.jumps", 5000, 1500);
  }

  static double jumpHeight(double minimumHeight, double maximumHeight, int level) {
    return jumpHeight(minimumHeight, maximumHeight, level, SUPER_JUMP_LEVELS);
  }

  static double jumpHeight(double minimumHeight, double maximumHeight, int level, int maxLevel) {
    double vanillaHeight = AgilityJumpPhysics.heightForStrength(AgilityJumpPhysics.VANILLA_JUMP_STRENGTH);
    double safeMinimum = Double.isFinite(minimumHeight) ? minimumHeight : vanillaHeight;
    double safeMaximum = Double.isFinite(maximumHeight) ? maximumHeight : safeMinimum;
    double lower = Math.max(vanillaHeight, Math.min(safeMinimum, safeMaximum));
    double upper = Math.max(lower, Math.max(safeMinimum, safeMaximum));
    if (maxLevel <= 1) {
      return upper;
    }

    int clampedLevel = Math.max(1, Math.min(level, maxLevel));
    double progress = (double) (clampedLevel - 1) / (maxLevel - 1);
    return lower + ((upper - lower) * progress);
  }

  private double getJumpHeight(int level) {
    return jumpHeight(getConfig().minimumJumpHeight, getConfig().maximumJumpHeight, level, getMaxLevel());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, HEIGHT);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef p = player.player();
    AgilityHighJump.remove(this, p);
    if (hasActiveAdaptation(p) && (p.isSneaking() || preference(p, CONTROL) == Control.EVERY_JUMP)) {
      applyBoost(p);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getJumpHeight(level), 2), 1);
    v.addLore(TextFormat.LIGHT_PURPLE + " " + Text.key("adapt.agility.super_jump.lore2").resolve());
  }

  private void reconcileJump(PlayerRef p) {
    if (isPlayerEnabled(p) && getActiveLevel(p) > 0 && (p.isSneaking() || preference(p, CONTROL) == Control.EVERY_JUMP)) {
      applyBoost(p);
    } else {
      AgilityHighJump.remove(this, p);
    }
  }

  static double personalJumpHeight(double earnedHeight, double fraction) {
    double baseline = AgilityJumpPhysics.heightForStrength(AgilityJumpPhysics.VANILLA_JUMP_STRENGTH);
    return baseline + Math.max(0D, earnedHeight - baseline) * Math.max(0D, Math.min(1D, fraction));
  }

  private void applyBoost(PlayerRef p) {
    AgilityHighJump.apply(this, p, personalJumpHeight(getJumpHeight(getLevel(p)), preference(p, HEIGHT).multiplier()));
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking() && preference(p, CONTROL) == Control.SNEAK) {
      AgilityHighJump.remove(this, p);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (hasActiveAdaptation(p) && canUse(p)) {
        applyBoost(p);
      }

      if (p.isOnGround()) {
        AgilityHighJump.crouch(this, p);
      }
    });
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      AgilityHighJump.remove(this, e.player());
    }
  }

  @Handle
  public void on(PlayerChangedWorldEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      AgilityHighJump.remove(this, p);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerJumpEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || (preference(p, CONTROL) == Control.SNEAK && !p.isSneaking())) {
      return;
    }

    handleJump(p, e);
  }

  private void handleJump(PlayerRef p, Cancellable e) {
    withPlayerThread(p, e, () -> {
      if (!hasActiveAdaptation(p) || !canUse(p)) {
        AgilityHighJump.remove(this, p);
        return;
      }

      applyBoost(p);

      UUID id = p.uuid();
      if (!cooldowns.isReady(id, 1000)) {
        return;
      }

      cooldowns.mark(id, 1000);
      addStat(p, "agility.super-jump.jumps", 1);

      AgilityHighJump.burst(this, p, getJumpHeight(getLevel(p)));
    });
  }


  @ConfigDescription("Sneak and jump for exceptional height advantage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Jump apex in blocks at level 1.", impact = "Higher values make the first super-jump level rise further.")
    double minimumJumpHeight = 1.5D;
    @ConfigDoc(value = "Jump apex in blocks at the configured maximum level.", impact = "Higher values make the final super-jump level rise further.")
    double maximumJumpHeight = 3.75D;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = SUPER_JUMP_LEVELS;
      initialCost = 2;
    }
  }

  public enum Control { SNEAK, EVERY_JUMP }


  static void crouchFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.location(), FxPriority.GAMEPLAY)
        .ring(Particles.CLOUD, 0.5D, 6, 0.05D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.35F);
  }

  static void burstFx(Adaptation<?> owner, PlayerRef p, double apexHeight) {
    float topPitch = (float) Math.min(2.0D, 1.4D + apexHeight);
    double shockRadius = Math.max(0.6D, apexHeight * 1.5D);
    Loc at = p.location();
    Fx.now(owner, at, FxPriority.GAMEPLAY)
        .column(Particles.CLOUD, 8, 1.2D)
        .ring(Particles.blockCrack(at.world().blockAt(at).relative(BlockFace.DOWN).state()), shockRadius, 10, 0.05D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, 0.7F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, topPitch, Sounds.ENTITY_ILLUSIONER_CAST_SPELL, 0.3F, 0.7F);
  }
}
