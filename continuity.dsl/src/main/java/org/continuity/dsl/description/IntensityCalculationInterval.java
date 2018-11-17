package org.continuity.dsl.description;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntensityCalculationInterval {
	SECONDLY {

		@Override
		public long asNumber() {
			return 1000000000L;
		}

	},
	MINUTELY {

		@Override
		public long asNumber() {
			return 60000000000L;
		}

	},
	HOURLY {

		@Override
		public long asNumber() {
			return 3600000000000L;
		}

	};

	private static final Map<String, IntensityCalculationInterval> prettyStringToInterval = new HashMap<>();

	static {
		for (IntensityCalculationInterval interval : values()) {
			prettyStringToInterval.put(interval.toPrettyString(), interval);
		}
	}

	@JsonCreator
	public static IntensityCalculationInterval fromPrettyString(String key) {
		return prettyStringToInterval.get(key);
	}

	@JsonValue
	public String toPrettyString() {
		return name().replace("_", "-").toLowerCase();
	}
	/**
	 * Returns interval in nanos
	 * @return interval in nanos
	 */
	public abstract long asNumber();
}
