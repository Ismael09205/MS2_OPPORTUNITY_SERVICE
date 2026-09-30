package com.ibm.opportunity_analysis_service.adapter.in.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductoIbmDto {

    private String product_name;

    private String description;

    private List<String> category;

    private List<String> capabilities;

    private List<String> use_cases;

    private List<String> technologies;

    private List<String> integrations;

    private List<String> tags;

    private List<String> keywords;

    private String source_url;
}