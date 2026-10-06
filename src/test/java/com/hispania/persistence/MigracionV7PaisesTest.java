package com.hispania.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba la migracion V7 por separado del arranque de la aplicacion.
 *
 * <p>Existe por un agujero concreto: {@link MigracionesSobrePostgresTest} levanta la
 * cadena entera sobre una base NUEVA, y en una base nueva V3 siembra Mexico y Peru
 * con el texto ya correcto. El fallo que se encontro en la base de desarrollo
 * ({@code CONO_SUR} en la columna, un 500 en {@code GET /api/countries}) no aparece
 * ahi, porque hace falta una base donde alguien escribio con el enum viejo.
 *
 * <p>Por eso estas pruebas controlan el punto de parada de Flyway: primero dejan la
 * base en V6, luego la dejan en el estado que estaba la de desarrollo, y solo
 * entonces aplican V7.
 *
 * <p>Sin Spring: no hay contexto que arranque, ni entity manager, ni nada que
 * pueda enmascarar lo que hace o deja de hacer el SQL.
 */
@Testcontainers
@DisplayName("V7: normalizar regiones heredadas y sembrar los paises que faltaban")
class MigracionV7PaisesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final List<String> REGIONES_CANONICAS =
            List.of("Norteamérica", "Centroamérica", "Caribe", "Andina", "Cono Sur");

    /**
     * Un esquema por prueba. Compartir uno obligaria a que cada test empezara en un
     * estado que depende del orden, que es justo lo que hace que estas pruebas no
     * sirvan para nada.
     */
    private String esquema;

    private SingleConnectionDataSource fuente;

    @BeforeEach
    void creaUnEsquemaPropio(TestInfo info) throws SQLException {
        // El nombre del metodo va en minusculas y sin acentos para que sea un
        // identificador valido de Postgres (top 63 caracteres).
        this.esquema = "v7_" + info.getTestMethod().orElseThrow().getName().toLowerCase();
        new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");

        // Una sola conexion con `search_path` ya puesto, en vez de coser el
        // esquema en la URL: `getJdbcUrl()` de Testcontainers ya trae sus propios
        // parametros, y añadir un `?currentSchema=` detras produce una URL que no
        // significa nada. Flyway y JdbcTemplate comparten la misma conexion, asi
        // que los dos ven el mismo esquema.
        Connection conexion = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        // `SET search_path` a un esquema que todavia no existe no da error: se
        // resuelve en la primera consulta, y para entonces Flyway ya lo creo.
        conexion.createStatement().execute("SET search_path TO " + esquema);

        this.fuente = new SingleConnectionDataSource(conexion, true);
    }

    /**
     * `SingleConnectionDataSource` va con {@code suppressClose=true} para que ni
     * Flyway ni JdbcTemplate cierren la conexion que comparten, asi que cerrarla
     * es cosa de esta prueba.
     */
    @AfterEach
    void cierraLaConexion() throws SQLException {
        if (fuente != null) {
            fuente.getConnection().close();
        }
    }

    // --- lo que se comprueba ------------------------------------------------

    @Test
    @DisplayName("normaliza las regiones que escribio el codigo viejo y siembra los que faltaban")
    void normalizaYSiembraSobreUnaBaseConDatosHeredados() {
        // 1. Base hasta V6: V3 siembra MX y PE con el texto correcto.
        flyway("6").migrate();

        // 2. Se reproduce el estado real de la base de desarrollo, que es el que
        //    reventaba: el enum viejo escribia el NOMBRE de la constante, y AR y CL
        //    se habian insertado a mano.
        jdbc().update("UPDATE paises SET region = 'NORTEAMERICA' WHERE code = 'MX'");
        jdbc().update("UPDATE paises SET region = 'ANDINA' WHERE code = 'PE'");
        jdbc().update("""
                INSERT INTO paises (code, name, capital, lat, lng, region, desc_text)
                VALUES ('AR', 'Argentina', 'Buenos Aires', -35.5, -64.5, 'CONO_SUR', 'Cono sur')
                """);
        jdbc().update("""
                INSERT INTO paises (code, name, capital, lat, lng, region, desc_text)
                VALUES ('CL', 'Chile', 'Santiago', -35.6, -71.5, 'CONO_SUR', 'Cono sur')
                """);

        // CU y GT no estaban en la base de desarrollo, pero V7 tiene un UPDATE
        // para cada uno de los cinco nombres heredados y hay que ejercitar los
        // cinco: si alguien anade una region al enum y olvida su UPDATE, el
        // guardia lo detecta, pero este test lo detecta antes.
        jdbc().update("""
                INSERT INTO paises (code, name, capital, lat, lng, region, desc_text)
                VALUES ('CU', 'Cuba', 'La Habana', 21.5, -77.8, 'CARIBE', 'Caribe')
                """);
        jdbc().update("""
                INSERT INTO paises (code, name, capital, lat, lng, region, desc_text)
                VALUES ('GT', 'Guatemala', 'Ciudad de Guatemala', 15.7, -90.2, 'CENTROAMERICA', 'Centroamerica')
                """);

        assertThat(estadosDeRegion())
                .containsExactlyInAnyOrder("NORTEAMERICA", "ANDINA", "CONO_SUR", "CARIBE", "CENTROAMERICA");

        // 3. Y ahora V7.
        flyway("latest").migrate();

        assertThat(estadosDeRegion()).containsOnlyElementsOf(REGIONES_CANONICAS);
        assertThat(codigos()).hasSize(19);
        assertThat(regionDe("MX")).isEqualTo("Norteamérica");
        assertThat(regionDe("PE")).isEqualTo("Andina");
        assertThat(regionDe("CU")).isEqualTo("Caribe");
        assertThat(regionDe("GT")).isEqualTo("Centroamérica");
        // AR y CL ya estaban, asi que V7 no los duplico: solo les normalizo el
        // valor, que es lo unico que estaba mal.
        assertThat(regionDe("AR")).isEqualTo("Cono Sur");
        assertThat(regionDe("CL")).isEqualTo("Cono Sur");
        assertThat(codigos()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("los 15 paises que faltaban estan, con la region que espera el frontend")
    void siembraLosQueFaltaban() {
        flyway("latest").migrate();

        assertThat(codigos()).containsExactlyInAnyOrder(
                "MX", "GT", "SV", "HN", "NI", "CR", "PA", "CU", "DO", "PR",
                "VE", "CO", "EC", "PE", "BO", "PY", "UY", "AR", "CL");

        // La region de cada uno tiene que ser una de las cinco que valida el
        // `Region` del frontend. Si V7 sembrara 'Cono_Sur' o 'ANDINA', el mapa
        // dibujaria el pais en el sitio equivocado sin que nada fallara.
        assertThat(regionesPorCodigo()).allSatisfy((code, region) ->
                assertThat(REGIONES_CANONICAS).as("region de %s", code).contains(region));

        // Que los paises nuevos no traigan series es esperado (esta migracion no
        // inventa datos de 2026), pero ningun pais puede quedarse sin fila en
        // `paises`: un pais que no existe en la tabla es un fallo de la migracion,
        // no una consecuencia de que falten los graficos.
        assertThat(codigos()).allSatisfy(codigo ->
                assertThat(jdbc().queryForObject(
                        "SELECT count(*) FROM paises p WHERE p.code = ?", Integer.class, codigo))
                        .as("pais %s presente", codigo)
                        .isEqualTo(1));
    }

    @Test
    @DisplayName("no pisa los datos de un pais que ya estaba, solo le corrige la region")
    void noTocaLosPaisesQueYaEstan() {
        flyway("6").migrate();

        // Alguien corrigio el pais a mano y sus datos no son los de la migracion.
        jdbc().update("""
                UPDATE paises
                SET name = 'Mexico', capital = 'CDMX', lat = 25.0, lng = -100.0, region = 'NORTEAMERICA'
                WHERE code = 'MX'
                """);

        flyway("latest").migrate();

        // El unico campo que V7 toca es `region`. Si empieza a sobreescribir el
        // resto, esta asercion falla.
        assertThat(jdbc().queryForMap("SELECT * FROM paises WHERE code = 'MX'"))
                .containsEntry("NAME", "Mexico")
                .containsEntry("CAPITAL", "CDMX")
                .containsEntry("LAT", 25.0)
                .containsEntry("REGION", "Norteamérica");
    }

    @Test
    @DisplayName("el CHECK rechaza una region inventada, en vez de dejarla pasar")
    void elCheckRechazaUnaRegionInventada() {
        flyway("latest").migrate();

        assertThatThrownBy(() -> jdbc().update("""
                INSERT INTO paises (code, name, capital, lat, lng, region, desc_text)
                VALUES ('XX', 'Pais Inventado', 'Capital', 0.0, 0.0, 'CONO_SUR', 'x')
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("paises_region_check");
    }

    @Test
    @DisplayName("aplicar V7 dos veces no inserta paises duplicados ni falla")
    void reejecutarLaMigracionEsInofensivo() {
        flyway("latest").migrate();
        List<String> despuesDeLaPrimera = codigos();

        // Se borra la fila del historial para que Flyway vuelva a ejecutar el
        // archivo entero. Es el escenario de tener que reejecutar V7 a mano
        // durante una depuracion, asi que la migracion deberia poder con ello.
        jdbc().update("DELETE FROM flyway_schema_history WHERE version = '7'");

        flyway("latest").migrate();

        assertThat(codigos()).isEqualTo(despuesDeLaPrimera);
        assertThat(codigos()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("si queda una region que no es canonica, V7 aborta y no deja nada a medias")
    void laGuardiaAbortaSiQuedaUnValorQueNoEsCanonico() {
        flyway("6").migrate();

        // Un valor que no es canonico pero tampoco es uno de los nombres del enum
        // viejo: es el caso que un UPPER() generico habria dejado pasar en
        // silencio, y el motivo de que el mapeo sea explicito.
        jdbc().update("UPDATE paises SET region = 'norteamerica' WHERE code = 'MX'");
        jdbc().update("UPDATE paises SET region = 'ANDINA' WHERE code = 'PE'");

        assertThatThrownBy(() -> flyway("latest").migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("norteamerica");

        // La migracion va en transaccion: los UPDATE del punto 1 tambien se han
        // deshecho, asi que la base no queda con unas regiones normalizadas y
        // otras no.
        assertThat(estadosDeRegion())
                .containsExactlyInAnyOrder("norteamerica", "ANDINA");
        assertThat(codigos()).hasSize(2);
    }

    // --- utilidades ---------------------------------------------------------

    private Flyway flyway(String objetivo) {
        return Flyway.configure()
                .dataSource(fuente)
                .locations("classpath:db/migration")
                .schemas(esquema)
                .defaultSchema(esquema)
                // El mismo `out-of-order` que lleva application.properties, para
                // que la prueba no dependa de una configuracion que la app si
                // tiene.
                .outOfOrder(true)
                .target(objetivo)
                .load();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(fuente);
    }

    private List<String> codigos() {
        return jdbc().queryForList("SELECT code FROM paises ORDER BY code", String.class);
    }

    private String regionDe(String code) {
        return jdbc().queryForObject("SELECT region FROM paises WHERE code = ?", String.class, code);
    }

    private List<String> estadosDeRegion() {
        return jdbc().queryForList("SELECT DISTINCT region FROM paises", String.class);
    }

    private Map<String, String> regionesPorCodigo() {
        Map<String, String> porCodigo = new LinkedHashMap<>();
        for (Map<String, Object> fila : jdbc().queryForList("SELECT code, region FROM paises ORDER BY code")) {
            porCodigo.put((String) fila.get("code"), (String) fila.get("region"));
        }
        return porCodigo;
    }
}
