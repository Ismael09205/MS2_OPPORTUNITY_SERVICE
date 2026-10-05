package com.ibm.opportunity_analysis_service.adapter.out.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "embedding_candidato_tecnologico")
public class EmbeddingCandidatoTecEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String ocid;

        @Column(nullable = false)
        private String modelo;

        @Column(name = "texto_embedding", columnDefinition = "TEXT", nullable = false)
        private String textoEmbedding;

        @JdbcTypeCode(SqlTypes.VECTOR)
        @Array(length = 768)
        @Column(columnDefinition = "vector(768)", nullable = false)
        private float[] embedding;

        @Column(name = "fecha_generacion", nullable = false)
        private OffsetDateTime fechaGeneracion;
}