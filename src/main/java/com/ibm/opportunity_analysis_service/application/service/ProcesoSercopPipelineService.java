package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.adapter.in.dto.ErrorProcesoDto;
import com.ibm.opportunity_analysis_service.adapter.in.dto.EstadoPipelineDto;
import com.ibm.opportunity_analysis_service.adapter.in.dto.ResultadoSincronizacionDto;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingCandidatoTecnologicoJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProcesoSercopJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProductoIbmJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.RecomendacionJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;
import com.ibm.opportunity_analysis_service.domain.entity.SimilitudProcesoProducto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ProcesoSercopPipelineService {

    private static final double MONTO_NORMALIZACION = 10_000_000.0;

    private final CatalogoIbmBootstrapService catalogoIbmBootstrapService;
    private final ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService;
    private final EmbeddingCandidatoPort embeddingCandidatoPort;
    private final EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    private final SimilitudPersistencePort similitudPersistencePort;
    private final ValidadorAlcanceIbmService validadorAlcanceIbmService;
    private final RecomendarOportunidadesService recomendarOportunidadesService;
    private final ConfiguracionGranite configuracionGranite;

    // Repositorios para métricas y estado
    private final ProcesoSercopJpaRepository procesoSercopJpaRepository;
    private final EmbeddingCandidatoTecnologicoJpaRepository embeddingCandidatoTecnologicoJpaRepository;
    private final ProductoIbmJpaRepository productoIbmJpaRepository;
    private final RecomendacionJpaRepository recomendacionJpaRepository;

    private final AtomicBoolean enEjecucion = new AtomicBoolean(false);
    private final AtomicReference<ResultadoSincronizacionDto> ultimaSincronizacion = new AtomicReference<>(null);

    public ProcesoSercopPipelineService(
            CatalogoIbmBootstrapService catalogoIbmBootstrapService,
            ObtenerProcesosSercopService obtenerProcesosSercopService,
            GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService,
            EmbeddingCandidatoPort embeddingCandidatoPort,
            EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort,
            SimilitudPersistencePort similitudPersistencePort,
            ValidadorAlcanceIbmService validadorAlcanceIbmService,
            RecomendarOportunidadesService recomendarOportunidadesService,
            ConfiguracionGranite configuracionGranite,
            ProcesoSercopJpaRepository procesoSercopJpaRepository,
            EmbeddingCandidatoTecnologicoJpaRepository embeddingCandidatoTecnologicoJpaRepository,
            ProductoIbmJpaRepository productoIbmJpaRepository,
            RecomendacionJpaRepository recomendacionJpaRepository) {
        this.catalogoIbmBootstrapService = catalogoIbmBootstrapService;
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.generarEmbeddingCandidatoService = generarEmbeddingCandidatoService;
        this.embeddingCandidatoPort = embeddingCandidatoPort;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.similitudPersistencePort = similitudPersistencePort;
        this.validadorAlcanceIbmService = validadorAlcanceIbmService;
        this.recomendarOportunidadesService = recomendarOportunidadesService;
        this.configuracionGranite = configuracionGranite;
        this.procesoSercopJpaRepository = procesoSercopJpaRepository;
        this.embeddingCandidatoTecnologicoJpaRepository = embeddingCandidatoTecnologicoJpaRepository;
        this.productoIbmJpaRepository = productoIbmJpaRepository;
        this.recomendacionJpaRepository = recomendacionJpaRepository;
    }

    /**
     * Sincroniza y procesa de forma integral y automática todos los procesos SERCOP en la BD.
     * Garantiza:
     * - No concurrencia (1 solo hilo ejecuta el pipeline a la vez).
     * - Tolerancia a fallos: un fallo en 1 proceso no interrumpe el resto del lote.
     * - Idempotencia: salta procesos cuyo contenido no haya cambiado (verificación por hash SHA-256).
     */
    public ResultadoSincronizacionDto sincronizarProcesos() {
        if (!enEjecucion.compareAndSet(false, true)) {
            log.info("Intento de ejecución de pipeline descartado: ya existe una sincronización en curso.");
            return ResultadoSincronizacionDto.enEjecucion();
        }

        long inicioMs = System.currentTimeMillis();
        int procesadosExitosos = 0;
        int alDiaSinCambios = 0;
        int fallidos = 0;
        List<ErrorProcesoDto> errores = new ArrayList<>();
        List<ProcesoSercop> procesos = List.of();

        try {
            log.info("Iniciando pipeline automático de sincronización de procesos SERCOP...");

            // 1. Asegurar catálogo IBM
            catalogoIbmBootstrapService.asegurarCatalogoIbmListo();

            // 2. Obtener procesos SERCOP
            procesos = obtenerProcesosSercopService.obtenerTodos();

            if (procesos.isEmpty()) {
                log.info("No hay procesos SERCOP registrados en la base de datos.");
                ResultadoSincronizacionDto resultado = ResultadoSincronizacionDto.builder()
                        .mensaje("No hay procesos SERCOP registrados en la base de datos.")
                        .enEjecucion(false)
                        .totalProcesosSercop(0)
                        .procesadosExitosos(0)
                        .alDiaSinCambios(0)
                        .fallidos(0)
                        .duracionSegundos(0)
                        .fechaFinalizacion(OffsetDateTime.now())
                        .build();
                ultimaSincronizacion.set(resultado);
                return resultado;
            }

            log.info("Se encontraron {} procesos SERCOP en BD. Verificando estado y calculando similitudes...", procesos.size());

            String modelo = configuracionGranite.getModelo();

            for (ProcesoSercop proceso : procesos) {
                try {
                    // Validar alcance y elegibilidad
                    var validacion = validadorAlcanceIbmService.validar(proceso);
                    if (!validacion.isEsElegible()) {
                        log.debug("Proceso {} no elegible para IBM: {}", proceso.getOcid(), validacion.getMotivoDescarte());
                        alDiaSinCambios++;
                        continue;
                    }

                    String hashActual = calcularHash(proceso);
                    Optional<String> hashGuardado = similitudPersistencePort.obtenerHashPorOcid(proceso.getOcid());

                    // Verificar si ya tiene similitudes calculadas con el mismo hash
                    if (hashGuardado.isPresent() && hashGuardado.get().equals(hashActual)) {
                        alDiaSinCambios++;
                        continue;
                    }

                    // Generar / actualizar embedding del candidato
                    EmbeddingCandidatoTecnologico embedding =
                            generarEmbeddingCandidatoService.generarOActualizarEmbedding(proceso);

                    if (embedding == null || embedding.getEmbedding() == null) {
                        throw new IllegalStateException("No se pudo obtener embedding para el proceso " + proceso.getOcid());
                    }

                    // Buscar top 5 productos IBM similares
                    List<ResultadoSimilitudIbm> resultados =
                            embeddingProductoIbmPersistencePort.buscarSimilares(
                                    embedding.getEmbedding(),
                                    5
                            );

                    double bonusMonto = calcularBonusMonto(proceso);
                    double bonusMetodo = calcularBonusMetodo(proceso);
                    double bonusAlcance = validacion.getBonusAlcance();

                    // Persistir similitudes
                    for (ResultadoSimilitudIbm resultado : resultados) {
                        double scoreCompuesto = calcularScoreCompuesto(
                                resultado.getSimilitud(), bonusMonto, bonusMetodo, bonusAlcance);

                        SimilitudProcesoProducto similitud = new SimilitudProcesoProducto();
                        similitud.setOcid(proceso.getOcid());
                        similitud.setProductoIbmId(resultado.getProductoIbmId());
                        similitud.setNombreProducto(resultado.getNombreProducto());
                        similitud.setScoreVectorial(resultado.getSimilitud());
                        similitud.setScoreCompuesto(scoreCompuesto);
                        similitud.setHashContenido(hashActual);

                        similitudPersistencePort.guardarOActualizar(similitud);
                    }

                    procesadosExitosos++;

                } catch (Exception e) {
                    fallidos++;
                    log.error("Fallo al procesar automáticamente proceso SERCOP con OCID {}: {}",
                            proceso.getOcid(), e.getMessage());
                    errores.add(new ErrorProcesoDto(proceso.getOcid(), e.getMessage()));
                }
            }

            double duracionSegundos = (System.currentTimeMillis() - inicioMs) / 1000.0;
            log.info("Pipeline completado en {}s. Total: {}, Procesados: {}, Al día: {}, Fallidos: {}.",
                    duracionSegundos, procesos.size(), procesadosExitosos, alDiaSinCambios, fallidos);

            // Si se procesaron nuevos candidatos exitosamente, lanzar evaluación asíncrona priorizada de Granite para el Top 5
            if (procesadosExitosos > 0) {
                log.info("Disparando evaluación asíncrona priorizada de Granite para los nuevos candidatos...");
                recomendarOportunidadesService.precalcularRecomendacionesAsync(5);
            }

            ResultadoSincronizacionDto resultado = ResultadoSincronizacionDto.builder()
                    .mensaje(fallidos == 0
                            ? "Sincronización completada exitosamente."
                            : "Sincronización completada con algunos fallos aislados.")
                    .enEjecucion(false)
                    .totalProcesosSercop(procesos.size())
                    .procesadosExitosos(procesadosExitosos)
                    .alDiaSinCambios(alDiaSinCambios)
                    .fallidos(fallidos)
                    .duracionSegundos(duracionSegundos)
                    .fechaFinalizacion(OffsetDateTime.now())
                    .errores(errores)
                    .build();

            ultimaSincronizacion.set(resultado);
            return resultado;

        } finally {
            enEjecucion.set(false);
        }
    }

    /**
     * Devuelve el estado actual de métricas del pipeline y salud del sistema.
     */
    public EstadoPipelineDto obtenerEstadoPipeline() {
        return EstadoPipelineDto.builder()
                .enEjecucion(enEjecucion.get())
                .totalProcesosSercop(procesoSercopJpaRepository.count())
                .totalEmbeddingsSercop(embeddingCandidatoTecnologicoJpaRepository.count())
                .totalProcesosConSimilitud(similitudPersistencePort.contarProcesosConSimilitud())
                .totalRecomendaciones(recomendacionJpaRepository.count())
                .catalogoIbmListo(catalogoIbmBootstrapService.estaListo())
                .totalProductosIbm(productoIbmJpaRepository.count())
                .ultimaSincronizacion(ultimaSincronizacion.get())
                .build();
    }

    private double calcularBonusMonto(ProcesoSercop proceso) {
        BigDecimal monto = proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        double ratio = monto.doubleValue() / MONTO_NORMALIZACION;
        return Math.min(ratio, 1.0) * 0.15;
    }

    private double calcularBonusMetodo(ProcesoSercop proceso) {
        if (proceso.getMetodo() == null) return 0.0;
        return switch (proceso.getMetodo().toLowerCase()) {
            case "open" -> 0.10;
            case "direct" -> 0.03;
            default -> 0.05;
        };
    }

    private double calcularScoreCompuesto(double scoreVectorial, double bonusMonto, double bonusMetodo, double bonusAlcance) {
        return (scoreVectorial * 0.70) + bonusMonto + bonusMetodo + bonusAlcance;
    }

    private String calcularHash(ProcesoSercop proceso) {
        BigDecimal monto = proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto();
        String contenido = String.join("|",
                nvl(proceso.getTitulo()),
                nvl(proceso.getDescripcion()),
                nvl(proceso.getTipoInterno()),
                monto != null ? monto.toPlainString() : "",
                nvl(proceso.getMetodo())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(contenido.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
