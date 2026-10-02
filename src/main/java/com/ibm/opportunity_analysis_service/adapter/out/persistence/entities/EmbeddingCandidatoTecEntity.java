package com.ibm.opportunity_analysis_service.adapter.out.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
@Getter
@Setter
@Entity
@Table(name = "embedding_producto_ibm")
public class EmbeddingCandidatoTecEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "producto_ibm_id", nullable = false)
        private Long titulo;

        @Column(nullable = false)
        private String candidatoTecnologcoOcid;

        @Column(name = "texto_embedding", columnDefinition = "TEXT", nullable = false)
        private String textoEmbedding;

        @Column(name="modelo", nullable = false)
        private String modelo;

        @JdbcTypeCode(SqlTypes.VECTOR)
        @org.hibernate.annotations.Array(length = 768)
        @Column(columnDefinition = "vector(768)", nullable = false)
        private float[] embedding;

        @Column(name = "fecha_generacion", nullable = false)
        private OffsetDateTime fechaGeneracion;
}
