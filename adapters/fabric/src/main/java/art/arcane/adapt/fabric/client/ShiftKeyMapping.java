package art.arcane.adapt.fabric.client;

import art.arcane.adapt.client.ShiftChord;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A binding whose default is Shift plus its key, for Fabric, which has no key modifiers. While it sits on its default
 * key it stays out of vanilla's key table, so the plain key keeps its vanilla meaning, and {@code KeyMappingShiftMixin}
 * hands it the Shift presses of that key before vanilla sees them.
 */
public final class ShiftKeyMapping extends KeyMapping {
    private static final List<ShiftKeyMapping> CHORDS = new ArrayList<>();

    private int presses;

    public ShiftKeyMapping(String name, InputConstants.Key key, Category category) {
        super(name, key.getType(), key.getValue(), category);
        CHORDS.add(this);
    }

    public static boolean claim(InputConstants.Key key) {
        boolean shiftDown = Minecraft.getInstance().hasShiftDown();
        boolean claimed = false;
        for (ShiftKeyMapping mapping : CHORDS) {
            if (key.equals(mapping.getDefaultKey()) && ShiftChord.claims(mapping.isDefault(), shiftDown)) {
                mapping.presses++;
                claimed = true;
            }
        }
        return claimed;
    }

    public static boolean keyTable(KeyMapping mapping, InputConstants.Key key) {
        return !(mapping instanceof ShiftKeyMapping) || ShiftChord.plainKey(key.equals(mapping.getDefaultKey()));
    }

    public static boolean chorded(KeyMapping mapping) {
        return mapping instanceof ShiftKeyMapping && mapping.isDefault();
    }

    @Override
    public boolean consumeClick() {
        if (presses > 0) {
            presses--;
            return true;
        }
        return super.consumeClick();
    }

    @Override
    public Component getTranslatedKeyMessage() {
        Component key = super.getTranslatedKeyMessage();
        return isDefault() ? Component.translatable(ShiftChord.LABEL, key) : key;
    }
}
