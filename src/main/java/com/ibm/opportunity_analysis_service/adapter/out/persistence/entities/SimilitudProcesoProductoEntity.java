package com.ibm.opportunity_analysis_service.adapter.out.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "similitud_proceso_producto",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_similitud_ocid_producto",
        columnNames = {"ocid", "producto_ibm_id"}
    )
)
@Getter
@Setter
public class SimilitudProcesoProductoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ocid;

    @Column(name = "producto_ibm_id", nullable = false)
    private Long productoIbmId;

    @Column(name = "nombre_producto", nullable = false)
    private String nombreProducto;

    @Column(name = "score_vectorial", nullable = false)
    private Double scoreVectorial;

    @Column(name = "score_compuesto", nullable = false)
    private Double scoreCompuesto;

    /**
     * Hash SHA-256 de los campos que intervienen en el cálculo:
     * titulo + descripcion + tipoInterno + monto + metodo.
     * Permite detectar si el proceso cambió sin recalcular el embedding.
     */
    @Column(name = "hash_contenido")
    private String hashContenido;

    @Column(name = "fecha_calculo", nullable = false)
    private OffsetDateTime fechaCalculo;
}
