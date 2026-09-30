package com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RespuestaEmbedding {

    private List<List<Float>> embeddings;
}