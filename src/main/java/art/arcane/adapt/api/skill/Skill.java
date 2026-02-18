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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.Component;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.tick.Ticked;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Public API for a skill line and its shared behavior.
 */
public interface Skill<T> extends Ticked, Component {
  /**
   * Builds the root advancement tree for this skill (including child
   * adaptations).
   */
  AdaptAdvancement buildAdvancements();

  /**
   * Returns the concrete config class used by this skill.
   */
  Class<T> getConfigurationClass();

  /**
   * Registers the config class used for load/reload of this skill.
   */
  void registerConfiguration(Class<T> type);

  /**
   * @return true when this skill is runtime-enabled.
   */
  boolean isEnabled();

  /**
   * Returns the live config instance for this skill.
   */
  T getConfig();

  /**
   * @return internal skill key (e.g. "swords").
   */
  String getName();

  /**
   * @return icon glyph/emoji prefix used in UI.
   */
  String getEmojiName();

  /**
   * @return base icon for this skill.
   */
  Material getIcon();

  /**
   * @return localized skill description.
   */
  String getDescription();

  /**
   * @return recipes registered by this skill.
   */
  KList<AdaptRecipe> getRecipes();

  /**
   * Registers an adaptation under this skill.
   */
  void registerAdaptation(Adaptation<?> a);

  /**
   * Registers a stat tracker/milestone for this skill.
   */
  void registerStatTracker(AdaptStatTracker tracker);

  /**
   * @return all stat trackers for this skill.
   */
  KList<AdaptStatTracker> getStatTrackers();

  /**
   * Skill-level particle toggle check (global + per-skill config aware).
   */
  @Override
  default boolean areParticlesEnabled() {
    return SkillGuiSupport.areParticlesEnabled(this, Component.super.areParticlesEnabled());
  }

  /**
   * Skill-level sound toggle check (global + per-skill config aware).
   */
  @Override
  default boolean areSoundsEnabled() {
    return SkillGuiSupport.areSoundsEnabled(this, Component.super.areSoundsEnabled());
  }

  /**
   * Evaluates and grants eligible stat-tracker advancements for one player.
   */
  default void checkStatTrackers(AdaptPlayer player) {
    SkillRuntimeGuards.checkStatTrackers(this, player);
  }

  /**
   * @return all adaptations currently attached to this skill.
   */
  KList<Adaptation<?>> getAdaptations();

  /**
   * @return primary chat/UI color for this skill.
   */
  C getColor();

  /**
   * @return minimum xp tuning value used by this skill.
   */
  double getMinXp();

  /**
   * Called while building advancement trees to append custom nodes.
   */
  void onRegisterAdvancements(KList<AdaptAdvancement> advancements);

  /**
   * Returns true when this player is blacklisted from this skill via
   * permission.
   */
  default boolean hasBlacklistPermission(Player p, Skill<?> s) {
    return SkillRuntimeGuards.hasBlacklistPermission(p, s);
  }

  /**
   * Formatted display name with color + emoji.
   */
  default String getDisplayName() {
    if (!this.isEnabled()) {
      return C.DARK_GRAY + Form.capitalize(getName());
    }
    return C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName() + " " + Form.capitalize(getName());
  }

  /**
   * Compact formatted display name with color + emoji.
   */
  default String getShortName() {
    if (!this.isEnabled()) {
      return C.DARK_GRAY + Form.capitalize(getName());
    }
    return C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName();
  }

  /**
   * Display name with optional level suffix.
   */
  default String getDisplayName(int level) {
    if (!this.isEnabled()) {
      return C.DARK_GRAY + Form.capitalize(getName());
    }
    if (level > 0) {
      return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }
    return getDisplayName();
  }

  /**
   * Returns the generated custom model binding for this skill icon.
   */
  default CustomModel getModel() {
    return CustomModel.get(getIcon(), "skill", getName());
  }

  /**
   * Grants visible xp at the player's location.
   */
  default void xp(Player p, double xp) {
    xp(p, xp, null);
  }

  /**
   * Grants visible xp at the player's location using an optional reward key.
   */
  default void xp(Player p, double xp, String rewardKey) {
    xp(p, p == null ? null : p.getLocation(), xp, rewardKey);

  }

  /**
   * Grants visible xp at a specific location.
   */
  default void xp(Player p, Location at, double xp) {
    xp(p, at, xp, null);
  }

  /**
   * Grants visible xp at a specific location using an optional reward key.
   */
  default void xp(Player p, Location at, double xp, String rewardKey) {
    SkillRuntimeGuards.grantXp(this, p, at, xp, rewardKey, false, true);
  }

  /**
   * Grants silent xp at a specific location (with optional burst visuals).
   */
  default void xpS(Player p, Location at, double xp) {
    xpS(p, at, xp, null);
  }

  /**
   * Grants silent xp at a specific location using an optional reward key.
   */
  default void xpS(Player p, Location at, double xp, String rewardKey) {
    SkillRuntimeGuards.grantXp(this, p, at, xp, rewardKey, true, true);
  }

  /**
   * Grants silent xp without visuals.
   */
  default void xpSilent(Player p, double xp) {
    xpSilent(p, xp, null);
  }

  /**
   * Grants silent xp without visuals using an optional reward key.
   */
  default void xpSilent(Player p, double xp, String rewardKey) {
    SkillRuntimeGuards.grantXpSilent(this, p, xp, rewardKey);
  }

  /**
   * Emits spatial xp pulses around a world location.
   */
  default void xp(Location at, double xp, int rad, long duration) {
    XP.spatialXP(at, this, xp, rad, duration);
    vfxXP(at);
  }

  /**
   * Grants knowledge points for this skill.
   */
  default void knowledge(Player p, long k) {
    SkillRuntimeGuards.grantKnowledge(this, p, k);
  }

  /**
   * Opens the skill GUI and optionally enforces blacklist permission checks.
   */
  default boolean openGui(Player player, boolean checkPermissions) {
    if (checkPermissions && hasBlacklistPermission(player, this)) {
      return false;
    } else {
      openGui(player);
      return true;
    }
  }

  /**
   * Opens page 0 of this skill GUI.
   */
  default void openGui(Player player) {
    openGui(player, 0);
  }

  /**
   * Opens a specific page of this skill GUI.
   */
  default void openGui(Player player, int page) {
    SkillGuiSupport.openGui(this, player, page);
  }
}
