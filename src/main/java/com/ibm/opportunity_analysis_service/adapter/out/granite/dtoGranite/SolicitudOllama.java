package com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SolicitudOllama {
    private String model;
    private List<MensajeOllama> messages;
    private boolean stream;
    private Object format;
    private Map<String, Object> options;
}