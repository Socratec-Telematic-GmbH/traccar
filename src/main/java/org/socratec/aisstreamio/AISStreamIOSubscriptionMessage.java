package org.socratec.aisstreamio;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

class AISStreamIOSubscriptionMessage {

    @JsonProperty("APIKey")
    private String apiKey;

    @JsonProperty("BoundingBoxes")
    private List<Object> boundingBoxes;

    @JsonProperty("FiltersShipMMSI")
    private List<String> filtersShipMMSI;

    @JsonProperty("FilterMessageTypes")
    private List<String> filterMessageTypes;

    AISStreamIOSubscriptionMessage(String apiKey, List<Object> boundingBoxes,
                                          List<String> filtersShipMMSI, List<String> filterMessageTypes) {
        this.apiKey = apiKey;
        this.boundingBoxes = boundingBoxes != null ? boundingBoxes : List.of();
        this.filtersShipMMSI = filtersShipMMSI;
        this.filterMessageTypes = filterMessageTypes;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<Object> getBoundingBoxes() {
        return boundingBoxes;
    }

    public void setBoundingBoxes(List<Object> boundingBoxes) {
        this.boundingBoxes = boundingBoxes;
    }

    public List<String> getFiltersShipMMSI() {
        return filtersShipMMSI;
    }

    public void setFiltersShipMMSI(List<String> filtersShipMMSI) {
        this.filtersShipMMSI = filtersShipMMSI;
    }

    public List<String> getFilterMessageTypes() {
        return filterMessageTypes;
    }

    public void setFilterMessageTypes(List<String> filterMessageTypes) {
        this.filterMessageTypes = filterMessageTypes;
    }
}
