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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import java.util.List;
import java.util.Map;

public class HunterSnareLine extends SimpleAdaptation<HunterSnareLine.Config> {
  public static final Id ID = Id.adapt("hunter/snare_line");
  public static final CustomItem.Definition<CustomItem.Plain> ITEM = CustomItem.builder(Id.adapt("snare_line"), HunterSnares.TRIPWIRE_HOOK)
      .recognisedBy(HunterSnares::isSnareItem)
      .build();
  private static final Rgb SNARE_GREEN = new Rgb(120, 180, 90);
  private final HunterSnares snares = new HunterSnares(this, (PlayerRef owner, LivingRef snared) -> {
    addStat(owner, "hunter.snare-line.mobs-snared", 1);
    xp(owner, getConfig().xpPerSnare);
  });

  public HunterSnareLine() {
    super(ID);
    registerConfiguration(Config.class);
    registerItem(ITEM);
    setIcon(HunterSnares.TRIPWIRE_HOOK);
    setInterval(250);
    registerRecipe(RecipeSpec.shaped(Id.adapt("hunter-snare"), List.of(
            "S S",
            "SIS",
            "S S"), Map.of('S', Ingredient.of(Id.minecraft("string")), 'I', Ingredient.of(Id.minecraft("iron_ingot"))), HunterSnares.buildSnareItem(2)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tripwire_hook"))
        .key("challenge_hunter_snare_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lead"))
            .key("challenge_hunter_snare_2k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_snare_200", "hunter.snare-line.mobs-snared", 200, 400);
    registerMilestone("challenge_hunter_snare_2k", "hunter.snare-line.mobs-snared", 2000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      snares.forget(player.player().uuid());
      HudStatus.clear(player.player().uuid(), this, HudGlyph.CHARGES);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRootDurationTicks(level) / 20.0D, 1), 1);
    statLore(v, TextFormat.YELLOW, "+ ", getCharges(level), 2);
    v.addLore(TextFormat.GRAY + "- " + Text.key("adapt.hunter.snare_line.lore3").resolve());
  }

  static int scaleInt(double levelPercent, int base, double factor) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  int getRootDurationTicks(int level) {
    return scaleInt(getLevelPercent(level), getConfig().rootDurationTicksBase, getConfig().rootDurationTicksFactor);
  }

  int getCharges(int level) {
    return scaleInt(getLevelPercent(level), getConfig().chargesBase, getConfig().chargesFactor);
  }

  static void snareLimitFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.eyeLocation(), FxPriority.GAMEPLAY).sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
  }

  static void snarePlacedFx(Adaptation<?> owner, Loc location, double radius) {
    Fx.now(owner, location, FxPriority.GAMEPLAY)
        .dustRing(SNARE_GREEN, radius, 14, 1.0F)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.2D, 0, 0.3D, 0.05D)
        .chord(Sounds.BLOCK_TRIPWIRE_ATTACH, 0.7F, 1.1F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.3F);
  }

  static void snareArmedFx(Adaptation<?> owner, Loc location, double radius) {
    Fx.now(owner, location, FxPriority.AMBIENT).dustRing(SNARE_GREEN, radius, 10, 0.8F);
  }

  static void snaredFx(Adaptation<?> owner, LivingRef monster) {
    Fx.now(owner, monster.location().add(0, monster.height() * 0.4D, 0), FxPriority.COMBAT)
        .particle(Particles.CRIT, 6, 0, 0.3D, 0, 0.3D, 0.0D)
        .dustBurst(SNARE_GREEN, 6, 0.3D, 1.0F)
        .chord(Sounds.BLOCK_TRIPWIRE_CLICK_ON, 0.8F, 0.8F, Sounds.ITEM_LEAD_TIED, 0.6F, 0.9F);
  }

  static void snareBrokenFx(Adaptation<?> owner, Loc location) {
    Fx.now(owner, location, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 6, 0, 0.2D, 0, 0.25D, 0.02D)
        .sound(Sounds.BLOCK_TRIPWIRE_DETACH, 0.6F, 0.9F);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    snares.forget(e.player().uuid());
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    snares.place(e, settings(), this::getCharges, this::getRootDurationTicks);
  }

  @Override
  public boolean hasTickDemand() {
    return !snares.isEmpty();
  }

  @Override
  public void onTick() {
    snares.tick(settings());
  }

  private HunterSnares.Settings settings() {
    Config config = getConfig();
    return new HunterSnares.Settings(config.triggerRadius, config.rootAmplifier, config.rearmBufferMillis,
        config.snareLifetimeTicks, config.maxSnaresPerPlayer, config.maxActiveSnares, config.maxTargetsPerScan);
  }

  @ConfigDescription("Craft a string-and-iron snare; hostile mobs that cross it are rooted briefly.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base snare root duration in ticks for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootDurationTicksBase = 30;
    @ConfigDoc(value = "Additional snare root duration in ticks gained across levels for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rootDurationTicksFactor = 50;
    @ConfigDoc(value = "Base trigger charges per crafted snare for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int chargesBase = 3;
    @ConfigDoc(value = "Additional trigger charges gained across levels for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargesFactor = 5;
    @ConfigDoc(value = "Snare trigger radius in blocks for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double triggerRadius = 1.6;
    @ConfigDoc(value = "Slowness-equivalent amplifier mapped to a movement speed reduction of 15 percent per level, capped at a full stop, while a mob is rooted for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootAmplifier = 6;
    @ConfigDoc(value = "Milliseconds a snared mob is immune to re-triggering the same snare for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long rearmBufferMillis = 500;
    @ConfigDoc(value = "Ticks a placed snare persists before decaying for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long snareLifetimeTicks = 2400;
    @ConfigDoc(value = "Maximum concurrent snares one player may keep placed for the Hunter Snare Line adaptation.", impact = "Lower values reduce tracked snare state.")
    int maxSnaresPerPlayer = 4;
    @ConfigDoc(value = "Maximum concurrent snares processed by this server for the Hunter Snare Line adaptation.", impact = "Lower values reduce scan scheduling work.")
    int maxActiveSnares = 64;
    @ConfigDoc(value = "Maximum mobs scheduled for rooting by one snare per scan for the Hunter Snare Line adaptation.", impact = "Lower values reduce burst entity work in crowds.")
    int maxTargetsPerScan = 8;
    @ConfigDoc(value = "Xp granted per mob snared for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerSnare = 6;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
