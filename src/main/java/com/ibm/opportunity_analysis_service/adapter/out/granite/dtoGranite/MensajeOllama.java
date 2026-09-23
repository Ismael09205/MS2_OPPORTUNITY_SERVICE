package com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MensajeOllama {

    private String role;
    private String content;
}