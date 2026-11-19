package art.arcane.adapt.gameplay.mod;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.gameplay.FixtureSender;
import art.arcane.adapt.gameplay.GameplayFixture;
import art.arcane.adapt.platform.entity.PlayerRef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ModFixtureCommand {
    private static final String ARGUMENTS = "arguments";

    private final String loader;
    private GameplayFixture fixture;
    private String failure;

    public ModFixtureCommand(String loader) {
        this.loader = loader;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adaptqa")
                .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(), ""))
                .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(),
                                StringArgumentType.getString(context, ARGUMENTS)))));
        dispatcher.register(Commands.literal("xpqa")
                .executes((CommandContext<CommandSourceStack> context) -> runXp(context.getSource(), ""))
                .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .executes((CommandContext<CommandSourceStack> context) -> runXp(context.getSource(),
                                StringArgumentType.getString(context, ARGUMENTS)))));
    }

    public void serverStopping(MinecraftServer server) {
        if (fixture != null) {
            fixture.stop();
            fixture = null;
        }
    }

    private int run(CommandSourceStack source, String text) {
        FixtureSender sender = sender(source);
        GameplayFixture active = active(source.getServer());
        if (active == null) {
            sender.send("ADAPT_QA ERROR " + failure);
            return 0;
        }
        String[] args = text.isBlank() ? new String[0] : text.trim().split(" +");
        if (!active.execute(sender, args)) {
            sender.send("ADAPT_QA ERROR invalid fixture command");
        }
        return 1;
    }

    private int runXp(CommandSourceStack source, String text) {
        FixtureSender sender = sender(source);
        GameplayFixture active = active(source.getServer());
        if (active == null) {
            sender.send("ADAPT_QA ERROR " + failure);
            return 0;
        }
        active.xp(sender, text.isBlank() ? new String[0] : text.trim().split(" +"));
        return 1;
    }

    private GameplayFixture active(MinecraftServer server) {
        if (fixture != null) {
            return fixture;
        }
        if (AdaptCore.current() == null) {
            failure = "Adapt core is not running";
            return null;
        }
        try {
            fixture = GameplayFixture.start(new ModFixturePlatform(loader, server));
        } catch (IllegalStateException refused) {
            failure = refused.getMessage();
            AdaptCore.platform().host().log().error("Adapt gameplay fixture refused this server", refused);
        }
        return fixture;
    }

    private static FixtureSender sender(CommandSourceStack source) {
        return new FixtureSender() {
            @Override
            public void send(String text) {
                source.sendSystemMessage(Component.literal(text));
            }

            @Override
            public PlayerRef player() {
                ServerPlayer player = source.getPlayer();
                return player == null || AdaptCore.current() == null ? null : AdaptCore.platform().host().player(player.getUUID());
            }

            @Override
            public boolean console() {
                return source.getPlayer() == null;
            }
        };
    }
}
