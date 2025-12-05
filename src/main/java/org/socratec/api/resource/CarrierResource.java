package org.socratec.api.resource;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.model.Carrier;
import org.socratec.protocol.AisStreamService;
import org.socratec.protocol.model.AISPositionReport;
import org.traccar.ProcessingHandler;
import org.traccar.api.BaseResource;
import org.traccar.helper.LogAction;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.stream.Collectors;

@Path("carriers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CarrierResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CarrierResource.class);

    @Inject
    private LogAction actionLogger;

    @Inject
    private ProcessingHandler processingHandler;

    @Inject
    private AisStreamService aisStreamService;

    @Context
    private HttpServletRequest request;

    @GET
    public Collection<Carrier> get(
            @QueryParam("all") boolean all,
            @QueryParam("userId") long userId) throws StorageException {

        var conditions = new LinkedList<Condition>();

        if (all) {
            if (permissionsService.notAdmin(getUserId())) {
                conditions.add(new Condition.Permission(User.class, getUserId(), Device.class));
            }
        } else {
            if (userId == 0) {
                conditions.add(new Condition.Permission(User.class, getUserId(), Device.class));
            } else {
                permissionsService.checkUser(getUserId(), userId);
                conditions.add(new Condition.Permission(User.class, userId, Device.class).excludeGroups());
            }
        }

        return storage.getObjects(Carrier.class, new Request(
                new Columns.All(), Condition.merge(conditions), new Order("id")));
    }

    @POST
    public Response add(Carrier entity) throws Exception {
        if (entity.getId() == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"id is required\"}")
                    .build();
        }

        if (entity.getCarrierId() == null || entity.getCarrierId().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"carrierId is required\"}")
                    .build();
        }

        permissionsService.checkPermission(Device.class, getUserId(), entity.getId());

        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(),
                new Condition.Equals("id", entity.getId())));

        if (device == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Device not found\"}")
                    .build();
        }

        entity.setCreatedAt(new Date());

        storage.addObject(entity, new Request(new Columns.All()));
        actionLogger.create(request, getUserId(), entity);

        return Response.ok(entity).build();
    }

    @Path("{deviceId}")
    @DELETE
    public Response remove(@PathParam("deviceId") long deviceId) throws Exception {

        permissionsService.checkPermission(Device.class, getUserId(), deviceId);

        storage.removeObject(Carrier.class, new Request(
                new Condition.Equals("id", deviceId)));

        actionLogger.remove(request, getUserId(), Carrier.class, deviceId);

        return Response.noContent().build();
    }

    @Path("startTracking")
    @POST
    public Response startTracking() throws StorageException {
        LOGGER.info("start tracking");
        var carriers = storage.getObjects(Carrier.class, new Request(new Columns.All()));
        var uniqueCarrierIds = carriers.stream()
                .map(Carrier::getCarrierId)
                .collect(Collectors.toSet());

        aisStreamService.connectToAisStream(uniqueCarrierIds, this::processMessage);

        var response = new java.util.HashMap<String, Object>();
        response.put("message", "start tracking");
        response.put("carrierIds", uniqueCarrierIds);
        return Response.ok(response).build();
    }

    @Path("stopTracking")
    @POST
    public Response stopTracking() throws StorageException {
        LOGGER.info("stop tracking");
        aisStreamService.shutdown();
        return Response.ok()
                .entity("{\"message\": \"stop tracking\"}")
                .build();
    }

    private void processMessage(AISPositionReport aisPosition) {
        try {
            // Find all carriers with the given MMSI (carrierId)
            Collection<Carrier> carriers = storage.getObjects(Carrier.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("carrierId", aisPosition.mmsi())));

            if (carriers.isEmpty()) {
                LOGGER.warn("Carrier with MMSI {} not tracked.", aisPosition.mmsi());
                return;
            }

            // Create and process a position for each device associated with this MMSI
            for (Carrier carrier : carriers) {
                Position position = mapToPosition(aisPosition, carrier.getId());
                processingHandler.onReleased(null, position);
                LOGGER.debug("Position queued for processing for device {} (MMSI: {})",
                        carrier.getId(), aisPosition.mmsi());
            }
        } catch (StorageException e) {
            LOGGER.error("Failed to process AIS position message for MMSI: {}", aisPosition.mmsi(), e);
        }
    }

    private static Position mapToPosition(AISPositionReport aisPosition, long deviceId) {
        Position position = new Position();
        position.setProtocol("AIS");
        position.setDeviceId(deviceId);

        // Set timestamps
        Date timestamp = Date.from(aisPosition.timestamp());
        position.setServerTime(new Date());
        position.setDeviceTime(timestamp);
        position.setFixTime(timestamp);

        // Set GPS coordinates
        position.setValid(true);
        position.setLatitude(aisPosition.position().latitude());
        position.setLongitude(aisPosition.position().longitude());

        // Set speed (SOG - Speed Over Ground) in knots
        position.setSpeed(aisPosition.sog());

        // Set course (COG - Course Over Ground)
        position.setCourse(aisPosition.cog());

        // Set altitude to 0 for marine vessels
        position.setAltitude(0);

        // Store additional AIS-specific data as attributes
        position.set(Position.KEY_TYPE, "AIS");
        position.set("MMSI", aisPosition.mmsi());
        position.set("trueHeading", aisPosition.trueHeading());

        return position;
    }
}
