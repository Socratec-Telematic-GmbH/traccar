package org.socratec.model;

import org.traccar.model.BaseModel;
import org.traccar.storage.StorageName;

import java.util.Date;

@StorageName("tc_carriers")
public class Carrier extends BaseModel {

    private String carrierId;
    private CarrierType type = CarrierType.VESSEL;
    private Date createdAt;

    public String getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(String carrierId) {
        this.carrierId = carrierId;
    }

    public CarrierType getType() {
        return type;
    }

    public void setType(CarrierType type) {
        this.type = type != null ? type : CarrierType.VESSEL;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
