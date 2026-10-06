package com.hispania.presentation.dto.response;

import java.util.List;

/**
 * Pais con su serie historica y sus lugares.
 *
 * <p>Es la respuesta de {@code GET /api/countries} y de
 * {@code GET /api/countries/{code}}. El frontend no hace una segunda peticion para
 * los lugares: los espera anidados aqui, porque al pintar un pais en el mapa necesita
 * su ficha y sus marcadores a la vez.
 *
 * <p>{@code seriesHistoricas} viene ordenada por anio ascendente; su ultimo elemento
 * es el dato vigente que la vista Economia muestra en las tarjetas.
 *
 * @param code              codigo ISO de dos letras
 * @param name              nombre del pais
 * @param capital           capital
 * @param lat               latitud
 * @param lng               longitud
 * @param region            region geografica
 * @param descText          descripcion
 * @param seriesHistoricas  serie economica, ordenada por anio ascendente
 * @param lugares           lugares asociados
 */
public record PaisResponse(
        String code,
        String name,
        String capital,
        double lat,
        double lng,
        String region,
        String descText,
        List<SerieHistoricaResponse> seriesHistoricas,
        List<LugarResponse> lugares
) {
}
