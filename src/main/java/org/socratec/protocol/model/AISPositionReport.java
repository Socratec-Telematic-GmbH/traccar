package org.socratec.protocol.model;

import java.time.Instant;

public record AISPositionReport(GPSCoordinates position, double sog, double cog, int trueHeading, Instant timestamp) {
    @Override
    public String toString() {
        return "AISPositionReport{" +
                "position=" + position +
                ", sog=" + sog +
                ", cog=" + cog +
                ", trueHeading=" + trueHeading +
                ", timestamp=" + timestamp +
                '}';
    }
}
