package org.socratec.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CarrierType {
    VESSEL(0);

    private final int value;

    CarrierType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static CarrierType fromValue(int value) {
        for (CarrierType type : CarrierType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        return VESSEL; // Default fallback
    }
}
