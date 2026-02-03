package org.socratec.reports;

import jakarta.inject.Inject;
import org.jxls.util.JxlsHelper;
import org.socratec.model.GeofenceVisit;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.reports.common.ReportUtils;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GeofenceVisitReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public GeofenceVisitReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    public Collection<GeofenceVisit> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException {
        reportUtils.checkPeriodLimit(from, to);

        ArrayList<GeofenceVisit> result = new ArrayList<>();

        // Get accessible devices
        Collection<Device> devices = DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds);

        for (Device device : devices) {
            // Query geofenceEnter events for this device within the time range
            Collection<Event> events = storage.getObjects(Event.class, new Request(
                    new Columns.All(),
                    new Condition.And(
                            new Condition.Equals("deviceId", device.getId()),
                            new Condition.Between("eventTime", "from", from, "to", to)
                    )
            ));

            // Filter by event type in memory since we can't use three conditions
            events = events.stream()
                    .filter(event -> Event.TYPE_GEOFENCE_ENTER.equals(event.getType()))
                    .toList();

            // Count visits per geofence
            Map<Long, Integer> geofenceVisitCounts = new HashMap<>();
            for (Event event : events) {
                long geofenceId = event.getGeofenceId();
                geofenceVisitCounts.put(geofenceId, geofenceVisitCounts.getOrDefault(geofenceId, 0) + 1);
            }

            // Create GeofenceVisit objects
            for (Map.Entry<Long, Integer> entry : geofenceVisitCounts.entrySet()) {
                long geofenceId = entry.getKey();
                int visitCount = entry.getValue();

                // Fetch geofence details
                Geofence geofence = storage.getObject(Geofence.class, new Request(
                        new Columns.All(),
                        new Condition.Equals("id", geofenceId)
                ));

                if (geofence != null) {
                    GeofenceVisit visit = new GeofenceVisit(
                            device.getId(),
                            device.getName(),
                            device.getUniqueId(),
                            geofence.getId(),
                            geofence.getName(),
                            visitCount
                    );
                    result.add(visit);
                }
            }
        }

        return result;
    }

    public void getExcel(OutputStream outputStream,
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(from, to);

        Collection<GeofenceVisit> visits = getObjects(userId, deviceIds, groupIds, from, to);

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "geofence-visits.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = reportUtils.initializeContext(userId);
            context.putVar("items", visits);
            context.putVar("from", from);
            context.putVar("to", to);
            JxlsHelper.getInstance().setUseFastFormulaProcessor(false)
                    .processTemplate(inputStream, outputStream, context);
        }
    }
}
