package art.arcane.adapt.gameplay;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class XpIntegrityPlugin extends JavaPlugin {
    private GameplayFixture fixture;

    @Override
    public void onEnable() {
        try {
            fixture = GameplayFixture.start(new BukkitFixturePlatform());
        } catch (IllegalStateException failure) {
            getLogger().log(Level.SEVERE, "XP fixture refused this server", failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (fixture != null) {
            fixture.stop();
            fixture = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin) || fixture == null) {
            sender.sendMessage("ADAPT_QA ERROR operator and actor/action required");
            return true;
        }
        fixture.xp(sender(admin), args);
        return true;
    }

    static FixtureSender sender(Player admin) {
        PlayerRef player = BukkitRefs.player(admin);
        return new FixtureSender() {
            @Override
            public void send(String text) {
                admin.sendMessage(text);
            }

            @Override
            public PlayerRef player() {
                return player;
            }

            @Override
            public boolean console() {
                return false;
            }
        };
    }
}
