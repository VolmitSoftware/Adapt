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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;

public class UnarmedComboChain extends SimpleAdaptation<UnarmedComboChain.Config> {
  private final Map<UUID, ComboState> combos = playerState();

  public UnarmedComboChain() {
    super("unarmed-combo-chain");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chainmail_boots"));
    setInterval(1800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_combo_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_unarmed_combo_5k", "unarmed.combo-chain.total-combo-hits", 5000, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_powder"))
        .key("challenge_unarmed_combo_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_rod"))
        .key("challenge_unarmed_combo_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxStacks(level), 1);
    statLore(v, Form.f(getDamagePerStack(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getComboWindowMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    ItemRef hand = p.inventory().mainHand();
    if (isMelee(hand)) {
      dropCombo(p);
      return;
    }

    long now = System.currentTimeMillis();
    int level = attack.level();
    ComboState state = combos.computeIfAbsent(p.uuid(), id -> new ComboState());
    long windowMillis = getComboWindowMillis(level);
    if (now - state.lastHitMillis > windowMillis) {
      state.stacks = 0;
    }

    state.lastHitMillis = now;
    state.stacks = Math.min(getMaxStacks(level), state.stacks + 1);
    AbilityGlyphs.duration(p.uuid(), this, now, windowMillis, state.stacks, getMaxStacks(level));

    double bonus = state.stacks * getDamagePerStack(level);
    e.setDamage(e.damage() + bonus);
    playComboFeedback(e.entity().location(), state.stacks, getMaxStacks(level));
    xp(p, bonus * getConfig().xpPerBonusDamage);
    addStat(p, "unarmed.combo-chain.total-combo-hits", 1);

    if (state.stacks >= 10) {
      grantOnce(p, "challenge_unarmed_combo_10");
    }
    if (state.stacks >= 25) {
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
    if (!hasActiveAdaptation(p) || isMelee(p.inventory().mainHand())) {
      return;
    }

    ComboState state = combos.get(p.uuid());
    if (state == null) {
      return;
    }

    long now = System.currentTimeMillis();
    if (now - state.lastHitMillis > getConfig().missResetGraceMillis) {
      dropCombo(p);
    }
  }

  private void dropCombo(PlayerRef p) {
    ComboState state = combos.remove(p.uuid());
    if (state != null) {
      AbilityGlyphs.clear(p.uuid(), this, HudGlyph.DURATION);
    }
    if (state == null || state.stacks < 3) {
      return;
    }

    Loc loc = p.location().add(0, 1, 0);
    fx(loc, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D);
    timeline(loc)
        .duration(3)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> fx.sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4F, (float) (1.2D - (0.5D * progress))))
        .start();
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

  private void playComboFeedback(Loc hitLocation, int stacks, int maxStacks) {
    if (stacks >= maxStacks) {
      Loc center = hitLocation.add(0, 1, 0);
      fx(center, FxPriority.COMBAT).sound(Sounds.BLOCK_ANVIL_PLACE, 0.55F, 1.7F);
      timeline(center)
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
    FxEmitter fx = fx(hitLocation.add(0, 0.9, 0), FxPriority.COMBAT)
        .particle(tier, count, 0, 0, 0, 0.22D, 0.1D)
        .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55F, pitch);
    if (stacks >= maxStacks - 1) {
      fx.particle(Particles.WAX_ON, 2, 0, 1.0D, 0, 0.2D, 0.05D);
    }
  }


  @ConfigDescription("Consecutive unarmed hits build combo stacks for increased punch damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Stacks Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxStacksBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Stacks Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxStacksFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerStackBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerStackFactor = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Combo Window Millis Base for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double comboWindowMillisBase = 1300;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Combo Window Millis Factor for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double comboWindowMillisFactor = 1400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Miss Reset Grace Millis for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long missResetGraceMillis = 280;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Unarmed Combo Chain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 4.1;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 6;
      initialCost = 4;
    }
  }

  private static class ComboState {
    private int stacks = 0;
    private long lastHitMillis = 0L;
  }
}
