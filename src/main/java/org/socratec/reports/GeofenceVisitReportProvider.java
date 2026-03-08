package org.socratec.reports;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import jakarta.inject.Inject;
import org.apache.velocity.app.VelocityEngine;
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
import java.io.StringWriter;
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

    public void getPdf(OutputStream outputStream,
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(from, to);

        Collection<GeofenceVisit> visits = getObjects(userId, deviceIds, groupIds, from, to);

        // Initialize Velocity Engine
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loaders", "file");
        velocityEngine.setProperty("resource.loader.file.class",
                "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        velocityEngine.setProperty("resource.loader.file.path",
                config.getString(Keys.TEMPLATES_ROOT) + "/export");
        velocityEngine.init();

        // Create Velocity Context using reportUtils for consistency
        var velocityContext = reportUtils.initializeVelocityContext(userId);
        velocityContext.put("items", visits);
        velocityContext.put("from", from);
        velocityContext.put("to", to);

        // Process template
        StringWriter writer = new StringWriter();
        velocityEngine.getTemplate("geofence-visits.vm").merge(velocityContext, writer);
        String htmlContent = writer.toString();

        // Create PDF document with landscape orientation
        PdfWriter pdfWriter = new PdfWriter(outputStream);
        PdfDocument pdfDocument = new PdfDocument(pdfWriter);
        pdfDocument.setDefaultPageSize(PageSize.A4.rotate());

        // Configure converter properties
        ConverterProperties converterProperties = new ConverterProperties();

        // Convert HTML to PDF
        HtmlConverter.convertToPdf(htmlContent, pdfDocument, converterProperties);

        pdfDocument.close();
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
