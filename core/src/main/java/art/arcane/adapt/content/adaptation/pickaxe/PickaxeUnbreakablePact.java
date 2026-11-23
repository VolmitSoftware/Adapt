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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PickaxeUnbreakablePact extends SimpleAdaptation<PickaxeUnbreakablePact.Config> {
  private final Map<UUID, Long> noticeTimes = playerState();

  public PickaxeUnbreakablePact() {
    super("pickaxe-unbreakable-pact");
    registerConfiguration(PickaxeUnbreakablePact.Config.class);
    setIcon(Id.minecraft("anvil"));
    setInterval(9122);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_pickaxe"))
        .key("challenge_pickaxe_pact_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_pact_100", "pickaxe.unbreakable-pact.saves", 100, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.NOTICE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.UNBREAKABLE_PACT_LORE1));
    statLore(v, Form.pc(getIgnoreChance(level), 0), 2);
  }

  private double getIgnoreChance(int level) {
    return Math.min(getConfig().maxIgnoreChance, level * getConfig().ignoreChancePerLevel);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerItemDamageEvent e) {
    ItemRef item = e.item();
    if (!isPickaxe(item)) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (preferenceEnabled(p, PickaxePreferences.NOTICE)) {
      int maximum = item.maxDamage();
      long now = System.currentTimeMillis();
      if (maximum - item.damage() - e.damage() <= maximum / 10
          && now - noticeTimes.getOrDefault(p.uuid(), 0L) >= 5_000L) {
        noticeTimes.put(p.uuid(), now);
        AdaptHud.actionBar(p, AdaptLanguage.text(PickaxePreferences.LOW_DURABILITY));
      }
    }

    if (M.r(getIgnoreChance(level))) {
      e.setCancelled(true);
      addStat(p, "pickaxe.unbreakable-pact.damage-ignored", e.damage());
      if (M.r(0.2)) {
        fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
            .particle(Particles.WAX_ON, 2, 0, 0.2, 0, 0.15, 0.01);
      }
      return;
    }

    int maxDurability = item.maxDamage();
    if (item.damage() + e.damage() < maxDurability) {
      return;
    }

    e.setCancelled(true);
    item.setDamage(maxDurability - 1);
    addStat(p, "pickaxe.unbreakable-pact.saves", 1);
    fx(p.location().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particles.CRIT, 10, 0, 0.2, 0, 0.3, 0.1)
        .particle(Particles.CRIT_MAGIC, 6, 0, 0.2, 0, 0.3, 0.05)
        .ring(Particles.WAX_ON, 0.5D, 12, 0.1D)
        .chord(Sounds.BLOCK_ANVIL_PLACE, 0.4f, 1.8f, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 0.8f, Sounds.ITEM_TRIDENT_RETURN, 0.3f, 1.0f);
  }

  @ConfigDescription("Your pickaxe refuses to break, surviving at 1 durability.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance per level to ignore pickaxe durability loss entirely.", impact = "Higher values make the pickaxe lose durability less often at higher levels.")
    double ignoreChancePerLevel = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total chance to ignore durability loss.", impact = "Higher values allow more durability loss to be ignored at max level.")
    double maxIgnoreChance = 0.25;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
