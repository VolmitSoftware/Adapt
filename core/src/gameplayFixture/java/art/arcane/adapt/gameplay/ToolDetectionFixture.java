package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Console checks of tool recognition: {@code toolqa probe <item> <block>} prints what every rule answers, {@code toolqa
 * spawn <name>} joins an in-process player where the platform can, {@code toolqa mine <player> <item> <block> [cube]} makes
 * that player break the block with the item through the platform break path, each time on fresh ground 64 blocks
 * further along x so area farming rules do not cut the XP (with {@code cube}, the block sits in the middle of a 3x3x3 cube
 * of itself straight ahead of the player's eyes, so area tools find the face they hit), and prints the XP each tool skill asked for, {@code toolqa learn <player> <skill> <adaptation> <level>} sets an adaptation level, and {@code toolqa quit
 * <player>} removes the player again. Items are ids, a whole stack in the platform's string form, or {@code held} to keep the
 * stack the player holds, so a tool can be worn down over several breaks.
 */
public final class ToolDetectionFixture {
    private static final String[] SKILLS = {"pickaxe", "axes", "excavation"};
    private static final int SITE_SPACING = 64;
    private static final String HELD = "held";
    private static final AtomicInteger SITES = new AtomicInteger(1);

    private ToolDetectionFixture() {
    }

    public static void execute(FixtureSender sender, FixturePlatform platform, String[] args) {
        try {
            switch (args.length == 0 ? "" : args[0]) {
                case "probe" -> GameplayFixture.sendJson(sender, "TOOLS PROBE", probe(item(args[1]),
                        Fixtures.state(args[2])));
                case "learn" -> {
                    learn(online(args[1]), args[2], args[3], Integer.parseInt(args[4]));
                    sender.send("ADAPT_QA TOOLS LEARN " + args[1] + " " + args[3] + " " + args[4]);
                }
                case "spawn" -> {
                    PlayerRef player = platform.spawnProbePlayer(args[1]);
                    sender.send("ADAPT_QA TOOLS SPAWN " + player.name() + " " + player.uuid());
                }
                case "mine" -> GameplayFixture.sendJson(sender, "TOOLS MINE", mine(online(args[1]), args[2], args[3],
                        args.length > 4 && args[4].equals("cube")));
                case "quit" -> {
                    platform.removeProbePlayer(online(args[1]));
                    sender.send("ADAPT_QA TOOLS QUIT " + args[1]);
                }
                default -> sender.send("ADAPT_QA ERROR toolqa probe|spawn|mine|quit");
            }
        } catch (RuntimeException failure) {
            sender.send("ADAPT_QA ERROR toolqa " + failure);
        }
    }

    private static JsonObject probe(ItemRef item, BlockStateRef block) {
        JsonObject result = new JsonObject();
        result.addProperty("item", item.type().toString());
        result.addProperty("block", block.type().toString());
        JsonObject kinds = new JsonObject();
        for (ToolKind kind : ToolKind.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("match", Tools.match(item, kind).name());
            entry.addProperty("serves", Tools.serves(item, block, kind));
            entry.addProperty("claims", Tools.claims(kind, block.type()));
            kinds.add(kind.name(), entry);
        }
        result.add("kinds", kinds);
        result.addProperty("loaderActions", Fixtures.items().toolActions(item).toString());
        result.addProperty("correctTool", block.isCorrectTool(item));
        result.addProperty("effective", Tools.isEffective(item, block));
        return result;
    }

    private static JsonObject mine(PlayerRef player, String item, String block, boolean cube) {
        Loc at = player.location();
        int site = SITES.getAndIncrement();
        Fixtures.teleport(player, player.world(), SITE_SPACING * site + 0.5D, at.y(), at.z());
        int x = SITE_SPACING * site + 2;
        int y = (int) Math.floor(at.y());
        int z = (int) Math.floor(at.z());
        if (cube) {
            x = SITE_SPACING * site;
            y++;
            z += 3;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Fixtures.set(player.world(), x + dx, y + dy, z + dz, block);
                    }
                }
            }
        } else {
            Fixtures.set(player.world(), x, y, z, block);
        }
        BlockRef target = player.world().blockAt(x, y, z);
        if (!item.equals(HELD)) {
            for (int slot = 0; slot < player.inventory().size(); slot++) {
                player.inventory().setItem(slot, Fixtures.items().empty());
            }
            player.inventory().setMainHand(item(item));
        }
        JsonObject before = requested(player);
        JsonObject result = probe(player.inventory().mainHand(), target.state());
        result.addProperty("player", player.name());
        result.addProperty("broken", AdaptCore.platform().protection().attemptBreak(player, target));
        result.addProperty("after", target.type().toString());
        if (cube) {
            int cleared = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (player.world().blockAt(x + dx, y + dy, z + dz).isAir()) {
                            cleared++;
                        }
                    }
                }
            }
            result.addProperty("cubeCleared", cleared);
        }
        ItemRef held = player.inventory().mainHand();
        result.addProperty("heldDamage", held.damage() + "/" + held.maxDamage());
        JsonObject inventory = new JsonObject();
        for (ItemRef stack : player.inventory().contents()) {
            if (stack != null && !stack.isEmpty() && stack != held) {
                String key = stack.type().toString();
                inventory.addProperty(key, (inventory.has(key) ? inventory.get(key).getAsInt() : 0) + stack.amount());
            }
        }
        result.add("inventory", inventory);
        int ground = 0;
        for (EntityRef entity : player.world().nearbyEntities(target.center().position(), 6D, 6D, 6D)) {
            if (entity instanceof ItemEntityRef) {
                ground++;
            }
        }
        result.addProperty("groundItems", ground);
        JsonObject after = requested(player);
        JsonObject gained = new JsonObject();
        for (String skill : SKILLS) {
            gained.addProperty(skill, after.get(skill).getAsDouble() - before.get(skill).getAsDouble());
        }
        result.add("xpRequested", gained);
        return result;
    }

    private static JsonObject requested(PlayerRef player) {
        AdaptPlayer runtime = AdaptCore.get().getAdaptServer().getOnlineAdaptPlayer(player.uuid());
        JsonObject out = new JsonObject();
        for (String skill : SKILLS) {
            String prefix = Id.adapt(skill) + "/";
            double total = 0D;
            if (runtime != null) {
                for (XpEarnings.SourceSnapshot source : runtime.getData().getXpEarnings().snapshot()) {
                    if (source.source().startsWith(prefix)) {
                        total += source.requested();
                    }
                }
            }
            out.addProperty(skill, total);
        }
        return out;
    }

    /** An item id, or a whole stack in the platform's own string form when it starts with a brace. */
    private static ItemRef item(String value) {
        return value.startsWith("{") ? Fixtures.items().decode(value) : Fixtures.item(value);
    }

    private static void learn(PlayerRef player, String skillName, String adaptationPath, int level) {
        Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(skillName);
        AdaptPlayer runtime = AdaptCore.get().getAdaptServer().getOnlineAdaptPlayer(player.uuid());
        if (skill == null || runtime == null) {
            throw new IllegalArgumentException("no skill " + skillName + " or player " + player.name());
        }
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
            if (adaptation.getId().path().equals(adaptationPath)) {
                runtime.getData().getSkillLine(skillName).setAdaptation(adaptation, level);
                return;
            }
        }
        throw new IllegalArgumentException("no adaptation " + adaptationPath + " in " + skillName);
    }

    private static PlayerRef online(String name) {
        PlayerRef player = Fixtures.host().player(name);
        if (player == null) {
            throw new IllegalArgumentException("no online player " + name);
        }
        return player;
    }
}
