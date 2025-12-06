package org.socratec.aisstreamio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class AISStreamIOMessage {

    @JsonProperty("Message")
    private Message message;

    @JsonProperty("MetaData")
    private MetaData metaData;

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public MetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(MetaData metaData) {
        this.metaData = metaData;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("PositionReport")
        private PositionReport positionReport;

        public PositionReport getPositionReport() {
            return positionReport;
        }

        public void setPositionReport(PositionReport positionReport) {
            this.positionReport = positionReport;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PositionReport {
        @JsonProperty("Latitude")
        private double latitude;

        @JsonProperty("Longitude")
        private double longitude;

        @JsonProperty("Sog")
        private double sog;

        @JsonProperty("Cog")
        private double cog;

        @JsonProperty("TrueHeading")
        private int trueHeading;

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public double getSog() {
            return sog;
        }

        public void setSog(double sog) {
            this.sog = sog;
        }

        public double getCog() {
            return cog;
        }

        public void setCog(double cog) {
            this.cog = cog;
        }

        public int getTrueHeading() {
            return trueHeading;
        }

        public void setTrueHeading(int trueHeading) {
            this.trueHeading = trueHeading;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetaData {
        @JsonProperty("MMSI")
        private String mmsi;

        @JsonProperty("time_utc")
        private String timeUtc;

        public String getTimeUtc() {
            return timeUtc;
        }

        public void setTimeUtc(String timeUtc) {
            this.timeUtc = timeUtc;
        }

        public String getMmsi() {
            return mmsi;
        }

        public void setMmsi(String mmsi) {
            this.mmsi = mmsi;
        }
    }
}
