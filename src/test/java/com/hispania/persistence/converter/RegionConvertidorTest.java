package com.hispania.persistence.converter;

import com.hispania.persistence.entity.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El valor de {@link Region} es texto acentuado y no el nombre de la constante porque es
 * lo que escribieron las migraciones y lo que espera el frontend. Estos tests fijan las
 * dos directions para que el desajuste no vuelva a colarse: sin ellos, una lectura
 * fallida solo se manifiesta como un 500 en tiempo de ejecucion.
 */
@DisplayName("RegionConvertidor")
class RegionConvertidorTest {

    private final RegionConvertidor convertidor = new RegionConvertidor();

    @Nested
    @DisplayName("hacia la base de datos")
    class HaciaLaBaseDeDatos {

        @ParameterizedTest(name = "{0} se persiste como \"{1}\"")
        @CsvSource({
                "NORTEAMERICA,  Norteamérica",
                "CENTROAMERICA, Centroamérica",
                "CARIBE,        Caribe",
                "ANDINA,        Andina",
                "CONO_SUR,      Cono Sur"
        })
        void persisteElTextoAcentuado(Region region, String esperado) {
            assertThat(convertidor.convertToDatabaseColumn(region)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("un null se persiste como null")
        void persisteNull() {
            assertThat(convertidor.convertToDatabaseColumn(null)).isNull();
        }
    }

    @Nested
    @DisplayName("desde la base de datos")
    class DesdeLaBaseDeDatos {

        @Test
        @DisplayName("\"Norteamérica\" (como lo sembró V3) se lee como NORTEAMERICA")
        void leeLoQueEscribioLaMigracion() {
            Region region = convertidor.convertToEntityAttribute("Norteamérica");

            assertThat(region).isEqualTo(Region.NORTEAMERICA);
        }

        @Test
        @DisplayName("\"Cono Sur\" se lee aunque lleve espacio, algo imposible en el nombre de la constante")
        void leeUnValorConEspacio() {
            assertThat(convertidor.convertToEntityAttribute("Cono Sur")).isEqualTo(Region.CONO_SUR);
        }

        @ParameterizedTest(name = "ida y vuelta sin perdida: {0}")
        @EnumSource(Region.class)
        void hayIdaYVueltaSinPerdida(Region region) {
            String persistido = convertidor.convertToDatabaseColumn(region);

            assertThat(convertidor.convertToEntityAttribute(persistido)).isEqualTo(region);
        }

        @Test
        @DisplayName("un null se lee como null")
        void leeNull() {
            assertThat(convertidor.convertToEntityAttribute(null)).isNull();
        }

        @ParameterizedTest(name = "rechaza el texto desconocido \"{0}\" en vez de adivinar")
        @ValueSource(strings = {"NORTEAMERICA", "Norteamerica", "ConoSur", "", "ANDINA "})
        void rechazaValoresDesconocidos(String valor) {
            assertThatThrownBy(() -> convertidor.convertToEntityAttribute(valor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(valor.isEmpty() ? "" : valor);
        }
    }
}
