package com.ibm.opportunity_analysis_service.application.mapper;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProcesoContratacionEntity;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.stereotype.Component;

@Component
public class ProcesoSercopMapper {

    public ProcesoSercop toDomain(ProcesoContratacionEntity entidad) {

        ProcesoSercop proceso = new ProcesoSercop();

        proceso.setId(entidad.getId());
        proceso.setAnio(entidad.getAnio());
        proceso.setComprador(entidad.getComprador());
        proceso.setDescripcion(entidad.getDescripcion());
        proceso.setFecha(entidad.getFecha());
        proceso.setIdentificadorSercop(entidad.getIdentificadorSercop());
        proceso.setLocalidad(entidad.getLocalidad());
        proceso.setMes(entidad.getMes());
        proceso.setMetodo(entidad.getMetodo());
        proceso.setMonto(entidad.getMonto());
        proceso.setOcid(entidad.getOcid());
        proceso.setPresupuesto(entidad.getPresupuesto());
        proceso.setProveedores(entidad.getProveedores());
        proceso.setRegion(entidad.getRegion());
        proceso.setTipoInterno(entidad.getTipoInterno());
        proceso.setTitulo(entidad.getTitulo());

        return proceso;
    }
}