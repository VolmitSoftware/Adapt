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

package art.arcane.adapt.content.adaptation.architect;

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
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ArchitectFoundation extends SimpleAdaptation<ArchitectFoundation.Config> {
  public static final Id ID = Id.adapt("architect/foundation");
  public static final PlayerPreference<Control> CONTROL = PlayerPreference.of("control", Control.class, Control.HOLD, List.of(
          new PlayerPreference.Choice<>(Control.HOLD, Text.key("adapt.architectfoundation.preferences.preference_control_hold"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.LATCH, Text.key("adapt.architectfoundation.preferences.preference_control_latch"), Id.minecraft("lever"), 1))).labelled(Text.key("adapt.architectfoundation.preferences.preference_control"));
  public static final PlayerPreference<CommonPreferences.Toggle> EMPTY_HAND = CommonPreferences.toggle("empty-hand", Text.key("adapt.architectfoundation.preferences.preference_empty_hand"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Scale> DURATION = CommonPreferences.scale("duration", Text.key("adapt.architectfoundation.preferences.preference_duration"));

  private static final Id TINTED_GLASS = Id.minecraft("tinted_glass");
  private final Map<UUID, Integer> blockPower;
  private final Map<UUID, Long> cooldowns;
  private final Map<UUID, Boolean> active;
  private final ArchitectTemporaryBlocks temporaryBlocks;

  public ArchitectFoundation() {
    super(ID);
    registerConfiguration(ArchitectFoundation.Config.class);
    setIcon(TINTED_GLASS);
    setInterval(988);
    blockPower = playerState();
    cooldowns = playerState();
    active = playerState();
    temporaryBlocks = new ArchitectTemporaryBlocks(this, "architect-foundation", (BlockRef block) ->
        fx(block.location().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
            .burst(Particles.ASH, 4, 0.2D)
            .sound(Sounds.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.0f));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("scaffolding"))
        .key("challenge_architect_foundation_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("scaffolding"))
            .key("challenge_architect_foundation_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_foundation_1k", "architect.foundation.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_foundation_10k", "architect.foundation.blocks-placed", 10000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, EMPTY_HAND, DURATION, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    deactivateFoundation(player.player(), player.player().uuid());
  }

  @Override
  protected void onRuntimeActivated() {
    temporaryBlocks.recoverLoadedChunks();
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.architect.foundation.lore1").resolve()
        + (getBlockPower(getLevelPercent(level))) + TextFormat.GRAY + " "
        + Text.key("adapt.architect.foundation.lore2").resolve());
  }




  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerMoveEvent e) {
    Loc to = e.to();
    if (to == null || active.isEmpty()) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() && preference(p, CONTROL) == Control.HOLD) {
      return;
    }

    UUID id = p.uuid();
    if (!active.containsKey(id) || (e.from().sameWorld(to) && e.from().blockPos().equals(to.blockPos()))) {
      return;
    }

    withPlayerThread(p, e, () -> {
      if ((!p.isSneaking() && preference(p, CONTROL) == Control.HOLD) || !active.containsKey(id) || !isPlayerEnabled(p)) {
        return;
      }
      placeFoundation(p, to);
    });
  }

  private void placeFoundation(PlayerRef p, Loc location) {
    if (location.world() == null
        || (AdaptApi.scheduler().isRegionized()
        && (!AdaptApi.scheduler().owns(p) || !AdaptApi.scheduler().owns(location)))) {
      return;
    }

    UUID id = p.uuid();
    if (getActiveBlockPlaceLevel(p, location) <= 0) {
      return;
    }
    int power = blockPower.getOrDefault(id, 0);

    if (power <= 0) {
      return;
    }

    WorldRef world = location.world();
    Set<BlockRef> locations = new LinkedHashSet<>(4);
    int y = location.blockY() - 1;
    int minX = (int) Math.floor(location.x() - 0.3D);
    int maxX = (int) Math.floor(location.x() + 0.3D);
    int minZ = (int) Math.floor(location.z() - 0.3D);
    int maxZ = (int) Math.floor(location.z() + 0.3D);
    addOwnedFoundationTarget(locations, world, minX, y, minZ);
    addOwnedFoundationTarget(locations, world, minX, y, maxZ);
    addOwnedFoundationTarget(locations, world, maxX, y, minZ);
    addOwnedFoundationTarget(locations, world, maxX, y, maxZ);

    int startPower = power;
    for (BlockRef b : locations) {
      if (addFoundation(p, b)) {
        power--;
        addStat(p, "architect.foundation.blocks-placed", 1);
        Mutations.landed(this, p);
      }

      if (power <= 0) {
        break;
      }
    }

    if (power != startPower) {
      float pitch = (float) (0.8D + (0.8D * (power / (double) Math.max(1, getConfig().maxBlocks))));
      fx(location, FxPriority.TRAIL)
          .dustRing(new Rgb(120, 255, 200), 0.6D, 12, 0.8F)
          .sound(Sounds.BLOCK_DEEPSLATE_PLACE, 0.7f, pitch);
    }

    blockPower.put(id, power);
    if (power != startPower) {
      HudStatus.charges(id, this, power, getBlockPower(getLevelPercent(p)));
    }
  }

  private void addOwnedFoundationTarget(Set<BlockRef> targets, WorldRef world, int x, int y, int z) {
    Loc target = new Loc(world, x, y, z);
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(target)) {
      return;
    }
    targets.add(world.blockAt(x, y, z));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPistonEvent e) {
    temporaryBlocks.on(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    temporaryBlocks.spare(e.blocks());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    temporaryBlocks.on(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    temporaryBlocks.spare(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (active.containsKey(id) && (preference(p, CONTROL) == Control.LATCH ? e.isSneaking() : !e.isSneaking())) {
      withPlayerThread(p, e, () -> deactivateFoundation(p, id));
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.gameMode() == GameMode.CREATIVE
          || p.gameMode() == GameMode.SPECTATOR) {
        return;
      }

      boolean ready = !hasCooldown(id);
      boolean activeNow = active.containsKey(id);

      if (e.isSneaking() && ready && !activeNow
          && (!preferenceEnabled(p, EMPTY_HAND) || p.inventory().mainHand().type().isAir())) {
        active.put(id, Boolean.TRUE);
        int power = getBlockPower(getLevelPercent(p));
        blockPower.put(id, power);
        HudStatus.charges(id, this, power, power);
        FxPresets.chargeRing(this, p.location(), 6);
        placeFoundation(p, p.location());
      }
    });
  }

  private boolean addFoundation(PlayerRef player, BlockRef block) {
    int durationTicks = (int) Math.max(1L,
        Math.min(Integer.MAX_VALUE, (long) Math.ceil(getConfig().duration * preference(player, DURATION).multiplier() / 50D)));
    return temporaryBlocks.raise(player, block, durationTicks);
  }

  public int getBlockPower(double factor) {
    return (int) Math.floor((getConfig().minBlocks + factor * (getConfig().maxBlocks - getConfig().minBlocks)));
  }

  private void deactivateFoundation(PlayerRef player, UUID playerId) {
    if (active.remove(playerId) == null) {
      return;
    }
    blockPower.remove(playerId);
    HudStatus.clear(playerId, this, HudGlyph.CHARGES);
    long cooldownMillis = Math.max(0L, getConfig().cooldown);
    long now = System.currentTimeMillis();
    long deadline = now + cooldownMillis;
    if (cooldownMillis > 0L) {
      cooldowns.put(playerId, deadline);
      HudStatus.cooldown(playerId, this, now, cooldownMillis);
      scheduleCooldownReady(player, playerId, deadline);
    } else {
      cooldowns.remove(playerId);
    }
    fx(player.location(), FxPriority.TRANSITION)
        .dustRing(new Rgb(128, 128, 128), 1.0D, 16, 1.0F)
        .chord(Sounds.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f, Sounds.BLOCK_SCULK_CATALYST_BREAK, 1.0f, 0.81f);
  }

  private void scheduleCooldownReady(PlayerRef player, UUID playerId, long deadline) {
    long remaining = Math.max(0L, deadline - System.currentTimeMillis());
    int delayTicks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (remaining + 49L) / 50L));
    AdaptApi.scheduler().runEntity(player, () -> completeCooldown(player, playerId, deadline), delayTicks);
  }

  private void completeCooldown(PlayerRef player, UUID playerId, long deadline) {
    if (!isRuntimeRegistered()) {
      return;
    }
    Long current = cooldowns.get(playerId);
    if (current == null || current != deadline) {
      return;
    }
    if (System.currentTimeMillis() < deadline) {
      scheduleCooldownReady(player, playerId, deadline);
      return;
    }

    cooldowns.remove(playerId, deadline);
    if (player.isOnline() && hasActiveAdaptation(player)) {
      fx(player.location(), FxPriority.TRANSITION)
          .dustRing(new Rgb(120, 255, 200), 1.2D, 16, 1.0F)
          .chord(Sounds.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f, Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.81f);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent event) {
    temporaryBlocks.on(event);
  }

  @Override
  public void unregister() {
    super.unregister();
    temporaryBlocks.clear();
    active.clear();
    blockPower.clear();
    cooldowns.clear();
  }

  private boolean hasCooldown(UUID id) {
    Long cooldown = cooldowns.get(id);
    if (cooldown != null && System.currentTimeMillis() >= cooldown) {
      cooldowns.remove(id);
      cooldown = null;
    }

    return cooldown != null;
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    blockPower.remove(id);
    cooldowns.remove(id);
    active.remove(id);
  }

  @ConfigDescription("Sneak to place a temporary foundation beneath you.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Duration for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long duration = 3000;
    @ConfigDoc(value = "Controls Min Blocks for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int minBlocks = 9;
    @ConfigDoc(value = "Controls Max Blocks for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int maxBlocks = 35;
    @ConfigDoc(value = "Controls Cooldown for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 5000;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  public enum Control { HOLD, LATCH }
}
