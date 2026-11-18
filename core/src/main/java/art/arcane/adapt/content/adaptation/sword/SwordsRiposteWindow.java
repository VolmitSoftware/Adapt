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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;

public class SwordsRiposteWindow extends SimpleAdaptation<SwordsRiposteWindow.Config> {
  private static final Rgb GOLD = Rgb.of(0xFFC94A);
  private static final Id SHIELD = Id.minecraft("shield");
  private final Map<UUID, Long> riposteUntil = playerState();

  public SwordsRiposteWindow() {
    super("sword-riposte-window");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_chestplate"));
    setInterval(2100);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_riposte_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_swords_riposte_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_riposte_200", "swords.riposte.ripostes-landed", 200, 400);
    registerMilestone("challenge_swords_riposte_2500", "swords.riposte.ripostes-landed", 2500, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_swords_riposte_3in5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getWindowMillis(level), 1), 1);
    statLore(v, Form.pc(getDamageBonus(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (e.entity() instanceof PlayerRef defender) {
      armRiposte(defender);
    }

    if (!(e.damager() instanceof PlayerRef attacker)) {
      return;
    }

    long now = System.currentTimeMillis();
    long until = riposteUntil.getOrDefault(attacker.uuid(), 0L);
    if (until < now) {
      return;
    }

    MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    attacker = combat.attacker();
    e.setDamage(e.damage() * (1D + getDamageBonus(combat.level())));
    riposteUntil.remove(attacker.uuid());
    fx(attacker.location().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particles.SWEEP_ATTACK, 2, 0, 0, 0, 0.1D, 0.02D)
        .particle(Particles.CRIT, 10, 0, 0, 0, 0.3D, 0.1D)
        .dustBurst(GOLD, 3, 0.3D, 1.0F)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 0.7F, 1.6F, Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.2F, Sounds.BLOCK_ANVIL_LAND, 0.25F, 1.8F);
    xp(attacker, e.damage() * getConfig().xpPerBuffedDamage);
    addStat(attacker, "swords.riposte.ripostes-landed", 1);

    long riposteWindowStart = getStorageLong(attacker, "riposteWindowStart", 0L);
    int riposteWindowCount = getStorageInt(attacker, "riposteWindowCount", 0);
    if (now - riposteWindowStart > 5000L) {
      riposteWindowStart = now;
      riposteWindowCount = 1;
    } else {
      riposteWindowCount++;
    }
    setStorage(attacker, "riposteWindowStart", riposteWindowStart);
    setStorage(attacker, "riposteWindowCount", riposteWindowCount);
    if (riposteWindowCount >= 3 && grantOnce(attacker, "challenge_swords_riposte_3in5")) {
      fx(attacker.location().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 10, 0.4D)
          .particle(Particles.FLASH, 1, 0, 0, 0, 0, 0)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  private void armRiposte(PlayerRef defender) {
    boolean hasShield = SHIELD.equals(defender.inventory().offHand().type())
        || SHIELD.equals(defender.inventory().mainHand().type());
    int level = getActiveLevel(defender);
    if (level <= 0 || !defender.isBlocking() || !hasShield) {
      return;
    }

    riposteUntil.put(defender.uuid(), System.currentTimeMillis() + getWindowMillis(level));
    int windowTicks = Math.max(4, (int) (getWindowMillis(level) / 50L));
    timeline(defender)
        .duration(windowTicks)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20D)
        .frame((fx, tick, progress) -> {
          fx.dustRing(GOLD, 1.2D - (0.9D * progress), 8, 0.9F);
          if (tick == 0) {
            fx.chord(Sounds.ITEM_SHIELD_BLOCK, 0.6F, 0.9F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.2F);
          }
        })
        .start();
  }

  private long getWindowMillis(int level) {
    return Math.max(150L, (long) Math.round(getConfig().windowMillisBase + (getLevelPercent(level) * getConfig().windowMillisFactor)));
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBonusBase + (getLevelPercent(level) * getConfig().damageBonusFactor);
  }


  @ConfigDescription("Blocking with a shield arms a short riposte window for your next sword strike.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Millis Base for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windowMillisBase = 350;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Millis Factor for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windowMillisFactor = 550;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Base for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Factor for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusFactor = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Buffed Damage for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBuffedDamage = 1.8;

    public Config() {
      costFactor = 0.71;
      initialCost = 4;
    }
  }
}
