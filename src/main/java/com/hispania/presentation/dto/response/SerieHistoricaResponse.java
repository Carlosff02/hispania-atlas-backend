package com.hispania.presentation.dto.response;

/**
 * Un punto de la serie economica de un pais, tal y como lo consume el frontend.
 *
 * <p>Solo {@code year} y {@code gdp} van como primitivos: el modelo
 * {@code SeriePunto} de Angular los declara como {@code number} y no acepta
 * {@code null}. El resto son metricas opcionales y viajan como {@code Double}; cuando
 * son nulas Jackson las omite del JSON y el frontend las sustituye con {@code ?? 0}.
 *
 * <p>Es un {@code record}: inmutable, sin setters y con deserializacion por nombre de
 * componente (el compilador genera el canonico). No tiene metodos {@code from} que
 * reciban entidades: el mapeo entidad &lt;-&gt; DTO vive en
 * {@code com.hispania.services.mapper}, de modo que esta capa no depende de
 * {@code persistence}.
 *
 * @param year      anio del dato, obligatorio
 * @param gdp       PIB total, obligatorio
 * @param gdpPc     PIB per capita
 * @param pop       poblacion
 * @param growth    crecimiento porcentual
 * @param inflation inflacion porcentual
 * @param exports   exportaciones
 * @param imports   importaciones
 * @param hdi       indice de desarrollo humano (0..1)
 * @param debt      deuda publica como porcentaje del PIB
 * @param trade     saldo comercial
 */
public record SerieHistoricaResponse(
        int year,
        double gdp,
        Double gdpPc,
        Double pop,
        Double growth,
        Double inflation,
        Double exports,
        Double imports,
        Double hdi,
        Double debt,
        Double trade
) {
}
