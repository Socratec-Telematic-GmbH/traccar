package org.socratec.model;

/**
 * Represents the number of times a device visited a geofence within a time period.
 */
public class GeofenceVisit {

    private long deviceId;
    private String deviceName;
    private String deviceUniqueId;
    private long geofenceId;
    private String geofenceName;
    private int visitCount;

    public GeofenceVisit() {
    }

    public GeofenceVisit(long deviceId, String deviceName, String deviceUniqueId,
                        long geofenceId, String geofenceName, int visitCount) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceUniqueId = deviceUniqueId;
        this.geofenceId = geofenceId;
        this.geofenceName = geofenceName;
        this.visitCount = visitCount;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceUniqueId() {
        return deviceUniqueId;
    }

    public void setDeviceUniqueId(String deviceUniqueId) {
        this.deviceUniqueId = deviceUniqueId;
    }

    public long getGeofenceId() {
        return geofenceId;
    }

    public void setGeofenceId(long geofenceId) {
        this.geofenceId = geofenceId;
    }

    public String getGeofenceName() {
        return geofenceName;
    }

    public void setGeofenceName(String geofenceName) {
        this.geofenceName = geofenceName;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }
}
