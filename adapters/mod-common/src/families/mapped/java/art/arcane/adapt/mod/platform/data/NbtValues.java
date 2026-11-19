package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.platform.data.DataValue;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** Maps SPI data values to the vanilla tag of the same primitive kind, as Bukkit persistent data does. */
public final class NbtValues {
    private NbtValues() {
    }

    public static Tag toTag(DataValue value) {
        return switch (value) {
            case DataValue.ByteValue byteValue -> ByteTag.valueOf(byteValue.value());
            case DataValue.ShortValue shortValue -> ShortTag.valueOf(shortValue.value());
            case DataValue.IntValue intValue -> IntTag.valueOf(intValue.value());
            case DataValue.LongValue longValue -> LongTag.valueOf(longValue.value());
            case DataValue.FloatValue floatValue -> FloatTag.valueOf(floatValue.value());
            case DataValue.DoubleValue doubleValue -> DoubleTag.valueOf(doubleValue.value());
            case DataValue.StringValue stringValue -> StringTag.valueOf(stringValue.value());
            case DataValue.ByteArrayValue bytes -> new ByteArrayTag(bytes.value());
            case DataValue.IntArrayValue ints -> new IntArrayTag(ints.value());
            case DataValue.LongArrayValue longs -> new LongArrayTag(longs.value());
            case DataValue.StringListValue strings -> stringList(strings.value());
        };
    }

    private static ListTag stringList(List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private static DataValue strings(ListTag list) {
        List<String> values = new ArrayList<>(list.size());
        for (Tag element : list) {
            if (!(element instanceof StringTag stringTag)) {
                return null;
            }
            values.add(stringTag.getAsString());
        }
        return new DataValue.StringListValue(values);
    }

    /** The data value stored in the tag, or null for compound and end tags and lists of anything but strings. */
    public static DataValue fromTag(Tag tag) {
        return switch (tag) {
            case ByteTag byteTag -> new DataValue.ByteValue(byteTag.getAsByte());
            case ShortTag shortTag -> new DataValue.ShortValue(shortTag.getAsShort());
            case IntTag intTag -> new DataValue.IntValue(intTag.getAsInt());
            case LongTag longTag -> new DataValue.LongValue(longTag.getAsLong());
            case FloatTag floatTag -> new DataValue.FloatValue(floatTag.getAsFloat());
            case DoubleTag doubleTag -> new DataValue.DoubleValue(doubleTag.getAsDouble());
            case StringTag stringTag -> new DataValue.StringValue(stringTag.getAsString());
            case ByteArrayTag bytes -> new DataValue.ByteArrayValue(bytes.getAsByteArray());
            case IntArrayTag ints -> new DataValue.IntArrayValue(ints.getAsIntArray());
            case LongArrayTag longs -> new DataValue.LongArrayValue(longs.getAsLongArray());
            case ListTag list -> strings(list);
            case null, default -> null;
        };
    }
}
