package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.item.ItemCooldowns;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.client.server.hud.ClientHud;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the ability glyphs and skill activity of the client HUD for a real client. {@code prepare} learns Architect
 * Foundation and a three-servant Skeletal Servant and hands out bones in the second hotbar slot, so a held sneak raises a
 * foundation and right clicks summon servants. {@code mark <adaptation> <millis>} marks that adaptation's own reuse gate
 * as its ability code would, {@code xp <skill> <amount>} awards xp the way play does, {@code flood <n>} puts n glyphs
 * up at once and {@code clear} drops every glyph.
 */
public final class HudFixtures {
    private static final int SERVANT_LEVEL = 3;

    private HudFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        JsonObject result = new JsonObject();
        result.addProperty("op", args[2]);
        switch (args[2]) {
            case "prepare" -> prepare(player);
            case "mark" -> result.addProperty("gate", mark(player, Fixtures.adaptation(args[3]), Long.parseLong(args[4])));
            case "xp" -> Fixtures.skill(args[3]).xp(player, Double.parseDouble(args[4]));
            case "flood" -> result.addProperty("glyphs", flood(player, Integer.parseInt(args[3])));
            case "clear" -> ClientHud.send(player.uuid(), ClientProtocol.HUD, new ServerFrame.HudClearAll());
            default -> throw new IllegalArgumentException("Unknown hud fixture: " + args[2]);
        }
        return result;
    }

    private static void prepare(PlayerRef player) {
        if (player.gameMode() != GameMode.SURVIVAL) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        Adaptation<?> foundation = Fixtures.adaptation("adapt:architect/foundation");
        GolemControlsFixtures.learn(player, foundation.getId().toString(), foundation.getMaxLevel());
        GolemControlsFixtures.learn(player, "adapt:tragoul/skeletal_servant", SERVANT_LEVEL);
        player.inventory().setItem(1, Fixtures.item("bone", 64));
    }

    /**
     * {@code count} glyphs at once from as many adaptations, as when everything is on cooldown: six in ten are
     * cooldowns of one to two minutes (marked on the adaptation's own gate when it has one), two charges, one a
     * running effect and one a stance. Returns how many were sent.
     */
    private static int flood(PlayerRef player, int count) {
        List<Adaptation<?>> adaptations = new ArrayList<>();
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            adaptations.addAll(skill.getAdaptations());
        }
        long now = System.currentTimeMillis();
        int sent = 0;
        for (int index = 0; index < count && index < adaptations.size(); index++) {
            Adaptation<?> adaptation = adaptations.get(index);
            int kind = index % 10;
            long duration = 60_000L + (index * 7_919L) % 60_000L;
            boolean shown;
            if (kind < 6) {
                shown = AbilityGlyphs.cooldown(player.uuid(), adaptation, now, duration);
            } else if (kind < 8) {
                shown = AbilityGlyphs.charges(player.uuid(), adaptation, 1 + index % 4, 4);
            } else if (kind == 8) {
                shown = AbilityGlyphs.duration(player.uuid(), adaptation, now, duration);
            } else {
                shown = AbilityGlyphs.toggle(player.uuid(), adaptation, true);
            }
            if (shown) {
                sent++;
            }
        }
        return sent;
    }

    /** The first reuse gate the adaptation holds, marked for {@code millis}; returns the gate's field name. */
    private static String mark(PlayerRef player, Adaptation<?> adaptation, long millis) {
        for (Class<?> type = adaptation.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != Cooldowns.class && field.getType() != ItemCooldowns.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object gate = field.get(adaptation);
                    if (gate instanceof Cooldowns cooldowns) {
                        cooldowns.mark(player.uuid(), millis);
                    } else if (gate instanceof ItemCooldowns itemCooldowns) {
                        itemCooldowns.mark(player, millis);
                    }
                    return field.getName();
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException("Cannot read " + field, failure);
                }
            }
        }
        throw new IllegalArgumentException(adaptation.getId().toString() + " has no reuse gate");
    }
}
