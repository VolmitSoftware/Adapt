package art.arcane.adapt.gameplay;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.gameplay.demo.DemoScope;
import art.arcane.adapt.gameplay.demo.DemoSkin;
import art.arcane.adapt.gameplay.demo.DemoStudio;
import art.arcane.adapt.gameplay.demo.PassiveComparisonFixtures;
import art.arcane.adapt.gameplay.demo.sets.StructureDiscoverySets;
import art.arcane.adapt.platform.entity.PlayerRef;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class AdaptGameplayFixture extends JavaPlugin {
    private final DemoStudio demoStudio = new DemoStudio(this);
    private GameplayFixture fixture;

    @Override
    public void onEnable() {
        try {
            fixture = GameplayFixture.start(new BukkitFixturePlatform());
            DemoSkin.install(this);
            SenseAuditFixtures.install(this);
        } catch (IllegalStateException failure) {
            getLogger().log(Level.SEVERE, "Adapt gameplay fixture refused this server", failure);
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
        if (command.getName().equals("xpqa")) {
            if (!(sender instanceof Player admin) || fixture == null) {
                sender.sendMessage("ADAPT_QA ERROR operator and actor/action required");
            } else {
                fixture.xp(sender(admin), args);
            }
            return true;
        }
        if (args.length >= 2 && args[0].equals("demo")) {
            demoCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player admin) || !admin.isOp()) {
            sender.sendMessage("ADAPT_QA ERROR operator player required");
            return true;
        }
        try {
            if (args.length >= 3 && args[0].equals("sense")) {
                sendJson(sender, "SENSE " + args[2], SenseAuditFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (ItemsRetirementFixtures.handles(args)) {
                sendJson(sender, "ITEMS_AUDIT " + args[2], ItemsRetirementFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (fixture == null || !fixture.execute(sender(admin), args)) {
                sender.sendMessage("ADAPT_QA ERROR invalid fixture command");
            }
        } catch (RuntimeException failure) {
            reportFailure(sender, failure);
        }
        return true;
    }

    private void demoCommand(CommandSender sender, String[] args) {
        if (!demoOperator(sender)) {
            sender.sendMessage("ADAPT_QA ERROR console or operator player required");
            return;
        }
        try {
            sender.sendMessage(demo(args));
        } catch (RuntimeException failure) {
            reportFailure(sender, failure);
        }
    }

    private String demo(String[] args) {
        return switch (args[1]) {
            case "world" -> demoStudio.world(demoArgument(args, 2));
            case "warm" -> GolemancyDemoFixtures.warm();
            case "locate" -> demoStudio.locate(Integer.parseInt(demoArgument(args, 2)), Integer.parseInt(demoArgument(args, 3)));
            case "plate" -> demoStudio.plate(Integer.parseInt(demoArgument(args, 2)), Integer.parseInt(demoArgument(args, 3)));
            case "set" -> demoStudio.set(demoArgument(args, 2));
            case "actor" -> demoStudio.actor(demoArgument(args, 2), opponentRole(args));
            case "park" -> demoStudio.park(demoArgument(args, 2));
            case "sparring" -> demoStudio.sparring();
            case "hit" -> demoStudio.hit(demoArgument(args, 2), demoArgument(args, 3));
            case "sync" -> demoStudio.sync(demoArgument(args, 2));
            case "preference" -> StructureDiscoverySets.preference(ordinaryPlayer(demoArgument(args, 2)), demoArgument(args, 3),
                    demoArgument(args, 4), demoArgument(args, 5));
            case "passive" -> PassiveComparisonFixtures.control(ordinaryPlayer(demoArgument(args, 2)), demoArgument(args, 3),
                    demoArgument(args, 4));
            case "give" -> GolemancyDemoFixtures.give(ordinaryPlayer(demoArgument(args, 2)), Integer.parseInt(demoArgument(args, 3)),
                    demoArgument(args, 4), Integer.parseInt(demoArgument(args, 5)));
            case "bind" -> GolemancyDemoFixtures.bind(ordinaryPlayer(demoArgument(args, 2)), demoArgument(args, 3), demoArgument(args, 4));
            case "probe" -> loggedProbe(ordinaryPlayer(demoArgument(args, 2)));
            case "near" -> GolemancyDemoFixtures.near(ordinaryPlayer(demoArgument(args, 2)));
            case "lumber" -> lumber(args);
            case "reset" -> demoStudio.reset();
            default -> throw new IllegalArgumentException("Unknown demo command " + args[1]);
        };
    }

    private String lumber(String[] args) {
        return switch (demoArgument(args, 2)) {
            case "scan" -> LumberProbeFixtures.scan(demoArgument(args, 3), Integer.parseInt(demoArgument(args, 4)), Integer.parseInt(demoArgument(args, 5)),
                    Integer.parseInt(demoArgument(args, 6)), getLogger());
            case "dump" -> LumberProbeFixtures.dump(demoArgument(args, 3), Integer.parseInt(demoArgument(args, 4)), Integer.parseInt(demoArgument(args, 5)),
                    Integer.parseInt(demoArgument(args, 6)), getLogger());
            case "node" -> LumberProbeFixtures.node(ordinaryPlayer(demoArgument(args, 3)), Integer.parseInt(demoArgument(args, 4)),
                    Integer.parseInt(demoArgument(args, 5)), Integer.parseInt(demoArgument(args, 6)), Integer.parseInt(demoArgument(args, 7)));
            default -> throw new IllegalArgumentException("Unknown lumber probe " + args[2]);
        };
    }

    private static String demoArgument(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing argument for demo " + args[1]);
        }
        return args[index];
    }

    private static boolean opponentRole(String[] args) {
        if (args.length <= 3) {
            return false;
        }
        if (!args[3].equals("opponent")) {
            throw new IllegalArgumentException("demo actor takes opponent as its only role, not " + args[3]);
        }
        return true;
    }

    private static boolean demoOperator(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || sender instanceof RemoteConsoleCommandSender
                || sender instanceof Player player && player.isOp();
    }

    private void reportFailure(CommandSender sender, RuntimeException failure) {
        getLogger().log(Level.SEVERE, "Adapt gameplay fixture command failed", failure);
        sender.sendMessage("ADAPT_QA ERROR " + failure.getMessage());
    }

    private void sendJson(CommandSender sender, String response, JsonObject value) {
        String json = value.toString();
        int total = (json.length() + 2499) / 2500;
        for (int index = 0; index < total; index++) {
            sender.sendMessage("ADAPT_QA " + response + " " + index + " " + total + " "
                    + json.substring(index * 2500, Math.min(json.length(), (index + 1) * 2500)));
        }
    }

    private String loggedProbe(Player owner) {
        String report = GolemancyDemoFixtures.probe(owner);
        getLogger().info(report);
        return report;
    }

    private Player ordinaryPlayer(String name) {
        Player player = Objects.requireNonNull(Bukkit.getPlayerExact(name), "Player is not online");
        if (player.isOp() || !DemoScope.ownsDemoState(name)) {
            throw new IllegalArgumentException("Fixture actor must be an ordinary studio player");
        }
        return player;
    }

    private static FixtureSender sender(Player admin) {
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
