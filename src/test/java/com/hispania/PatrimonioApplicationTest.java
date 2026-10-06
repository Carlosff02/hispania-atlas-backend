package com.hispania;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del fallo rapido de credenciales.
 *
 * <p>Existe por un bug concreto: el nombre canonico se usaba solo para el mensaje
 * de error y la busqueda iba solo por las rutas alternativas. Con todo bien
 * configurado en IntelliJ, {@code DB_PASSWORD} no se encontra nunca y la aplicacion
 * se niega a arrancar. Un fallo asi no lo pilla el arranque normal porque el
 * {@code main} solo se ejecuta de verdad, y solo se comprueba de verdad cuando
 * alguien lo ejecuta.
 */
@DisplayName("Fallo rapido de credenciales")
class PatrimonioApplicationTest {

    private static final String CANONICA = "DB_PASSWORD";
    private static final List<String> CANDIDATAS = List.of(CANONICA, "SPRING_DATASOURCE_PASSWORD");

    @Nested
    @DisplayName("Acepta la variable canonica")
    class AceptaLaVariableCanonica {

        @Test
        @DisplayName("la busca, y no solo la menciona en el mensaje de error")
        void encuentraLaNombreCanonico() {
            var soloLaCanonica = resolver(Map.of(CANONICA, "clave-de-postgres"));

            assertThatCode(() -> PatrimonioApplication.exigirCredencial(
                    CANONICA, CANDIDATAS, soloLaCanonica)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("también acepta una ruta alternativa")
        void encuentraUnaRutaAlternativa() {
            var soloLaAlternativa = resolver(Map.of("SPRING_DATASOURCE_PASSWORD", "clave"));

            assertThatCode(() -> PatrimonioApplication.exigirCredencial(
                    CANONICA, CANDIDATAS, soloLaAlternativa)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Rechaza valores que no sirven")
    class RechazaValoresQueNoSirven {

        @Test
        @DisplayName("si la variable no esta en ninguna ruta")
        void fallaSiNoEstaEnNingunaRuta() {
            var ninguna = resolver(Map.of());

            assertThatThrownBy(() -> PatrimonioApplication.exigirCredencial(
                    CANONICA, CANDIDATAS, ninguna))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(CANONICA);
        }

        @Test
        @DisplayName("si la variable esta vacia")
        void fallaSiEstaVacia() {
            var vacia = resolver(Map.of(CANONICA, ""));

            assertThatThrownBy(() -> PatrimonioApplication.exigirCredencial(
                    CANONICA, CANDIDATAS, vacia)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("si la variable solo tiene espacios")
        void fallaSiSoloTieneEspacios() {
            var espacios = resolver(Map.of(CANONICA, "   "));

            assertThatThrownBy(() -> PatrimonioApplication.exigirCredencial(
                    CANONICA, CANDIDATAS, espacios)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("si alguien copio el placeholder sin expandir")
        void fallaSiElPlaceholderNoSeExpadio() {
            // Alguien exporta el texto literal "${DB_PASSWORD}" creyendo que basta.
            var sinExpandir = resolver(Map.of(CANONICA, "${DB_PASSWORD}"));

            assertThatThrownBy(() -> PatrimonioApplication.exigirCredencial(
                    CANONICA, CANDIDATAS, sinExpandir)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("El mensaje de error dice como se arregla en IntelliJ")
    void elMensajeMencionaComoConfigurarEnIntellij() {
        var ninguna = resolver(Map.of());

        assertThatThrownBy(() -> PatrimonioApplication.exigirCredencial(
                CANONICA, CANDIDATAS, ninguna))
                .hasMessageContaining("IntelliJ")
                .hasMessageContaining(CANONICA);
    }

    private static java.util.function.Function<String, String> resolver(Map<String, String> entorno) {
        return entorno::get;
    }
}
