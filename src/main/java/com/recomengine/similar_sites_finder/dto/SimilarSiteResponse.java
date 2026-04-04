package com.recomengine.similar_sites_finder.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarSiteResponse {

    private String queriedUrl;
    private String resolvedFrom;   // "CACHE", "EXTERNAL_API", "ELASTICSEARCH", etc.
    private int totalResults;
    private List<SiteResultDto> results;
}
