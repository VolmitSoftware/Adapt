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
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;


public class UnarmedComboChain extends SimpleAdaptation<UnarmedComboChain.Config> {
  public static final Id ID = Id.adapt("unarmed/combo_chain");
  private final UnarmedCombo combo = new UnarmedCombo(this, playerState());

  public UnarmedComboChain() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chainmail_boots"));
    setInterval(1800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_combo_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_unarmed_combo_5k", "unarmed.combo-chain.total-combo-hits", 5000, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_powder"))
        .key("challenge_unarmed_combo_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_rod"))
        .key("challenge_unarmed_combo_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxStacks(level), 1);
    statLore(v, Format.f(getDamagePerStack(level), 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getComboWindowMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    ItemRef hand = p.inventory().mainHand();
    if (ItemKinds.isMelee(hand)) {
      combo.drop(p);
      return;
    }

    long now = System.currentTimeMillis();
    int level = attack.level();
    int maxStacks = getMaxStacks(level);
    int stacks = combo.build(p, maxStacks, getComboWindowMillis(level), now);

    double bonus = stacks * getDamagePerStack(level);
    e.setDamage(e.damage() + bonus);
    feedback(this, e.entity().location(), stacks, maxStacks);
    xp(p, bonus * getConfig().xpPerBonusDamage);
    addStat(p, "unarmed.combo-chain.total-combo-hits", 1);

    if (stacks >= 10) {
      grantOnce(p, "challenge_unarmed_combo_10");
    }
    if (stacks >= 25) {
      grantOnce(p, "challenge_unarmed_combo_25");
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || ItemKinds.isMelee(p.inventory().mainHand())) {
      return;
    }

    combo.missed(p, getConfig().missResetGraceMillis, System.currentTimeMillis());
  }

  private int getMaxStacks(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxStacksBase + (getLevelPercent(level) * getConfig().maxStacksFactor)));
  }

  private double getDamagePerStack(int level) {
    return getConfig().damagePerStackBase + (getLevelPercent(level) * getConfig().damagePerStackFactor);
  }

  private long getComboWindowMillis(int level) {
    return Math.max(250, (long) Math.round(getConfig().comboWindowMillisBase + (getLevelPercent(level) * getConfig().comboWindowMillisFactor)));
  }

  public static void feedback(Adaptation<?> owner, Loc hitLocation, int stacks, int maxStacks) {
    if (stacks >= maxStacks) {
      Loc center = hitLocation.add(0, 1, 0);
      Fx.now(owner, center, FxPriority.COMBAT).sound(Sounds.BLOCK_ANVIL_PLACE, 0.55F, 1.7F);
      FxTimeline.at(owner, center)
          .duration(8)
          .priority(FxPriority.COMBAT)
          .frame((fx, tick, progress) -> {
            fx.helix(Particles.WAX_ON, 0.8D, 2.2D, 4, progress * Math.PI * 2.0D);
            if (tick == 0) {
              fx.sound(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.9F);
            }
          })
          .start();
      return;
    }

    float pitch = Math.min(2.0F, 0.85F + (stacks * 0.09F));
    ParticleSpec tier = stacks >= (maxStacks * 0.6D) ? Particles.WAX_ON : Particles.CRIT;
    int count = 4 + Math.min(14, stacks * 2);
    FxEmitter fx = Fx.now(owner, hitLocation.add(0, 0.9, 0), FxPriority.COMBAT)
        .particle(tier, count, 0, 0, 0, 0.22D, 0.1D)
        .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55F, pitch);
    if (stacks >= maxStacks - 1) {
      fx.particle(Particles.WAX_ON, 2, 0, 1.0D, 0, 0.2D, 0.05D);
    }
  }

  static void brokenFx(Adaptation<?> owner, Loc loc) {
    Fx.now(owner, loc, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D);
    FxTimeline.at(owner, loc)
        .duration(3)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> fx.sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4F, (float) (1.2D - (0.5D * progress))))
        .start();
  }

  @ConfigDescription("Consecutive unarmed hits build combo stacks for increased punch damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Max Stacks Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxStacksBase = 2;
    @ConfigDoc(value = "Controls Max Stacks Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxStacksFactor = 8;
    @ConfigDoc(value = "Controls Damage Per Stack Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerStackBase = 0.2;
    @ConfigDoc(value = "Controls Damage Per Stack Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerStackFactor = 0.85;
    @ConfigDoc(value = "Controls Combo Window Millis Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double comboWindowMillisBase = 1300;
    @ConfigDoc(value = "Controls Combo Window Millis Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double comboWindowMillisFactor = 1400;
    @ConfigDoc(value = "Controls Miss Reset Grace Millis for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long missResetGraceMillis = 280;
    @ConfigDoc(value = "Controls Xp Per Bonus Damage for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 4.1;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 6;
      initialCost = 2;
    }
  }
}
