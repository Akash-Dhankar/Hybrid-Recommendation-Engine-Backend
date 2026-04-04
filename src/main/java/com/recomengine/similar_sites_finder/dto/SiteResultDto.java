package com.recomengine.similar_sites_finder.dto;

import com.recomengine.similar_sites_finder.model.SimilarSite.ResultSource;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteResultDto {

    private String url;
    private String title;
    private String category;
    private Double score;
    private ResultSource source;
}
