package com.ibm.opportunity_analysis_service.adapter.out.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "candidato_tecnologico", uniqueConstraints = @UniqueConstraint(name = "uk_candidato_tecnologico_ocid", columnNames = "ocid"))
public class CandidatoTecnologicoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ocid;

    @Column(columnDefinition = "TEXT")
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_deteccion", nullable = false)
    private OffsetDateTime fechaDeteccion;

    @Column(nullable = false)
    private String estado;
}