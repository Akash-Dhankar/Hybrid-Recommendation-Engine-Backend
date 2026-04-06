package com.recomengine.similar_sites_finder.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "websites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String url;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String keywords;

    @Field(type = FieldType.Long)
    private Long monthlyVisits;

    @Field(type = FieldType.Double)
    private Double domainRank;
}