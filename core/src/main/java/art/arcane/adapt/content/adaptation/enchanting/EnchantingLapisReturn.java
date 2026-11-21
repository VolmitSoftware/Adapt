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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingLapisReturn extends SimpleAdaptation<EnchantingLapisReturn.Config> {
  private static final Id LAPIS_LAZULI = Id.minecraft("lapis_lazuli");
  private final Cooldowns cooldown = cooldowns();

  public EnchantingLapisReturn() {
    super("enchanting-lapis-return");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lapis_lazuli"));
    setInterval(20999);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lapis_lazuli"))
        .key("challenge_enchanting_lapis_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lapis_block"))
            .key("challenge_enchanting_lapis_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_lapis_100", "enchanting.lapis-return.lapis-saved", 100, 300);
    registerMilestone("challenge_enchanting_lapis_2500", "enchanting.lapis-return.lapis-saved", 2500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(EnchantingMessages.LAPIS_RETURN_LORE1));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EnchantItemEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0 || e.isCancelled() || !paysLapis(p)) {
      return;
    }

    InventoryRef inventory = p.openMenuInventory();
    if (inventory == null) {
      return;
    }
    int lapisBefore = lapisAmount(inventory);
    int cost = lapisCost(e.button());
    if (cost <= 0 || lapisBefore < cost || e.expLevelCost() > p.level() || e.enchantsToAdd().isEmpty()) {
      return;
    }
    if (!cooldown.isReady(p.uuid(), 20000L) || ThreadLocalRandom.current().nextDouble() >= getRefundChance(level)) {
      return;
    }

    Refund refund = new Refund(e, inventory, level, lapisBefore, p.enchantmentSeed());
    Tasks.runEntity(p, () -> completeRefund(refund), 1);
  }

  static int refundAmount(Refund refund) {
    EnchantItemEvent event = refund.event();
    PlayerRef player = event.player();
    if (event.isCancelled() || !player.isOnline() || !paysLapis(player) || event.enchantsToAdd().isEmpty()
        || player.enchantmentSeed() == refund.enchantmentSeed()
        || !refund.inventory().equals(player.openMenuInventory())) {
      return 0;
    }
    int cost = lapisCost(event.button());
    int consumed = refund.lapisBefore() - lapisAmount(refund.inventory());
    if (cost <= 0 || consumed != cost) {
      return 0;
    }
    return Math.max(0, Math.min(refund.level(), consumed));
  }

  private void completeRefund(Refund refund) {
    PlayerRef player = refund.event().player();
    UUID playerId = player.uuid();
    if (!cooldown.isReady(playerId, 20000L)) {
      return;
    }
    int amount = refundAmount(refund);
    if (amount <= 0) {
      return;
    }

    cooldown.mark(playerId, 20000L);
    Loc drop = player.location();
    List<ItemRef> overflow = player.inventory().addItem(AdaptCore.platform().host().items().create(LAPIS_LAZULI, amount));
    for (ItemRef leftover : overflow) {
      player.world().dropItemNaturally(drop.position(), leftover);
    }
    addStat(player, "enchanting.lapis-return.lapis-saved", amount);
    lapisRefundFx(refund.event(), drop);
  }

  private static boolean paysLapis(PlayerRef player) {
    return player.gameMode() == GameMode.SURVIVAL || player.gameMode() == GameMode.ADVENTURE;
  }

  private static int lapisCost(int button) {
    return button >= 0 && button < 3 ? button + 1 : 0;
  }

  private static int lapisAmount(InventoryRef inventory) {
    ItemRef lapis = inventory.item(1);
    return lapis != null && LAPIS_LAZULI.equals(lapis.type()) ? Math.max(0, lapis.amount()) : 0;
  }

  private double getRefundChance(int level) {
    return Math.min(getConfig().maxRefundChance, getConfig().refundChanceBase + (getLevelPercent(level) * getConfig().refundChanceFactor));
  }

  private void lapisRefundFx(EnchantItemEvent e, Loc drop) {
    Rgb lapis = new Rgb(38, 97, 156);
    ItemRef fragment = AdaptCore.platform().host().items().create(LAPIS_LAZULI);
    timeline(drop)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.REDSTONE, (0.6D * (1.0D - progress)) + 0.15D, 10, 0.1D + (0.8D * progress), new ParticleData.Dust(lapis, 1.0F));
          if (tick == 0) {
            f.particle(Particles.itemCrack(fragment), 5, 0, 0.2D, 0, 0.25D, 0.05D)
                .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 0.9F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.3F);
          }
        })
        .start();
    fx(e.table().location().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
        .particle(Particles.GLOW, 3, 0, 0, 0, 0.1D, 0.02D);
  }


  record Refund(EnchantItemEvent event, InventoryRef inventory, int level, int lapisBefore, int enchantmentSeed) {
  }

  @ConfigDescription("Chance to refund paid lapis when enchanting, capped at the amount consumed.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance to refund lapis on an enchant.", impact = "Higher values refund lapis more often at every level.")
    double refundChanceBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional refund chance granted as the adaptation levels up.", impact = "Higher values make high levels refund lapis more often.")
    double refundChanceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Upper bound on the lapis refund chance.", impact = "Higher values allow the scaled chance to climb further.")
    double maxRefundChance = 0.4;

    public Config() {
      baseCost = 5;
      costFactor = 0.9;
      maxLevel = 3;
    }
  }
}
