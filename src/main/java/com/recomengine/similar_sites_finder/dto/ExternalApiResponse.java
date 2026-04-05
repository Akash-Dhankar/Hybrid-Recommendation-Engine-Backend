package com.recomengine.similar_sites_finder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalApiResponse {

    // Maps to "similar_sites": ["stackoverflow.com", "medium.com", ...]
    @JsonProperty("similar_sites")
    private List<String> similarSites;
}
