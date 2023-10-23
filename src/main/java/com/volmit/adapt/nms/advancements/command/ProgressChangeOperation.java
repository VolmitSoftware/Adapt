package com.volmit.adapt.nms.advancements.command;

public enum ProgressChangeOperation {
	
	SET {
		@Override
		public int apply(int base, int amount) {
			return amount;
		}
	},
	
	ADD {
		@Override
		public int apply(int base, int amount) {
			return base + amount;
		}
	},
	
	REMOVE {
		@Override
		public int apply(int base, int amount) {
			return base - amount;
		}
	},
	
	MULTIPLY {
		@Override
		public int apply(int base, int amount) {
			return base * amount;
		}
	},
	
	DIVIDE {
		@Override
		public int apply(int base, int amount) {
			return (int) Math.floor(base * 1d / amount);
		}
	},
	
	POWER {
		@Override
		public int apply(int base, int amount) {
			return (int) Math.pow(base, amount);
		}
	},
	
	;
	
	/**
	 * Applies this Operation to a given base with the specified amount
	 * 
	 * @param base The Base to use
	 * @param amount The Amount to use
	 * @return The Output
	 */
	public abstract int apply(int base, int amount);
	
	/**
	 * Parses the ProgressChangeOperation from a given Input
	 * 
	 * @param input The Input to parse from
	 * @return The parsed Operation or {@link ProgressChangeOperation#SET} if parsing fails
	 */
	public static ProgressChangeOperation parse(String input) {
		for(ProgressChangeOperation op : values()) {
			if(op.name().equalsIgnoreCase(input)) {
				return op;
			}
		}
		return SET;
	}
	
}