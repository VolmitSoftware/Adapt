package art.arcane.adapt.mod.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.entity.PlayerRef;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

/**
 * A vanilla command source as the core command tree sees it. A player's permissions go through Adapt's permission
 * host; the console and other sources count as operators when they hold the gamemaster level. Replies reach the source
 * on the server thread.
 */
public record ModCommandSource(CommandSourceStack source) implements CommandSource {
    private static final int GAMEMASTER_LEVEL = 2;
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    public ModCommandSource {
        Objects.requireNonNull(source, "source");
    }

    @Override
    public String name() {
        return source.getTextName();
    }

    @Override
    public Optional<PlayerRef> player() {
        ServerPlayer player = source.getPlayer();
        return player == null ? Optional.empty() : Optional.of(ModRefs.player(player));
    }

    @Override
    public boolean hasPermission(String node) {
        Optional<PlayerRef> player = player();
        if (player.isPresent()) {
            AdaptCore core = AdaptCore.current();
            return core != null && core.getPlatform().permissions().has(player.get(), node);
        }
        return isOp();
    }

    @Override
    public boolean isOp() {
        return source.hasPermission(GAMEMASTER_LEVEL);
    }

    @Override
    public void sendSection(String sectionText) {
        send(ModText.vanilla(SECTION.deserialize(sectionText)));
    }

    @Override
    public void sendLiteral(String plainText) {
        send(Component.literal(plainText));
    }

    @Override
    public void sendMarkup(String markup) {
        send(ModText.component(markup));
    }

    private void send(Component message) {
        MinecraftServer server = source.getServer();
        if (server == null || server.isSameThread()) {
            source.sendSystemMessage(message);
        } else {
            server.execute(() -> source.sendSystemMessage(message));
        }
    }
}
