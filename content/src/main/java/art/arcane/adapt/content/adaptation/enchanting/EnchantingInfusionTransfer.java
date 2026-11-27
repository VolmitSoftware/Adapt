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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class EnchantingInfusionTransfer extends SimpleAdaptation<EnchantingInfusionTransfer.Config> {
  public static final Id ID = Id.adapt("enchanting/infusion_transfer");
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", Text.key("adapt.enchanting.preferences.enchantinginfusiontransfer.confirmation"), CommonPreferences.Toggle.OFF);


  public EnchantingInfusionTransfer() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    setInterval(2300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_enchanting_infusion_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_book"))
            .key("challenge_enchanting_infusion_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_infusion_25", "enchanting.infusion-transfer.transfers", 25, 400);
    registerMilestone("challenge_enchanting_infusion_250", "enchanting.infusion-transfer.transfers", 250, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSourceSurvival(level), 0), 1);
    statLore(v, TextFormat.YELLOW, "* ", getXpCost(level), 2);
  }

  static double infusionSourceSurvival(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static int infusionXpCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  private double getSourceSurvival(int level) {
    return infusionSourceSurvival(getConfig().survivalBase, getConfig().survivalFactor, getConfig().maxSurvival, getLevelPercent(level));
  }

  private int getXpCost(int level) {
    return infusionXpCost(getConfig().xpCostBase, getConfig().xpCostFactor, getConfig().minXpCost, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();
    if (e.rawSlot() != 0
        || e.click() != ClickKind.RIGHT
        || e.viewKind() != InventoryKind.ANVIL
        || ItemKinds.isItem(e.cursor())) {
      return;
    }

    InventoryRef top = p.openMenuInventory();
    if (top == null) {
      return;
    }
    ItemRef base = top.item(0);
    ItemRef source = top.item(1);
    if (!EnchantingBooks.isTransferBase(base) || !ItemKinds.isItem(source)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (preferenceEnabled(p, CONFIRM) && !confirm(p, "infusion", base, source)) {
      e.setCancelled(true);
      return;
    }
    Id transfer = EnchantingBooks.pickTransferEnchant(base, source);
    if (transfer == null) {
      return;
    }

    e.setCancelled(true);
    int cost = getXpCost(level);
    if (p.level() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    int enchantLevel = EnchantingBooks.transferLevel(source, transfer);
    ItemRef newBase = base.copy();
    newBase.setEnchantment(transfer, enchantLevel);
    top.setItem(0, newBase);

    boolean survives = ThreadLocalRandom.current().nextDouble() < getSourceSurvival(level);
    top.setItem(1, survives ? EnchantingBooks.reduceSource(source, transfer) : null);

    p.setLevel(Math.max(0, p.level() - cost));
    addStat(p, "enchanting.infusion-transfer.transfers", 1);
    xp(p, getConfig().skillXpOnTransfer);
    HudStatus.show(p, TextFormat.LIGHT_PURPLE + Text.key("adapt.enchanting.infusion_transfer.message").withMarkup("enchantment", transfer.path().replace('_', ' ')).withMarkup("level", String.valueOf(enchantLevel)).resolve());
    transferFx(p);
    AdaptApi.scheduler().runEntity(p, p::updateInventory, 1);
  }

  private void transferFx(PlayerRef p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANT, (0.7D * (1.0D - progress)) + 0.15D, 1.5D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT, 4, 0, 0.5D, 0, 0.3D, 0.05D)
                .sound(Sounds.BLOCK_ANVIL_USE, 0.6F, 1.3F);
          }
          if (tick == 4) {
            f.column(Particles.END_ROD, 4, 1.3D)
                .chord(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.4F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
          }
        })
        .start();
  }


  @ConfigDescription("Right-click the base item in an anvil to move one enchantment onto it from the sacrifice item.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Survival Base for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalBase = 0.1;
    @ConfigDoc(value = "Controls Survival Factor for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalFactor = 0.9;
    @ConfigDoc(value = "Controls Max Survival for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxSurvival = 1.0;
    @ConfigDoc(value = "Controls Xp Cost Base for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostBase = 6;
    @ConfigDoc(value = "Controls Xp Cost Factor for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostFactor = 3;
    @ConfigDoc(value = "Controls Min Xp Cost for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minXpCost = 2;
    @ConfigDoc(value = "Controls Skill Xp On Transfer for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnTransfer = 20;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
