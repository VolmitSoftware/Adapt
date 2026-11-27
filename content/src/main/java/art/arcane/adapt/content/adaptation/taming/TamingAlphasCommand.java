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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ContentIds;
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
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

public class TamingAlphasCommand extends SimpleAdaptation<TamingAlphasCommand.Config> {
  public static final Id ID = Id.adapt("tame/alphas_command");
  private final TamePackCommand pack = new TamePackCommand(this, cooldowns(), (PlayerRef p) -> {
    addStat(p, "taming.alphas-command.commands", 1);
    xp(p, getConfig().xpPerCommand);
  });

  public TamingAlphasCommand() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.taming.alphas_command");
    setIcon(Id.minecraft("bone"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_taming_command_250")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(ContentIds.itemOr(Id.minecraft("wolf_armor"), Id.minecraft("iron_horse_armor")))
            .key("challenge_taming_command_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_command_250", "taming.alphas-command.commands", 250, 400);
    registerMilestone("challenge_taming_command_2500", "taming.alphas-command.commands", 2500, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    pack.clear(player.player());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.TARGETS, TamingPreferences.BONES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getCommandRange(level), 1), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getFocusTicks(level) * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !p.isSneaking()) {
      return;
    }

    if (!TamePackCommand.BONE.equals(p.inventory().mainHand().type())) {
      return;
    }

    if (!(e.entity() instanceof LivingRef target) || !pack.command(p, target, this::tuning)) {
      return;
    }

    e.setCancelled(true);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (!TamePackCommand.isCommandGesture(e.action(), e.hand())) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() || !TamePackCommand.BONE.equals(p.inventory().mainHand().type())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    LivingRef target = pack.aim(p, getCommandRange(level));
    if (target == null || !pack.command(p, target, this::tuning)) {
      return;
    }

    e.setCancelled(true);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    pack.clear(e.player());
  }

  @Override
  public void unregister() {
    if (pack.close()) {
      super.unregister();
    }
  }

  private TamePackCommand.Tuning tuning(int level) {
    return new TamePackCommand.Tuning(getCommandRange(level), getFocusTicks(level), getConfig().focusSpeedAmplifier,
        getConfig().commandCooldownMillis, TamePackCommand.petLimit(getConfig().maxPets), true);
  }

  private double getCommandRange(int level) {
    return getConfig().commandRangeBase + (getLevelPercent(level) * getConfig().commandRangeFactor);
  }

  private int getFocusTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().focusTicksBase + (getLevelPercent(level) * getConfig().focusTicksFactor)));
  }

  static void commandFx(Adaptation<?> owner, Loc origin) {
    Fx.now(owner, origin, FxPriority.COMBAT)
        .ring(Particles.VILLAGER_HAPPY, 1.2D, 12, 0.3D)
        .chord(Sounds.ENTITY_WOLF_GROWL, 0.8F, 0.9F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.7F);
  }

  static void focusFx(Adaptation<?> owner, LivingRef pet) {
    Fx.now(owner, pet, FxPriority.COMBAT)
        .burst(Particles.CRIT, 4, 0.25D)
        .sound(Sounds.ENTITY_WOLF_GROWL, 0.6F, 1.2F);
  }

  @ConfigDescription("Sneak-left-click a target while holding a bone to make your nearby pets focus it.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Command Range Base for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double commandRangeBase = 8.0;
    @ConfigDoc(value = "Controls Command Range Factor for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double commandRangeFactor = 12.0;
    @ConfigDoc(value = "Controls Focus Ticks Base for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double focusTicksBase = 60;
    @ConfigDoc(value = "Controls Focus Ticks Factor for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double focusTicksFactor = 120;
    @ConfigDoc(value = "Amplifier of the speed and strength focus buff applied to commanded pets.", impact = "Higher values give commanded pets a stronger pursuit buff.")
    int focusSpeedAmplifier = 0;
    @ConfigDoc(value = "Cooldown in milliseconds between commands.", impact = "Higher values reduce how often the pack can be re-commanded.")
    long commandCooldownMillis = 3000;
    @ConfigDoc(value = "Xp granted each time the pack is commanded.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCommand = 12;
    @ConfigDoc(value = "Maximum pets commanded per activation, capped internally at 24.", impact = "Lower values reduce entity scheduling in very large packs.")
    int maxPets = 12;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
