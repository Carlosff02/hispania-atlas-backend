package com.hispania.services.mapper;

import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.Lugar;
import com.hispania.persistence.entity.Pais;
import com.hispania.persistence.entity.PaisSerieHistorica;
import com.hispania.persistence.entity.Region;
import com.hispania.presentation.dto.response.LugarResponse;
import com.hispania.presentation.dto.response.PaisResponse;
import com.hispania.presentation.dto.response.SerieHistoricaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del mapeo entidad -&gt; DTO.
 *
 * <p>No necesita Spring ni base de datos: el mapper es un {@code @Component} sin
 * estado, asi que se instancia directamente. Es la prueba mas barata del proyecto y
 * cubre justo lo que el frontend ve, que es el contrato que no se puede romper.
 */
class ResponseMapperTest {

    private final ResponseMapper mapper = new ResponseMapper();

    private static Pais pais() {
        return new Pais("PE", "Perú", "Lima", -9.2, -75.0, Region.ANDINA, "Antiguo epicentro virreinal");
    }

    private static PaisSerieHistorica serie(int year, double gdp) {
        PaisSerieHistorica s = new PaisSerieHistorica(year, gdp, "PE");
        s.setGdpPc(10960.0);
        s.setPop(34.4);
        s.setHdi(0.796);
        return s;
    }

    @Test
    @DisplayName("el enum viaja por su nombre, no por su ordinal")
    void convierteCategoriaPorNombre() {
        // Es lo que espera el filtro de la vista Cultura del frontend: si saliera
        // "0" en vez de "ARTE", el filtro no casaria con nada.
        Lugar lugar = new Lugar("mali", "Museo de Arte de Lima", pais(), -12.06, -77.037,
                CategoriaLugar.ARTE, "palette", "1961", "Palacio de la Exposición", null);

        LugarResponse dto = mapper.toLugarResponse(lugar);

        assertThat(dto.category()).isEqualTo("ARTE");
    }

    @Test
    @DisplayName("sin categoria, el DTO la deja en null y no en un texto vacio")
    void categoriaNulaQuedaNull() {
        Lugar lugar = new Lugar("x", "X", pais(), 0, 0, null, null, null, null, null);

        assertThat(mapper.toLugarResponse(lugar).category()).isNull();
    }

    @Test
    @DisplayName("el constructor de Lugar escribe country a partir del pais")
    void countrySeDerivaDelPais() {
        // Es la garantia que hace funcionar el CHECK `country = pais_code` de la
        // migracion V5 sin depender de que el servicio recuerde hacerlo.
        Pais pe = pais();
        Lugar lugar = new Lugar("chan_chan", "Chan Chan", pe, -8.10, -79.07,
                CategoriaLugar.PATRIMONIO, null, "Siglo IX", null, null);

        assertThat(lugar.getCountry()).isEqualTo("PE");
        assertThat(lugar.getPais().getCode()).isEqualTo("PE");
    }

    @Test
    @DisplayName("cambiar de pais con setPais actualiza las dos columnas a la vez")
    void setPaisMantieneLasColumnasSincronizadas() {
        Lugar lugar = new Lugar("x", "X", pais(), 0, 0, null, null, null, null, null);

        lugar.setPais(new Pais("MX", "México", "Ciudad de México", 23.6, -102.5, Region.NORTEAMERICA, null));

        assertThat(lugar.getCountry()).isEqualTo("MX");
    }

    @Test
    @DisplayName("las metricas nulas se propagan tal cual, sin convertir a 0")
    void conservaMetricasNulas() {
        // El frontend las sustituye con `?? 0`; si el backend las convirtiese a 0,
        // un dato ausente seria indistinguible de un dato real de cero.
        SerieHistoricaResponse dto = mapper.toSerieResponse(serie(2026, 380));

        assertThat(dto.gdp()).isEqualTo(380.0);
        assertThat(dto.gdpPc()).isEqualTo(10960.0);
        assertThat(dto.hdi()).isEqualTo(0.796);
        assertThat(dto.growth()).isNull();
        assertThat(dto.debt()).isNull();
    }

    @Test
    @DisplayName("el pais se proyecta con las colecciones ya resueltas")
    void proyectaPaisConSusColecciones() {
        Lugar lugar = new Lugar("mali", "Museo de Arte de Lima", pais(), -12.06, -77.037,
                CategoriaLugar.ARTE, null, null, null, null);

        PaisResponse dto = mapper.toPaisResponse(
                pais(),
                List.of(lugar),
                List.of(serie(2023, 272.755), serie(2026, 380)));

        assertThat(dto.code()).isEqualTo("PE");
        assertThat(dto.region()).isEqualTo("Andina");
        assertThat(dto.lugares()).hasSize(1);
        assertThat(dto.seriesHistoricas()).hasSize(2);
        // El orden lo impone el repositorio; el mapper lo respeta sin reordenar.
        assertThat(dto.seriesHistoricas().getLast().year()).isEqualTo(2026);
    }

    @Test
    @DisplayName("un pais sin lugares ni series produce listas vacias, no nulls")
    void coleccionesVaciasNoSonNull() {
        PaisResponse dto = mapper.toPaisResponse(pais(), List.of(), List.of());

        // Jackson omite los null, y el frontend hace `seriesHistoricas[length - 1]`
        // sin comprobar undefined: una lista vacia evita que reviente.
        assertThat(dto.lugares()).isNotNull().isEmpty();
        assertThat(dto.seriesHistoricas()).isNotNull().isEmpty();
    }
}
