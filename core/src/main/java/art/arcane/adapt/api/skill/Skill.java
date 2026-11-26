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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.Component;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.tick.Ticked;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;

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

  /** What this skill needs from the Minecraft version; without it the whole skill is left out. */
  default List<PlatformNeed> getPlatformNeeds() {
    return List.of();
  }

  default String getLocalizedName() {
    return Form.capitalize(getName());
  }

  /**
   * @return icon glyph/emoji prefix used in UI.
   */
  String getEmojiName();

  /**
   * @return base icon for this skill.
   */
  Id getIcon();

  /**
   * @return localized skill description.
   */
  String getDescription();

  /**
   * @return recipes registered by this skill.
   */
  KList<RecipeSpec> getRecipes();

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
   * @return all adaptations currently attached to this skill.
   */
  KList<Adaptation<?>> getAdaptations();

  /**
   * Upgraded adaptations this skill owns. They run, load config and register like the skill's own adaptations, but a
   * player can only learn them on a mutated skill line that contains this skill.
   */
  default List<Adaptation<?>> getUpgrades() {
    return List.of();
  }

  /**
   * The mutation blocks this skill brings to every mutation it is part of, the ones the running server supports. They
   * run, load config and register like adaptations, but no player can learn them.
   */
  default List<MutationBlock<?>> getBlocks() {
    return List.of();
  }

  /** Blocks this skill declares that the running Minecraft version cannot run; they stay inert. */
  default List<MutationBlock<?>> getUnavailableBlocks() {
    return List.of();
  }

  /**
   * The signatures of the mutations this skill leads, the ones the running server supports. They run, load config and
   * register like adaptations, but no player learns them: each reads the level of the mutated adaptation it replaces.
   */
  default List<Signature<?>> getSignatures() {
    return List.of();
  }

  /** Every adaptation {@code skill} owns: its own adaptations, then its upgrades, mutation blocks and signatures. */
  static List<Adaptation<?>> ownedAdaptations(Skill<?> skill) {
    List<Adaptation<?>> upgrades = skill.getUpgrades();
    List<MutationBlock<?>> blocks = skill.getBlocks();
    List<Signature<?>> signatures = skill.getSignatures();
    if ((upgrades == null || upgrades.isEmpty()) && (blocks == null || blocks.isEmpty())
        && (signatures == null || signatures.isEmpty())) {
      return skill.getAdaptations();
    }
    List<Adaptation<?>> owned = new ArrayList<>(skill.getAdaptations().size() + 12);
    owned.addAll(skill.getAdaptations());
    if (upgrades != null) {
      owned.addAll(upgrades);
    }
    if (blocks != null) {
      owned.addAll(blocks);
    }
    if (signatures != null) {
      owned.addAll(signatures);
    }
    return owned;
  }

  /** Whether the adaptation is one of {@code skill}'s own adaptations, upgrades, mutation blocks or signatures. */
  static boolean owns(Skill<?> skill, Adaptation<?> adaptation) {
    List<Adaptation<?>> upgrades = skill.getUpgrades();
    List<MutationBlock<?>> blocks = skill.getBlocks();
    List<Signature<?>> signatures = skill.getSignatures();
    return skill.getAdaptations().contains(adaptation) || (upgrades != null && upgrades.contains(adaptation))
        || (blocks != null && blocks.contains(adaptation)) || (signatures != null && signatures.contains(adaptation));
  }

  /** The named groups the skill's pages show its adaptations in; empty for one plain list. */
  default List<AdaptationGroup> getAdaptationGroups() {
    return List.of();
  }

  default List<List<String>> getAdaptationRowGroups() {
    List<List<String>> rows = new ArrayList<>();
    for (AdaptationGroup group : getAdaptationGroups()) {
      rows.add(group.adaptations());
    }
    return rows;
  }

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
   * Returns true when this player has use permission for this skill via
   * permission.
   */
  default boolean hasUsePermission(PlayerRef p, Skill<?> s) {
    return SkillRuntimeGuards.hasUsePermission(p, s);
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
  default void xp(PlayerRef p, double xp) {
    xp(p, xp, null);
  }

  /**
   * Grants visible xp at the player's location using an optional reward key.
   */
  default void xp(PlayerRef p, double xp, String rewardKey) {
    xp(p, p == null ? null : p.location(), xp, rewardKey);

  }

  /**
   * Grants visible xp at a specific location.
   */
  default void xp(PlayerRef p, Loc at, double xp) {
    xp(p, at, xp, null);
  }

  /**
   * Grants visible xp at a specific location using an optional reward key.
   */
  default void xp(PlayerRef p, Loc at, double xp, String rewardKey) {
    SkillRuntimeGuards.grantXp(this, p, at, xp, rewardKey, false, true);
  }

  /**
   * Grants silent xp at a specific location (with optional burst visuals).
   */
  default void xpS(PlayerRef p, Loc at, double xp) {
    xpS(p, at, xp, null);
  }

  /**
   * Grants silent xp at a specific location using an optional reward key.
   */
  default void xpS(PlayerRef p, Loc at, double xp, String rewardKey) {
    SkillRuntimeGuards.grantXp(this, p, at, xp, rewardKey, true, true);
  }

  /**
   * Grants silent xp without visuals.
   */
  default void xpSilent(PlayerRef p, double xp) {
    xpSilent(p, xp, null);
  }

  /**
   * Grants silent xp without visuals using an optional reward key.
   */
  default void xpSilent(PlayerRef p, double xp, String rewardKey) {
    SkillRuntimeGuards.grantXpSilent(this, p, xp, rewardKey);
  }

  /**
   * Emits spatial xp pulses around a world location.
   */
  default void xp(Loc at, double xp, int rad, long duration) {
    XP.spatialXP(at, this, xp, rad, duration);
    if (AdaptConfig.get().isUseEnchantmentTableParticleForActiveEffects()) {
      Fx.now(this, at, FxPriority.AMBIENT).particle(Particles.ENCHANTMENT_TABLE, 3, 0, 1.7, 0, 0.1, 3);
    }
  }

  /**
   * Grants knowledge points for this skill.
   */
  default void knowledge(PlayerRef p, long k) {
    SkillRuntimeGuards.grantKnowledge(this, p, k);
  }

  /**
   * Opens the skill GUI and optionally enforces use permission checks.
   */
  default boolean openGui(PlayerRef player, boolean checkPermissions) {
    if (checkPermissions && !hasUsePermission(player, this)) {
      return false;
    } else {
      openGui(player);
      return true;
    }
  }

  /**
   * Opens page 0 of this skill GUI.
   */
  default void openGui(PlayerRef player) {
    openGui(player, 0);
  }

  /**
   * Opens a specific page of this skill GUI.
   */
  default void openGui(PlayerRef player, int page) {
    SkillGuiSupport.openGui(this, player, page);
  }
}
