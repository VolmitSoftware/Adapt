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
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
  private final AdaptationOwnerPulse.Registration ownerMaintenance;
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", AgilityMessages.AGILITYSUPERJUMP_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, AgilityMessages.AGILITYSUPERJUMP_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.EVERY_JUMP, AgilityMessages.AGILITYSUPERJUMP_PREFERENCE_CONTROL_EVERY_JUMP, Id.minecraft("feather"), 1))));
  public static final PlayerPreference<CommonPreferences.Scale> HEIGHT = CommonPreferences.scale("height", AgilityMessages.AGILITYSUPERJUMP_PREFERENCE_HEIGHT);

  private static final String SLOT_JUMP = "jump";
  private static final int SUPER_JUMP_LEVELS = 4;

  private final Cooldowns cooldowns = cooldowns();

  public AgilitySuperJump() {
    super("agility-super-jump");
    needs(PlatformNeed.attribute(Attributes.JUMP_STRENGTH));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_boots"));
    setInterval(500);
    ownerMaintenance = AdaptationOwnerPulse.register(this, this::getInterval, this::reconcileJump);
    setMaxLevel(SUPER_JUMP_LEVELS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_agility_super_jump_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_boots"))
            .key("challenge_agility_super_jump_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
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

  static double jumpStrengthBonus(double jumpHeight) {
    return AgilityJumpPhysics.bonusForHeight(jumpHeight);
  }

  private double getJumpHeight(int level) {
    return jumpHeight(getConfig().minimumJumpHeight, getConfig().maximumJumpHeight, level, getMaxLevel());
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, HEIGHT);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
    if (hasActiveAdaptation(p) && (p.isSneaking() || preference(p, CONTROL) == Control.EVERY_JUMP)) {
      applyBoost(p);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getJumpHeight(level), 2), 1);
    v.addLore(C.LIGHT_PURPLE + " " + AdaptLanguage.text(AgilityMessages.SUPER_JUMP_LORE2));
  }

  private void reconcileJump(PlayerRef p) {
    if (isPlayerEnabled(p) && getActiveLevel(p) > 0 && (p.isSneaking() || preference(p, CONTROL) == Control.EVERY_JUMP)) {
      applyBoost(p);
    } else {
      AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
    }
  }

  static double personalJumpHeight(double earnedHeight, double fraction) {
    double baseline = AgilityJumpPhysics.heightForStrength(AgilityJumpPhysics.VANILLA_JUMP_STRENGTH);
    return baseline + Math.max(0D, earnedHeight - baseline) * Math.max(0D, Math.min(1D, fraction));
  }

  private void applyBoost(PlayerRef p) {
    AdaptAttributeService.get().apply(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH, jumpStrengthBonus(personalJumpHeight(getJumpHeight(getLevel(p)), preference(p, HEIGHT).multiplier())), AttributeOperation.ADD_VALUE);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking() && preference(p, CONTROL) == Control.SNEAK) {
      AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (hasActiveAdaptation(p) && canUse(getPlayer(p))) {
        applyBoost(p);
      }

      if (p.isOnGround()) {
        fx(p.location(), FxPriority.GAMEPLAY)
            .ring(Particles.CLOUD, 0.5D, 6, 0.05D)
            .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.35F);
      }
    });
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      AdaptAttributeService.get().remove(e.player(), getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
    }
  }

  @Handle
  public void on(PlayerChangedWorldEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
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
      if (!hasActiveAdaptation(p) || !canUse(getPlayer(p))) {
        AdaptAttributeService.get().remove(p, getName(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
        return;
      }

      applyBoost(p);

      UUID id = p.uuid();
      if (!cooldowns.isReady(id, 1000)) {
        return;
      }

      cooldowns.mark(id, 1000);
      addStat(p, "agility.super-jump.jumps", 1);

      double jumpHeight = getJumpHeight(getLevel(p));
      float topPitch = (float) Math.min(2.0D, 1.4D + jumpHeight);
      double shockRadius = Math.max(0.6D, jumpHeight * 1.5D);
      Loc at = p.location();
      fx(at, FxPriority.GAMEPLAY)
          .column(Particles.CLOUD, 8, 1.2D)
          .ring(Particles.blockCrack(at.world().blockAt(at).relative(BlockFace.DOWN).state()), shockRadius, 10, 0.05D)
          .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, 0.7F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, topPitch, Sounds.ENTITY_ILLUSIONER_CAST_SPELL, 0.3F, 0.7F);
    });
  }


  @ConfigDescription("Sneak and jump for exceptional height advantage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Jump apex in blocks at level 1.", impact = "Higher values make the first super-jump level rise further.")
    double minimumJumpHeight = 1.5D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Jump apex in blocks at the configured maximum level.", impact = "Higher values make the final super-jump level rise further.")
    double maximumJumpHeight = 3.75D;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = SUPER_JUMP_LEVELS;
      initialCost = 2;
    }
  }

  public enum Control { SNEAK, EVERY_JUMP }
}
