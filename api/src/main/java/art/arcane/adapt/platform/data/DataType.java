package art.arcane.adapt.platform.data;

import org.jetbrains.annotations.ApiStatus;

/**
 * Primitive storage kinds shared by Bukkit persistent data containers and vanilla compound tags.
 * Adapters map each kind to the matching native type (INT to INTEGER, INT_ARRAY to INTEGER_ARRAY)
 * so values already stored in existing worlds stay readable.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum DataType {
  BYTE,
  SHORT,
  INT,
  LONG,
  FLOAT,
  DOUBLE,
  STRING,
  BYTE_ARRAY,
  INT_ARRAY,
  LONG_ARRAY,
  STRING_LIST
}
