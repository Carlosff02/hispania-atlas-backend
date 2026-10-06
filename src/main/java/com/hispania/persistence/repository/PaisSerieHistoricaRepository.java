package com.hispania.persistence.repository;

import com.hispania.persistence.entity.PaisSerieHistorica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla {@code paises_series_historicas}.
 */
public interface PaisSerieHistoricaRepository extends JpaRepository<PaisSerieHistorica, Long> {

    /**
     * Serie de un pais ordenada por anio ascendente.
     *
     * <p>El orden importa: el frontend toma el ULTIMO elemento del array como el dato
     * vigente ({@code series[series.length - 1]}) para las tarjetas de la vista
     * Economia, asi que un orden ascendente garantiza que ese elemento sea el anio
     * mas reciente. La restriccion {@code UNIQUE (pais_code, year)} de la migracion
     * V5 garantiza que no haya dos filas del mismo anio.
     */
    List<PaisSerieHistorica> findByPaisCodeOrderByYearAsc(String paisCode);

    /**
     * Todas las series, agrupadas por pais y ordenadas por anio.
     *
     * <p>Con este orden el servicio puede agrupar en una sola pasada
     * ({@codeCollectors.groupingBy}) sin resorting, que es lo que permite servir
     * {@code GET /api/countries} con una unica consulta para todos los paises.
     */
    List<PaisSerieHistorica> findAllByOrderByPaisCodeAscYearAsc();
}
