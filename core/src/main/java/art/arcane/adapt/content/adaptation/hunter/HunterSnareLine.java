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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HunterMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class HunterSnareLine extends SimpleAdaptation<HunterSnareLine.Config> {
  private static final DataKey<Byte> SNARE_ITEM_KEY = DataKey.adapt("hunter-snare-item", DataCodecs.BYTE);
  private static final Id TRIPWIRE_HOOK = Id.minecraft("tripwire_hook");
  private static final Rgb SNARE_GREEN = new Rgb(120, 180, 90);
  private final List<Snare> snares = new CopyOnWriteArrayList<>();

  public HunterSnareLine() {
    super("hunter-snare-line");
    registerConfiguration(Config.class);
    setIcon(TRIPWIRE_HOOK);
    setInterval(250);
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("hunter-snare"),
        List.of(
            "S S",
            "SIS",
            "S S"),
        Map.of('S', Ingredient.of(Id.minecraft("string")), 'I', Ingredient.of(Id.minecraft("iron_ingot"))),
        buildSnareItem(2)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tripwire_hook"))
        .key("challenge_hunter_snare_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lead"))
            .key("challenge_hunter_snare_2k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_snare_200", "hunter.snare-line.mobs-snared", 200, 400);
    registerMilestone("challenge_hunter_snare_2k", "hunter.snare-line.mobs-snared", 2000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (getActiveLevel(player.getPlayer()) <= 0) {
      snares.removeIf(snare -> snare.owner.equals(player.getPlayer().uuid()));
      AbilityGlyphs.clear(player.getPlayer().uuid(), this, HudGlyph.CHARGES);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRootDurationTicks(level) / 20.0D, 1), 1);
    statLore(v, C.YELLOW, "+ ", getCharges(level), 2);
    v.addLore(C.GRAY + "- " + AdaptLanguage.text(HunterMessages.SNARE_LINE_LORE3));
  }

  static int scaleInt(double levelPercent, int base, double factor) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  static double rootSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  int getRootDurationTicks(int level) {
    return scaleInt(getLevelPercent(level), getConfig().rootDurationTicksBase, getConfig().rootDurationTicksFactor);
  }

  int getCharges(int level) {
    return scaleInt(getLevelPercent(level), getConfig().chargesBase, getConfig().chargesFactor);
  }

  static ItemRef buildSnareItem(int amount) {
    ItemRef item = AdaptCore.platform().host().items().create(TRIPWIRE_HOOK, amount);
    item.setDisplayName(C.GREEN + AdaptLanguage.text(HunterMessages.SNARE_LINE_ITEM));
    item.data().set(SNARE_ITEM_KEY, (byte) 1);
    return item;
  }

  static boolean isSnareItem(ItemRef item) {
    if (item == null || item.isEmpty() || !item.type().equals(TRIPWIRE_HOOK)) {
      return false;
    }
    return item.data().has(SNARE_ITEM_KEY);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    snares.removeIf(snare -> snare.owner.equals(e.player().uuid()));
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.clickedBlock() == null) {
      return;
    }

    Hand handSlot = e.hand();
    if (handSlot == null) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef hand = getItemInHand(p, handSlot);
    if (!isSnareItem(hand)) {
      return;
    }

    boolean blockUseDenied = e.useBlock() == UseResult.DENY;
    e.setCancelled(true);
    if (!hasActiveAdaptation(p) || (preferenceEnabled(p, HunterPreferences.SNEAK) && !p.isSneaking())) {
      return;
    }

    if (blockUseDenied) {
      return;
    }

    BlockRef clicked = e.clickedBlock();
    Loc snareLocation = clicked.location().add(0.5D, 1.0D, 0.5D);
    if (!canInteract(p, snareLocation)) {
      return;
    }

    if (countOwned(p.uuid()) >= Math.max(1, getConfig().maxSnaresPerPlayer)) {
      fx(p.eyeLocation(), FxPriority.GAMEPLAY).sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }
    if (snares.size() >= Math.max(1, getConfig().maxActiveSnares)) {
      return;
    }

    int level = getActiveLevel(p);
    consumeSnareItem(p, handSlot, hand);
    long now = M.ms();
    Snare snare = new Snare(
        p.uuid(),
        snareLocation,
        getCharges(level),
        getRootDurationTicks(level),
        now + (getConfig().snareLifetimeTicks * 50L));
    snares.add(snare);
    publishSnares(p.uuid());

    fx(snareLocation, FxPriority.GAMEPLAY)
        .dustRing(SNARE_GREEN, getConfig().triggerRadius, 14, 1.0F)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.2D, 0, 0.3D, 0.05D)
        .chord(Sounds.BLOCK_TRIPWIRE_ATTACH, 0.7F, 1.1F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.3F);
  }

  private void consumeSnareItem(PlayerRef p, Hand handSlot, ItemRef hand) {
    int remaining = hand.amount() - 1;
    ItemRef replacement = remaining <= 0 ? AdaptCore.platform().host().items().empty() : hand.copyWithAmount(remaining);
    if (handSlot == Hand.OFF_HAND) {
      p.inventory().setOffHand(replacement);
    } else {
      p.inventory().setMainHand(replacement);
    }
  }

  private ItemRef getItemInHand(PlayerRef p, Hand handSlot) {
    return handSlot == Hand.OFF_HAND
        ? p.inventory().offHand()
        : p.inventory().mainHand();
  }

  private int countOwned(UUID owner) {
    int count = 0;
    for (Snare snare : snares) {
      if (snare.owner.equals(owner)) {
        count++;
      }
    }
    return count;
  }

  private void publishSnares(UUID owner) {
    if (!AbilityGlyphs.capable(owner)) {
      return;
    }
    int count = countOwned(owner);
    if (count <= 0) {
      AbilityGlyphs.clear(owner, this, HudGlyph.CHARGES);
      return;
    }
    AbilityGlyphs.charges(owner, this, count, Math.max(1, getConfig().maxSnaresPerPlayer));
  }

  @Override
  public boolean hasTickDemand() {
    return !snares.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    for (Snare snare : snares) {
      if (snare.expiresAt <= now || snare.charges.get() <= 0) {
        if (snares.remove(snare)) {
          emitSnareBreak(snare);
          publishSnares(snare.owner);
        }
        continue;
      }
      Tasks.runAt(snare.location, () -> scanSnare(snare, now));
    }
  }

  private void scanSnare(Snare snare, long scheduledAt) {
    WorldRef world = snare.location.world();
    if (world == null || !snares.contains(snare) || snare.charges.get() <= 0) {
      return;
    }

    double radius = getConfig().triggerRadius;
    fx(snare.location, FxPriority.AMBIENT).dustRing(SNARE_GREEN, radius, 10, 0.8F);

    int budget = Math.max(1, getConfig().maxTargetsPerScan);
    int scheduled = 0;
    for (EntityRef entity : world.nearbyEntities(snare.location.position(), radius, radius, radius)) {
      if (!(entity instanceof LivingRef monster) || !entity.has(EntityTrait.MONSTER)) {
        continue;
      }
      if (scheduled >= budget || snare.charges.get() <= 0) {
        break;
      }
      Long rearm = snare.rearmUntil.get(monster.uuid());
      if (rearm != null && rearm > scheduledAt) {
        continue;
      }
      scheduled++;
      Tasks.runEntity(monster, () -> rootMonster(snare, monster));
    }
  }

  private void rootMonster(Snare snare, LivingRef monster) {
    if (!monster.isValid() || monster.isDead() || !snares.contains(snare)) {
      return;
    }

    if (isProtectedFriendlyOwned(snare.owner, monster)) {
      return;
    }
    if (!snare.tryConsumeCharge()) {
      return;
    }

    long now = M.ms();
    snare.rearmUntil.put(monster.uuid(), now + (snare.rootDurationTicks * 50L) + getConfig().rearmBufferMillis);
    if (snare.rootDurationTicks > 0) {
      AdaptAttributeService.get().applyTimed(
          monster,
          getName(),
          "root",
          Attributes.MOVEMENT_SPEED,
          rootSpeedScalar(getConfig().rootAmplifier),
          AttributeOperation.ADD_MULTIPLIED_TOTAL,
          snare.rootDurationTicks);
    }
    monster.setVelocity(Vec3.ZERO);

    fx(monster.location().add(0, monster.height() * 0.4D, 0), FxPriority.COMBAT)
        .particle(Particles.CRIT_MAGIC, 6, 0, 0.3D, 0, 0.3D, 0.0D)
        .dustBurst(SNARE_GREEN, 6, 0.3D, 1.0F)
        .chord(Sounds.BLOCK_TRIPWIRE_CLICK_ON, 0.8F, 0.8F, Sounds.ITEM_LEAD_TIED, 0.6F, 0.9F);

    PlayerRef owner = AdaptCore.platform().host().player(snare.owner);
    if (owner != null) {
      Tasks.runEntity(owner, () -> {
        if (owner.isOnline()) {
          addStat(owner, "hunter.snare-line.mobs-snared", 1);
          xp(owner, getConfig().xpPerSnare);
        }
      });
    }
  }

  private void emitSnareBreak(Snare snare) {
    Tasks.runAt(snare.location, () -> fx(snare.location, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 6, 0, 0.2D, 0, 0.25D, 0.02D)
        .sound(Sounds.BLOCK_TRIPWIRE_DETACH, 0.6F, 0.9F));
  }

  @ConfigDescription("Craft a string-and-iron snare; hostile mobs that cross it are rooted briefly.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base snare root duration in ticks for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootDurationTicksBase = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional snare root duration in ticks gained across levels for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rootDurationTicksFactor = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base trigger charges per crafted snare for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int chargesBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional trigger charges gained across levels for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargesFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Snare trigger radius in blocks for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double triggerRadius = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness-equivalent amplifier mapped to a movement speed reduction of 15 percent per level, capped at a full stop, while a mob is rooted for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootAmplifier = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds a snared mob is immune to re-triggering the same snare for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long rearmBufferMillis = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a placed snare persists before decaying for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long snareLifetimeTicks = 2400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum concurrent snares one player may keep placed for the Hunter Snare Line adaptation.", impact = "Lower values reduce tracked snare state.")
    int maxSnaresPerPlayer = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum concurrent snares processed by this server for the Hunter Snare Line adaptation.", impact = "Lower values reduce scan scheduling work.")
    int maxActiveSnares = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs scheduled for rooting by one snare per scan for the Hunter Snare Line adaptation.", impact = "Lower values reduce burst entity work in crowds.")
    int maxTargetsPerScan = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted per mob snared for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerSnare = 6;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }

  private static final class Snare {
    private final UUID owner;
    private final Loc location;
    private final AtomicInteger charges;
    private final int rootDurationTicks;
    private final long expiresAt;
    private final Map<UUID, Long> rearmUntil;

    private Snare(UUID owner, Loc location, int charges, int rootDurationTicks, long expiresAt) {
      this.owner = owner;
      this.location = location;
      this.charges = new AtomicInteger(Math.max(1, charges));
      this.rootDurationTicks = rootDurationTicks;
      this.expiresAt = expiresAt;
      this.rearmUntil = new ConcurrentHashMap<>();
    }

    private boolean tryConsumeCharge() {
      int current = charges.get();
      while (current > 0) {
        if (charges.compareAndSet(current, current - 1)) {
          return true;
        }
        current = charges.get();
      }
      return false;
    }
  }
}
