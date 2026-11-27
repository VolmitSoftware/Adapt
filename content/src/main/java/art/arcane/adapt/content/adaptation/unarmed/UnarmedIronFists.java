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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UnarmedIronFists extends SimpleAdaptation<UnarmedIronFists.Config> {
  public static final Id ID = Id.adapt("unarmed/iron_fists");
  private final Map<UUID, Long> lastPunchedBlock = playerState();

  public UnarmedIronFists() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    setInterval(4622);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_iron_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_iron_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_iron_1k", "unarmed.iron-fists.iron-hits", 1000, 400);
    registerMilestone("challenge_unarmed_iron_10k", "unarmed.iron-fists.iron-hits", 10000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0 || !preferenceEnabled(player.player(), UnarmedPreferences.BREAKING)) {
      AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.COMBAT, UnarmedPreferences.BREAKING);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDamageBonus(level), 1), 1);
    statLore(v, getHasteAmplifier(level) + 1, 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (ItemKinds.isTool(p.inventory().mainHand()) || ItemKinds.isTool(p.inventory().offHand())) {
      return;
    }

    if (!preferenceEnabled(p, UnarmedPreferences.COMBAT)) { return; }
    e.setDamage(e.damage() + getDamageBonus(attack.level()));
    xp(p, getConfig().xpPerHit);
    addStat(p, "unarmed.iron-fists.iron-hits", 1);
    double levelPercent = getLevelPercent(attack.level());
    if (levelPercent >= 0.4) {
      fx(e.entity(), FxPriority.COMBAT)
          .particle(Particles.CRIT, 3, 0, 1.0D, 0, 0.15D, 0.0D)
          .particle(Particles.ELECTRIC_SPARK, 1, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sounds.BLOCK_ANVIL_LAND, 0.3F, (float) (1.4D + (levelPercent * 0.3D)));
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, UnarmedPreferences.BREAKING) || ItemKinds.isItem(p.inventory().mainHand())) {
      return;
    }

    BlockRef block = e.block();
    if (!UnarmedFists.isSoftBlock(block, getConfig().softBlockMaxHardness)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, block.location());
    if (context == null) {
      return;
    }

    int hasteDurationTicks = getConfig().hasteDurationTicks;
    if (hasteDurationTicks <= 0) {
      return;
    }

    AdaptAttributeService.get().applyTimed(p, getId().toString(), "haste", Attributes.BLOCK_BREAK_SPEED, breakSpeedBonus(getHasteAmplifier(context.level())), AttributeOperation.ADD_MULTIPLIED_TOTAL, hasteDurationTicks);

    long key = blockKey(block.x(), block.y(), block.z());
    Long last = lastPunchedBlock.get(p.uuid());
    if (last == null || last != key) {
      lastPunchedBlock.put(p.uuid(), key);
      fx(block.center(), FxPriority.GAMEPLAY)
          .particle(Particles.blockCrack(block.state()), 4, 0, 0, 0, 0.1D, 0.02D)
          .particle(Particles.WAX_ON, 2, 0, 0, 0, 0.1D, 0.0D)
          .sound(Sounds.BLOCK_STONE_HIT, 0.35F, 0.8F);
    }
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBase + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private int getHasteAmplifier(int level) {
    return hasteAmplifier(getLevelPercent(level), getConfig().hasteAmplifierFactor);
  }

  static int hasteAmplifier(double levelPercent, double amplifierFactor) {
    return Math.max(0, (int) Math.round(levelPercent * amplifierFactor));
  }

  static double breakSpeedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  @ConfigDescription("Bare fists hit harder and punch through soft blocks faster.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base flat bare-hand damage bonus at level 1.", impact = "Higher values make every bare-hand hit stronger.")
    double damageBase = 0.5;
    @ConfigDoc(value = "Additional flat damage bonus granted at max level.", impact = "Higher values make bare-hand hits stronger as levels increase.")
    double damageFactor = 2.5;
    @ConfigDoc(value = "Maximum block hardness still considered a soft block.", impact = "Higher values let the punch-haste apply to tougher blocks.")
    double softBlockMaxHardness = 0.8;
    @ConfigDoc(value = "Haste duration in ticks while punching soft blocks.", impact = "Higher values keep the dig-speed buff active longer.")
    int hasteDurationTicks = 25;
    @ConfigDoc(value = "Haste amplifier granted at max level while punching soft blocks.", impact = "Higher values speed up bare-hand block breaking.")
    double hasteAmplifierFactor = 2;
    @ConfigDoc(value = "XP granted per bare-hand hit.", impact = "Higher values speed up unarmed skill progression from hits.")
    double xpPerHit = 2.4;

    public Config() {
      costFactor = 0.5;
      baseCost = 2;
      initialCost = 1;
    }
  }
}
