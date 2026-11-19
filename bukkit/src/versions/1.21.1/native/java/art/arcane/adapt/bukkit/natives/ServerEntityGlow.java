package art.arcane.adapt.bukkit.natives;

import art.arcane.volmlib.nativelib.entity.EntityGlow;
import art.arcane.volmlib.nativelib.entity.EntityGlowAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-viewer glow: the glowing bit of the entity's shared flags and a client-only colour team, sent only to the viewer.
 * Same packets as the 26.x native glow.
 */
final class ServerEntityGlow implements EntityGlowAccess {
  /** Entity data slot 0 is the shared flags byte on every version. */
  private static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
  private static final byte GLOWING_FLAG = 1 << 6;

  @Override
  public EntityGlow create(Plugin plugin) {
    Glow glow = new Glow();
    plugin.getServer().getPluginManager().registerEvents(glow, plugin);
    return glow;
  }

  private static final class Glow implements EntityGlow, Listener {
    private final Scoreboard scoreboard = new Scoreboard();
    private final Map<ChatColor, PlayerTeam> teams = new EnumMap<>(ChatColor.class);
    private final Map<Player, Viewer> viewers = new HashMap<>();
    private final int uid = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    private boolean enabled = true;

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
      viewers.remove(event.getPlayer());
    }

    @Override
    public void setGlowing(org.bukkit.entity.Entity entity, Player receiver, ChatColor color) {
      ensureEnabled();
      if (color != null && !color.isColor()) {
        throw new IllegalArgumentException("ChatColor must be a color format");
      }
      Entity handle = ServerHandles.entity(entity);
      if (handle == null) {
        return;
      }
      String teamId = entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
      byte otherFlags = handle.getEntityData().get(SHARED_FLAGS);
      Viewer viewer = viewers.computeIfAbsent(receiver, Viewer::new);
      Glowing glowing = viewer.glowing.get(entity.getEntityId());
      if (glowing == null) {
        glowing = new Glowing(teamId, color, otherFlags);
        viewer.glowing.put(entity.getEntityId(), glowing);
      } else {
        if (glowing.color != null && glowing.color != color) {
          removeColor(viewer, glowing);
        }
        glowing.teamId = teamId;
        glowing.color = color;
        glowing.otherFlags = otherFlags;
      }
      sendFlags(receiver, entity.getEntityId(), (byte) (glowing.otherFlags | GLOWING_FLAG));
      if (color != null) {
        addColor(viewer, glowing);
      }
    }

    @Override
    public void unsetGlowing(int entityId, Player receiver) {
      ensureEnabled();
      Viewer viewer = viewers.get(receiver);
      if (viewer == null) {
        return;
      }
      Glowing glowing = viewer.glowing.remove(entityId);
      if (glowing == null) {
        return;
      }
      sendFlags(receiver, entityId, glowing.otherFlags);
      if (glowing.color != null) {
        removeColor(viewer, glowing);
      }
    }

    @Override
    public void disable() {
      if (!enabled) {
        return;
      }
      HandlerList.unregisterAll(this);
      viewers.clear();
      enabled = false;
    }

    private void ensureEnabled() {
      if (!enabled) {
        throw new IllegalStateException("The Glowing Entities API is not enabled.");
      }
    }

    private void addColor(Viewer viewer, Glowing glowing) {
      PlayerTeam team = teams.computeIfAbsent(glowing.color, this::team);
      if (viewer.sentColors.add(glowing.color)) {
        send(viewer.player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
      }
      send(viewer.player, ClientboundSetPlayerTeamPacket.createPlayerPacket(team, glowing.teamId,
          ClientboundSetPlayerTeamPacket.Action.ADD));
    }

    private void removeColor(Viewer viewer, Glowing glowing) {
      PlayerTeam team = teams.get(glowing.color);
      if (team != null) {
        send(viewer.player, ClientboundSetPlayerTeamPacket.createPlayerPacket(team, glowing.teamId,
            ClientboundSetPlayerTeamPacket.Action.REMOVE));
      }
    }

    private PlayerTeam team(ChatColor color) {
      PlayerTeam team = new PlayerTeam(scoreboard, "glow-" + uid + color.getChar());
      team.setCollisionRule(Team.CollisionRule.NEVER);
      ChatFormatting formatting = ChatFormatting.getByCode(color.getChar());
      team.setColor(formatting == null ? ChatFormatting.WHITE : formatting);
      return team;
    }

    private static void sendFlags(Player receiver, int entityId, byte flags) {
      send(receiver, new ClientboundSetEntityDataPacket(entityId, List.of(SynchedEntityData.DataValue.create(SHARED_FLAGS, flags))));
    }

    private static void send(Player receiver, Packet<?> packet) {
      ServerPlayer player = ServerHandles.player(receiver);
      if (player != null) {
        player.connection.send(packet);
      }
    }
  }

  private static final class Viewer {
    private final Player player;
    private final Map<Integer, Glowing> glowing = new HashMap<>();
    private final EnumSet<ChatColor> sentColors = EnumSet.noneOf(ChatColor.class);

    private Viewer(Player player) {
      this.player = player;
    }
  }

  private static final class Glowing {
    private String teamId;
    private ChatColor color;
    private byte otherFlags;

    private Glowing(String teamId, ChatColor color, byte otherFlags) {
      this.teamId = teamId;
      this.color = color;
      this.otherFlags = otherFlags;
    }
  }
}
