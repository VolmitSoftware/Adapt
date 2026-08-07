package fr.skytasul.glowingentities;

import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GlowingEntities implements Listener {
  private static final byte GLOWING_FLAG = 1 << 6;

  private final @NotNull Plugin plugin;
  private final EntityDataAccessor<Byte> sharedFlagsAccessor;
  private final Scoreboard scoreboard;
  private final EnumMap<ChatColor, TeamData> teams;

  private Map<Player, PlayerData> glowing;
  private boolean enabled;
  private int uid;

  public GlowingEntities(@NotNull Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
    this.sharedFlagsAccessor = loadSharedFlagsAccessor();
    this.scoreboard = new Scoreboard();
    this.teams = new EnumMap<>(ChatColor.class);
    enable();
  }

  public void enable() {
    if (enabled) {
      throw new IllegalStateException("The Glowing Entities API has already been enabled.");
    }

    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    glowing = new HashMap<>();
    uid = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    enabled = true;
  }

  public void disable() {
    if (!enabled) {
      return;
    }

    HandlerList.unregisterAll(this);
    glowing.clear();
    glowing = null;
    uid = 0;
    enabled = false;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    if (glowing != null) {
      glowing.remove(event.getPlayer());
    }
  }

  public void setGlowing(org.bukkit.entity.Entity entity, Player receiver) throws ReflectiveOperationException {
    setGlowing(entity, receiver, null);
  }

  public void setGlowing(org.bukkit.entity.Entity entity, Player receiver, ChatColor color)
      throws ReflectiveOperationException {
    String teamID = entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
    byte flags = getEntityFlags(entity);
    setGlowing(entity.getEntityId(), teamID, receiver, color, flags);
  }

  public void setGlowing(int entityID, String teamID, Player receiver) throws ReflectiveOperationException {
    setGlowing(entityID, teamID, receiver, null, (byte) 0);
  }

  public void setGlowing(int entityID, String teamID, Player receiver, ChatColor color)
      throws ReflectiveOperationException {
    setGlowing(entityID, teamID, receiver, color, (byte) 0);
  }

  public void setGlowing(int entityID, String teamID, Player receiver, ChatColor color, byte otherFlags)
      throws ReflectiveOperationException {
    ensureEnabled();
    if (color != null && !color.isColor()) {
      throw new IllegalArgumentException("ChatColor must be a color format");
    }

    PlayerData playerData = glowing.computeIfAbsent(receiver, PlayerData::new);
    GlowingData glowingData = playerData.glowingDatas.get(entityID);
    if (glowingData == null) {
      glowingData = new GlowingData(entityID, teamID, color, otherFlags);
      playerData.glowingDatas.put(entityID, glowingData);
    } else {
      if (glowingData.color != null && glowingData.color != color) {
        removeGlowingColor(playerData, glowingData);
      }
      glowingData.teamID = teamID;
      glowingData.color = color;
      glowingData.otherFlags = otherFlags;
    }

    sendMetadata(receiver, entityID, computeFlags(glowingData));
    if (color != null) {
      setGlowingColor(playerData, glowingData);
    }
  }

  public void unsetGlowing(org.bukkit.entity.Entity entity, Player receiver) throws ReflectiveOperationException {
    unsetGlowing(entity.getEntityId(), receiver);
  }

  public void unsetGlowing(int entityID, Player receiver) throws ReflectiveOperationException {
    ensureEnabled();

    PlayerData playerData = glowing.get(receiver);
    if (playerData == null) {
      return;
    }

    GlowingData glowingData = playerData.glowingDatas.remove(entityID);
    if (glowingData == null) {
      return;
    }

    sendMetadata(receiver, entityID, glowingData.otherFlags);
    if (glowingData.color != null) {
      removeGlowingColor(playerData, glowingData);
    }
  }

  private void ensureEnabled() {
    if (!enabled) {
      throw new IllegalStateException("The Glowing Entities API is not enabled.");
    }
  }

  private byte getEntityFlags(org.bukkit.entity.Entity entity) {
    Entity nmsEntity = ((CraftEntity) entity).getHandle();
    return nmsEntity.getEntityData().get(sharedFlagsAccessor);
  }

  private byte computeFlags(GlowingData glowingData) {
    return (byte) (glowingData.otherFlags | GLOWING_FLAG);
  }

  private void sendMetadata(Player player, int entityID, byte flags) {
    SynchedEntityData.DataValue<Byte> value = SynchedEntityData.DataValue.create(sharedFlagsAccessor, flags);
    sendPacket(player, new ClientboundSetEntityDataPacket(entityID, List.of(value)));
  }

  private void setGlowingColor(PlayerData playerData, GlowingData glowingData) {
    boolean sendCreation = false;
    if (playerData.sentColors == null) {
      playerData.sentColors = EnumSet.of(glowingData.color);
      sendCreation = true;
    } else if (playerData.sentColors.add(glowingData.color)) {
      sendCreation = true;
    }

    TeamData teamData = getTeamData(glowingData.color);
    if (sendCreation) {
      sendPacket(playerData.player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(teamData.team, true));
    }
    sendPacket(playerData.player,
        ClientboundSetPlayerTeamPacket.createPlayerPacket(
            teamData.team,
            glowingData.teamID,
            ClientboundSetPlayerTeamPacket.Action.ADD
        ));
  }

  private void removeGlowingColor(PlayerData playerData, GlowingData glowingData) {
    TeamData teamData = teams.get(glowingData.color);
    if (teamData == null) {
      return;
    }

    sendPacket(playerData.player,
        ClientboundSetPlayerTeamPacket.createPlayerPacket(
            teamData.team,
            glowingData.teamID,
            ClientboundSetPlayerTeamPacket.Action.REMOVE
        ));
  }

  private TeamData getTeamData(ChatColor color) {
    TeamData teamData = teams.get(color);
    if (teamData != null) {
      return teamData;
    }

    TeamData created = new TeamData(uid, color, scoreboard);
    teams.put(color, created);
    return created;
  }

  private void sendPacket(Player player, Packet<?> packet) {
    ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
    serverPlayer.connection.send(packet);
  }

  private static EntityDataAccessor<Byte> loadSharedFlagsAccessor() {
    try {
      Field field = Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      EntityDataAccessor<Byte> accessor = (EntityDataAccessor<Byte>) field.get(null);
      return accessor;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to locate entity shared flags metadata accessor.", e);
    }
  }

  private static final class PlayerData {
    private final Player player;
    private final Map<Integer, GlowingData> glowingDatas;
    private EnumSet<ChatColor> sentColors;

    private PlayerData(Player player) {
      this.player = player;
      this.glowingDatas = new HashMap<>();
    }
  }

  private static final class GlowingData {
    private final int entityID;
    private String teamID;
    private ChatColor color;
    private byte otherFlags;

    private GlowingData(int entityID, String teamID, ChatColor color, byte otherFlags) {
      this.entityID = entityID;
      this.teamID = teamID;
      this.color = color;
      this.otherFlags = otherFlags;
    }
  }

  private static final class TeamData {
    private final PlayerTeam team;

    private TeamData(int uid, ChatColor color, Scoreboard scoreboard) {
      this.team = new PlayerTeam(scoreboard, "glow-" + uid + color.getChar());
      team.setCollisionRule(Team.CollisionRule.NEVER);
      TeamColorBridge.apply(team, color);
    }
  }

  /**
   * PlayerTeam.setColor takes ChatFormatting on 26.1.x and Optional&lt;TeamColor&gt; on
   * 26.2+ (net.minecraft.world.scores.TeamColor does not exist on 26.1.x). Both call
   * shapes are resolved through MethodHandles probed once at first use, so the same
   * bytecode links against either server version.
   */
  private static final class TeamColorBridge {
    private static volatile MethodHandle setColorFormatting;
    private static volatile MethodHandle setColorOptional;
    private static volatile MethodHandle teamColorByName;
    private static volatile boolean resolved;

    static void apply(PlayerTeam team, ChatColor color) {
      try {
        if (!resolved) {
          resolve();
        }

        MethodHandle direct = setColorFormatting;
        if (direct != null) {
          direct.invoke(team, resolveFormatting(color));
          return;
        }

        Object teamColor = teamColorByName.invoke(color.name().toLowerCase(Locale.ROOT));
        if (teamColor == null) {
          teamColor = teamColorByName.invoke("white");
        }
        setColorOptional.invoke(team, Optional.of(teamColor));
      } catch (Throwable error) {
        throw new IllegalStateException("Unable to apply glowing team color " + color + ".", error);
      }
    }

    private static synchronized void resolve() throws ReflectiveOperationException {
      if (resolved) {
        return;
      }

      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      try {
        setColorFormatting = lookup.findVirtual(PlayerTeam.class, "setColor",
            MethodType.methodType(void.class, ChatFormatting.class));
      } catch (NoSuchMethodException absent) {
        Class<?> teamColorClass = Class.forName("net.minecraft.world.scores.TeamColor");
        teamColorByName = lookup.findStatic(teamColorClass, "byName",
            MethodType.methodType(teamColorClass, String.class));
        setColorOptional = lookup.findVirtual(PlayerTeam.class, "setColor",
            MethodType.methodType(void.class, Optional.class));
      }
      resolved = true;
    }

    private static ChatFormatting resolveFormatting(ChatColor color) {
      ChatFormatting formatting = ChatFormatting.getByCode(color.getChar());
      return formatting == null ? ChatFormatting.WHITE : formatting;
    }
  }
}
