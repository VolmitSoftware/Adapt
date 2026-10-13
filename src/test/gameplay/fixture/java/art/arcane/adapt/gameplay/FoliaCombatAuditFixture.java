package art.arcane.adapt.gameplay;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.logging.Level;

public final class FoliaCombatAuditFixture extends JavaPlugin {
    @Override
    public void onEnable() {
        try {
            String source = Files.readString(Path.of(".server-source"));
            if (Bukkit.getOnlineMode() || !"127.0.0.1".equals(Bukkit.getIp())
                    || !source.lines().anyMatch("isolated=true"::equals)) {
                throw new IllegalStateException("Fixture requires an isolated offline loopback instance");
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        CombatAuditFixtures.install(this);
        SenseAuditFixtures.install(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("adaptqa") && sender.hasPermission("adaptqa.admin")
                && args.length >= 3 && (args[0].equals("temporal") || args[0].equals("items-audit") || args[0].equals("sense"))) {
            Player actor = Bukkit.getPlayerExact(args[1]);
            if (actor == null) {
                reply(sender, "ADAPT_QA ERROR player is not online");
                return true;
            }
            J.runEntity(actor, () -> {
                try {
                    String json = switch (args[0]) {
                        case "temporal" -> TemporalAuditFixtures.execute(actor, args).toString();
                        case "sense" -> SenseAuditFixtures.execute(actor, args).toString();
                        default -> ItemsAuditFixtures.execute(actor, args).toString();
                    };
                    String prefix = switch (args[0]) {
                        case "temporal" -> "TEMPORAL";
                        case "sense" -> "SENSE";
                        default -> "ITEMS_AUDIT";
                    };
                    reply(sender, "ADAPT_QA " + prefix + " " + args[2] + " 0 1 " + json);
                } catch (RuntimeException failure) {
                    getLogger().log(Level.SEVERE, "Gameplay fixture operation failed", failure);
                    reply(sender, "ADAPT_QA ERROR " + failure.getMessage());
                }
            });
            return true;
        }
        if (!sender.hasPermission("adaptcombatqa.admin") || args.length != 2 || !args[0].equals("snapshot")) {
            return false;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("ADAPT_COMBAT_QA ERROR player is not online");
            return true;
        }
        J.runEntity(player, () -> reply(sender, "ADAPT_COMBAT_QA " + CombatAuditFixtures.snapshot(player)));
        return true;
    }

    private void reply(CommandSender sender, String message) {
        if (sender instanceof Player player) J.runEntity(player, () -> player.sendMessage(message));
        else sender.sendMessage(message);
    }
}
