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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.discovery.upgraded.UnityPrime;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


public class DiscoveryArmor extends SimpleAdaptation<DiscoveryArmor.Config> {
  public static final Id ID = Id.adapt("discovery/world_armor");
  static final UUID LEGACY_MODIFIER = UUID.nameUUIDFromBytes("adapt-discovery-armor".getBytes());
  static final Id LEGACY_MODIFIER_KEY = Id.adapt("discovery-armor");
  private static final Id LEGACY_UUID_MODIFIER_KEY = Id.minecraft(LEGACY_MODIFIER.toString());
  private static final String LEGACY_MODIFIER_NAME = LEGACY_MODIFIER_KEY.namespace() + "-" + LEGACY_MODIFIER_KEY.path();
  private static final long UPDATE_COOLDOWN = TimeUnit.SECONDS.toMillis(3);

  private final Cooldowns updateThrottle = cooldowns();
  private final WorldArmor armor = new WorldArmor(this, playerState());
  private final Map<UUID, Long> bonusStatAt = playerState();
  private final Map<UUID, Boolean> legacyPurged = playerState();
  private Iterator<UUID> cleanupCursor = Map.<UUID, Double>of().keySet().iterator();
  private int playerCursor;

  public DiscoveryArmor() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.discovery.armor");
    setIcon(Id.minecraft("turtle_helmet"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_chestplate"))
        .key("challenge_discovery_armor_1hr")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_discovery_armor_1hr", "discovery.armor.ticks-with-bonus", 72000, 400);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (!hasActiveAdaptation(player.player())) {
      removeArmorModifier(player.player());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.discovery.armor.passive").resolve());
    v.addLore(TextFormat.YELLOW + "~ " + Text.key("adapt.discovery.armor.strength").withMarkup("strength", TextFormat.BLUE + String.valueOf(level * 0.25)).resolve());
  }

  @Override
  public void unregister() {
    armor.clear();
    bonusStatAt.clear();
    legacyPurged.clear();
    super.unregister();
  }

  static double elapsedActiveTicks(Long previous, long now) {
    if (previous == null || now <= previous) {
      return 0D;
    }
    return (now - previous) / 50D;
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand() || !armor.isEmpty();
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    List<PlayerProfile> candidates = learnedCandidates(now);
    int size = candidates.size();
    if (size == 0) {
      playerCursor = 0;
    } else {
      int limit = Math.max(1, Math.min(size, getConfig().maxPlayersPerPass));
      int start = Math.floorMod(playerCursor, size);
      for (int i = 0; i < limit; i++) {
        queueArmorUpdate(candidates.get((start + i) % size));
      }
      playerCursor = (start + limit) % size;
    }

    cleanupInactivePlayers();
  }

  @Handle
  public void on(PlayerJoinEvent event) {
    PlayerRef player = event.player();
    withPlayerThread(player, () -> purgeLegacyModifier(player));
  }

  private void queueArmorUpdate(PlayerProfile adaptPlayer) {
    PlayerRef player = adaptPlayer.player();
    if (player == null || !player.isOnline()) {
      return;
    }

    UUID playerId = player.uuid();
    if (!updateThrottle.isReady(playerId, UPDATE_COOLDOWN)) {
      return;
    }
    updateThrottle.mark(playerId);
    withPlayerThread(player, () -> updateArmor(adaptPlayer, player));
  }

  private void updateArmor(PlayerProfile adaptPlayer, PlayerRef player) {
    purgeLegacyModifier(player);
    if (!hasActiveAdaptation(player) || UnityPrime.covers(this, player)) {
      removeArmorModifier(player);
      return;
    }

    UUID playerId = player.uuid();
    WorldArmor.Change change = armor.update(player, getLevel(player));
    double oldArmor = change.before();
    double lerpedArmor = change.after();
    if (lerpedArmor > 0D) {
      double activeTicks = activeTicksSinceLastUpdate(playerId);
      if (activeTicks > 0D) {
        adaptPlayer.addStat("discovery.armor.ticks-with-bonus", activeTicks);
      }
    } else {
      bonusStatAt.remove(playerId);
    }

    if (Math.round(lerpedArmor) != Math.round(oldArmor) && Math.round(lerpedArmor) > 0) {
      fx(player.location(), FxPriority.TRANSITION)
          .dustRing(new Rgb(150, 170, 190), 0.8D, 16, 1.1F)
          .chord(Sounds.BLOCK_STONE_PLACE, 0.5F, 0.7F, Sounds.BLOCK_AMETHYST_BLOCK_STEP, 0.3F, 1.2F);
    } else if (lerpedArmor > 6D && ThreadLocalRandom.current().nextInt(20) == 0) {
      fx(player.location(), FxPriority.AMBIENT)
          .particle(Particles.CRIT, 2, 0, 0, 0, 0.05D, 0.01D);
    }
  }

  private void cleanupInactivePlayers() {
    if (!cleanupCursor.hasNext()) {
      cleanupCursor = armor.players().iterator();
    }

    int limit = Math.max(1, getConfig().maxPlayersPerPass);
    int examined = 0;
    while (examined < limit && cleanupCursor.hasNext()) {
      UUID playerId = cleanupCursor.next();
      examined++;
      PlayerRef player = AdaptApi.platform().host().player(playerId);
      if (player == null || !player.isOnline()) {
        armor.forget(playerId);
        continue;
      }

      PlayerProfile adaptPlayer = getPlayer(player);
      if (getLevel(adaptPlayer) > 0 && !UnityPrime.covers(this, player)) {
        continue;
      }
      withPlayerThread(player, () -> removeInactiveModifier(player));
    }
  }

  private void removeInactiveModifier(PlayerRef player) {
    if (hasActiveAdaptation(player) && !UnityPrime.covers(this, player)) {
      return;
    }

    removeArmorModifier(player);
  }

  private void removeArmorModifier(PlayerRef player) {
    armor.remove(player);
    bonusStatAt.remove(player.uuid());
  }

  private void purgeLegacyModifier(PlayerRef player) {
    if (legacyPurged.putIfAbsent(player.uuid(), Boolean.TRUE) != null) {
      return;
    }

    if (!player.hasAttribute(Attributes.ARMOR)) {
      return;
    }
    for (AttributeMod modifier : List.copyOf(player.modifiers(Attributes.ARMOR))) {
      if (isLegacyModifier(modifier.key())) {
        player.removeModifier(Attributes.ARMOR, modifier.key());
      }
    }
  }

  static boolean isLegacyModifier(Id key) {
    return LEGACY_MODIFIER_KEY.equals(key) || LEGACY_UUID_MODIFIER_KEY.equals(key) || LEGACY_MODIFIER_NAME.equals(key.path());
  }

  private double activeTicksSinceLastUpdate(UUID playerId) {
    long now = System.currentTimeMillis();
    Long previous = bonusStatAt.put(playerId, now);
    return elapsedActiveTicks(previous, now);
  }

  @ConfigDescription("Gain passive armor based on nearby block hardness.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Maximum players examined per scheduler pass.", impact = "Higher values refresh large servers faster but increase per-tick block reads.")
    public int maxPlayersPerPass = 16;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
