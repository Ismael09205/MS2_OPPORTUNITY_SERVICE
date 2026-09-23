package com.ibm.opportunity_analysis_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpportunityAnalysisServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpportunityAnalysisServiceApplication.class, args);
	}
}