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
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;


public class EnchantingSoulLink extends SimpleAdaptation<EnchantingSoulLink.Config> {
  public static final Id ID = Id.adapt("enchanting/soul_link");
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", Text.key("adapt.enchanting.preferences.enchantingsoullink.confirmation"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<EnchantingPreferences.Reserve> XP_RESERVE = EnchantingPreferences.reserve("xp-reserve", Text.key("adapt.enchanting.preferences.enchantingsoullink.xp-reserve"));


  private final EnchantingSoulVault vault = new EnchantingSoulVault(this, new EnchantingSoulVault.Feedback() {
    @Override
    public void refused(Loc at) {
      FxPresets.failFizzle(EnchantingSoulLink.this, at);
    }

    @Override
    public void linked(PlayerRef p) {
      markFx(p);
    }

    @Override
    public void saved(PlayerRef p) {
      addStat(p, "enchanting.soul-link.items-saved", 1);
    }

    @Override
    public void returned(PlayerRef p) {
      xp(p, getConfig().skillXpOnSave);
      savedFx(p);
    }
  });

  public EnchantingSoulLink() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("totem_of_undying"))
        .key("challenge_enchanting_soul_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_enchanting_soul_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_soul_10", "enchanting.soul-link.items-saved", 10, 400);
    registerMilestone("challenge_enchanting_soul_100", "enchanting.soul-link.items-saved", 100, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM, XP_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.YELLOW, "* ", getSaveCost(level), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getRemarkCooldownMs(level), 1), 2);
  }

  static int soulSaveCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  static long soulRemarkCooldownMs(double base, double factor, long min, double levelPercent) {
    return Math.max(min, Math.round(base - (levelPercent * factor)));
  }

  private int getSaveCost(int level) {
    return soulSaveCost(getConfig().saveCostBase, getConfig().saveCostFactor, getConfig().minSaveCost, getLevelPercent(level));
  }

  private long getRemarkCooldownMs(int level) {
    return soulRemarkCooldownMs(getConfig().remarkCooldownBase, getConfig().remarkCooldownFactor, getConfig().minRemarkCooldown, getLevelPercent(level));
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    BlockRef block = e.clickedBlock();
    if (block == null || !block.type().equals(EnchantingSoulVault.ANVIL)) {
      return;
    }

    if (e.useBlock() == UseResult.DENY) {
      return;
    }

    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!EnchantingSoulVault.isEnchanted(hand)) {
      return;
    }

    e.setCancelled(true);
    vault.link(p, block.location().add(0.5D, 1.0D, 0.5D), getRemarkCooldownMs(level),
        () -> !(preference(p, XP_RESERVE).amount() > 0 && p.level() - getSaveCost(level) < preference(p, XP_RESERVE).amount()
            || preferenceEnabled(p, CONFIRM) && !confirm(p, "link", hand)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    PlayerRef p = e.player();

    int level = getActiveDeathLevel(p);
    if (level <= 0) {
      return;
    }

    vault.capture(e, p, getSaveCost(level), preference(p, XP_RESERVE).amount());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    vault.restorePending(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef p = e.player();
    AdaptApi.scheduler().runEntity(p, () -> vault.restorePending(p), 20);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    vault.forget(e.player().uuid());
  }

  @Override
  public void unregister() {
    vault.clear();
    super.unregister();
  }

  private void markFx(PlayerRef p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.TOTEM, (0.4D + progress) * 0.8D, 10, 1.0D);
          if (tick == 0) {
            f.particle(Particles.END_ROD, 4, 0, 0.6D, 0, 0.3D, 0.04D)
                .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 0.9F, Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.5F, 1.5F);
          }
        })
        .start();
  }

  private void savedFx(PlayerRef p) {
    timeline(p)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.TOTEM, (0.8D * (1.0D - progress)) + 0.2D, 1.7D, 4, progress * Math.PI * 3.0D);
          if (tick == 0) {
            f.dome(Particles.END_ROD, 1.4D, 12)
                .chord(Sounds.ITEM_TOTEM_USE, 0.5F, 1.2F, Sounds.BLOCK_BEACON_ACTIVATE, 0.4F, 1.4F);
          }
        })
        .start();
  }

  @ConfigDescription("Sneak-right-click an anvil to soul-link an enchanted item so it survives death for an XP cost.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Save Cost Base for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saveCostBase = 8;
    @ConfigDoc(value = "Controls Save Cost Factor for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saveCostFactor = 5;
    @ConfigDoc(value = "Controls Min Save Cost for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minSaveCost = 2;
    @ConfigDoc(value = "Controls Remark Cooldown Base for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double remarkCooldownBase = 60000;
    @ConfigDoc(value = "Controls Remark Cooldown Factor for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double remarkCooldownFactor = 45000;
    @ConfigDoc(value = "Controls Min Remark Cooldown for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long minRemarkCooldown = 8000;
    @ConfigDoc(value = "Controls Skill Xp On Save for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnSave = 40;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
