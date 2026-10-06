package com.hispania.persistence;

import com.hispania.persistence.entity.CategoriaExpresion;
import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.Region;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.presentation.dto.response.ExpresionCulturalResponse;
import com.hispania.presentation.dto.response.PaisResponse;
import com.hispania.services.interfaces.ExpresionCulturalService;
import com.hispania.services.interfaces.PaisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Levanta la aplicacion completa contra un Postgres de verdad y comprueba que la cadena
 * de migraciones deja una base utilizable.
 *
 * <p>Esta prueba existe porque un Postgres en memoria no sirve para nada de lo que ha
 * fallado aqui. Los dos fallos que encontro ejecutando contra la base real no habrian
 * salido con una base de mentira:
 *
 * <ul>
 *   <li>V1 sembraba {@code 'Arte'} y {@code 'Arqueologia'}, y el CHECK de V2 solo
 *       acepta mayusculas: la base no se podia construir (arreglado con V1_1).</li>
 *   <li>{@code Region} guardaba el nombre de la constante en vez del texto acentuado,
 *       asi que leer los paises daba 500 y el JSON mandaba un valor que el frontend
 *       rechazaba.</li>
 * </ul>
 *
 * <p>Que el contexto arranque ya es parte de la asercion: `ddl-auto=validate` compara
 * las entidades con el esquema migrado y detiene el arranque si no coinciden, de modo
 * que las pruebas no pueden pasar con el esquema desalineado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@DisplayName("La cadena de migraciones sobre Postgres real")
class MigracionesSobrePostgresTest {

    /**
     * La misma major que declara el README. El alpine va porque es mas rapido de
     * arrancar y las pruebas solo necesitan el motor, no las utilidades del imagen
     * completa.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        // `jwt.secret` no tiene valor por defecto en application.properties a
        // proposito, asi que hay que darle uno aunque esta prueba no firme nada.
        registro.add("jwt.secret", () -> "clave-que-solo-vive-en-esta-prueba-de-integracion-1234567890");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaisRepository paisRepository;

    @Autowired
    private PaisService paisService;

    @Autowired
    private ExpresionCulturalService expresionService;

    @Test
    @DisplayName("aplica V1..V9 mas V1_1, sin saltos y en el orden esperado")
    void aplicaTodaLaCadena() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(versiones).containsExactly("1", "1.1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    @DisplayName("V7 deja los 19 paises que define el frontend, cada uno con una region canonica")
    void siembraLosPaisesQueFaltaban() {
        // V3 solo siembra Mexico y Peru, y V7 anade los 17 que faltaban. El
        // frontend ya define 19, asi que es el numero que tiene que haber.
        assertThat(paisRepository.findAllByOrderByCodeAsc()).hasSize(19);

        // La comprobacion fuerte no es el total, sino que ningun pais quede con un
        // valor de region que el enum no sepa resolver: eso es exactamente lo que
        // devolvia un 500 en GET /api/countries.
        assertThat(jdbc.queryForList("SELECT DISTINCT region FROM paises", String.class))
                .containsExactlyInAnyOrder("Norteamérica", "Centroamérica", "Caribe", "Andina", "Cono Sur");
    }

    @Test
    @DisplayName("V1_1 deja la categoria de los lugares en un valor que el enum y el CHECK aceptan")
    void normalizaLasCategoriasQueSembroV1() {
        // Estas dos filas son las que V1 sembro con 'Arqueologia' y 'Arte' en
        // mayuscula inicial, y las que bloqueaban el CHECK de V2.
        assertThat(categoriaDe("machu")).isEqualTo(CategoriaLugar.ARQUEOLOGIA);
        assertThat(categoriaDe("mali")).isEqualTo(CategoriaLugar.ARTE);

        // Y ninguna de las filas que siemicolon las migraciones queda fuera del enum:
        // si alguien anade una categoria al enum y no la anade a la migracion, o al
        // reves, esta comprobacion se entera.
        List<String> huerfanas = jdbc.queryForList(
                "SELECT DISTINCT category FROM lugares WHERE category NOT IN "
                        + "('ARTE','DANZA','ARQUEOLOGIA','PATRIMONIO','HISTORICO',"
                        + "'INFRAESTRUCTURA','PAISAJE_NATURAL','ACADEMICO','GASTRONOMICO')",
                String.class);

        assertThat(huerfanas).isEmpty();
    }

    @Test
    @DisplayName("la columna region guarda el texto acentuado, no el nombre de la constante")
    void regionSeGuardaComoTextoAcentuado() {
        // Lo que hay literalmente en la base, sin pasar por el enum: es el contrato
        // que escribieron las migraciones y que espera el frontend.
        assertThat(regionEnLaBase("MX")).isEqualTo("Norteamérica");
        assertThat(regionEnLaBase("PE")).isEqualTo("Andina");
    }

    @Test
    @DisplayName("leer los paises no revienta: cada region de la base tiene su enum")
    void leerLosPaisesFunciona() {
        // Este es el recorrido que daba 500 con el enum desalineado. Si un valor de la
        // base no encuentra su constante, Hibernate lanza aqui y no en produccion.
        List<Region> regiones = paisRepository.findAllByOrderByCodeAsc().stream()
                .map(pais -> pais.getRegion())
                .toList();

        assertThat(regiones).isNotEmpty().doesNotContainNull();
    }

    @Test
    @DisplayName("la respuesta de la API viaja con el texto que valida el frontend")
    void laRespuestaExponeElTextoDelFrontend() {
        // El frontend no usa `as Region` a lo bruto: valida contra una lista cerrada
        // ('Norteamerica' acentuada, 'Centroamerica', 'Caribe', 'Andina', 'Cono Sur') y
        // si no coincide avisa y cae en 'Andina'. Si aqui saliera 'NORTEAMERICA',
        // Mexico se dibujaria en la region equivocada sin que nada fallara.
        assertThat(paisService.listarTodos())
                .allSatisfy(pais ->
                        assertThat(pais.region()).isIn("Norteamérica", "Centroamérica",
                                "Caribe", "Andina", "Cono Sur"));
    }

    @Test
    @DisplayName("los lugares de un pais vienen anidados, como espera el frontend")
    void losLugaresVienenAnidados() {
        var peru = paisService.buscarPorCodigo("PE");

        assertThat(peru.lugares()).isNotEmpty();
        assertThat(peru.lugares())
                .allSatisfy(lugar -> assertThat(lugar.category()).isNotNull());
    }

    /**
     * Lee la categoria cruda de la base y la resuelve como enum.
     *
     * <p>El {@code valueOf} no es un detalle: si el texto de la base no coincide
     * exactamente con el nombre de la constante lanza {@code IllegalArgumentException}, y
     * con eso queda fijado justo el invariante que rompio V1 al sembrar 'Arqueologia'.
     */
    @Test
    @DisplayName("V8 siembra las cuatro expresiones del frontend, ya con el pais por codigo")
    void siembraLasExpresionesQueEstabanEnElFrontend() {
        // Las cuatro venian de `art-data.ts`, con el pais como texto suelto ('Peru',
        // 'Mexico', 'Argentina'). Aqui `pais_code` es una FK, y eso es justo lo que
        // hacia falta: un texto que no participa en ninguna consulta no avisa cuando
        // se equivoca.
        var expresiones = expresionService.listarTodas();

        assertThat(expresiones).hasSize(4);
        assertThat(expresiones)
                .extracting(ExpresionCulturalResponse::id)
                .containsExactlyInAnyOrder("la_escuela_cusquena", "el_muralismo",
                        "la_marinera", "el_tango");
        assertThat(expresiones)
                .extracting(ExpresionCulturalResponse::paisCode)
                .containsExactlyInAnyOrder("PE", "MX", "PE", "AR");
    }

    @Test
    @DisplayName("la categoria de una expresion es un valor del enum, no texto libre")
    void laCategoriaDeUnaExpresionEsDelEnum() {
        // Las categorias del frontend eran 'Pintura Virreinal', 'Danza Tradicional'...
        // y el filtro las agrupaba como texto. Con enum, cada expression cae en una
        // disciplina, y el CHECK de V8 impide que aparezca una categoria nueva por un
        // descuido al escribir.
        assertThat(expresionService.listarTodas())
                .extracting(ExpresionCulturalResponse::categoria)
                .containsExactlyInAnyOrder("PINTURA", "PINTURA", "DANZA", "DANZA")
                .allSatisfy(categoria ->
                        assertThat(CategoriaExpresion.valueOf(categoria)).isNotNull());
    }

    @Test
    @DisplayName("la imagen viaja como ruta servida y siempre con sus creditos")
    void laImagenSeSirveDelBackendYConCreditos() {
        // Las cuatro imagenes originales eran enlaces a Bing y Pinterest, de licencia
        // desconocida. Ahora son ficheros en `static/expresiones/`, y la columna
        // `creditos` no es opcional cuando hay imagen.
        var conImagen = expresionService.listarTodas().stream()
                .filter(e -> e.imagenUrl() != null)
                .toList();

        assertThat(conImagen).hasSize(4);
        assertThat(conImagen)
                .allSatisfy(e -> {
                    assertThat(e.imagenUrl()).startsWith("/expresiones/");
                    assertThat(e.creditos()).isNotBlank();
                });
    }

    @Test
    @DisplayName("el CHECK rechaza una categoria inventada y una imagen sin creditos")
    void losChecksDeLaTablaEstanPuestos() {
        // Los dos CHECK que evitan el tipo de dato que ya dio problemas una vez: el enum
        // desincronizado del CHECK, y material de origen desconocido.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO expresiones_culturales
                    (id, titulo, categoria, pais_code, desc_text)
                VALUES ('prueba', 'Prueba', 'AFROAMERICANA', 'AR', 'x')
                """))
                .hasMessageContaining("ck_expresiones_categoria");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO expresiones_culturales
                    (id, titulo, categoria, pais_code, desc_text, imagen)
                VALUES ('prueba2', 'Prueba', 'MUSICA', 'AR', 'x', 'sin-creditos.jpg')
                """))
                .hasMessageContaining("ck_expresiones_imagen_creditos");
    }

    @Test
    @DisplayName("un pais sin expresiones devuelve 200 con lista vacia, no 404")
    void unPaisSinExpresionesNoEsUnError() {
        // 15 de los 19 paises no tienen ninguna todavia. Si esto devolviera 404, la
        // ficha del mapa dejaria de funcionar para casi todos, asi que el 404 queda
        // reservado al pais que no existe.
        var codigoSinExpresiones = paisService.listarTodos().stream()
                .map(PaisResponse::code)
                .filter(c -> !"PE".equals(c) && !"MX".equals(c) && !"AR".equals(c))
                .findFirst()
                .orElseThrow();

        assertThat(expresionService.listarPorPais(codigoSinExpresiones)).isEmpty();
        assertThat(expresionService.listarPorPais("ZZ")).isNull();
    }

    @Test
    @DisplayName("la cola de propuestas acepta la pendiente y veta el rechazo sin motivo y el pais inventado")
    void laColaDePropuestasAceptaLoValidoYRechazaLoInvalido() {
        // `propuestas_expresion` llega viva desde V8, pero hasta ahora solo se comprobaba
        // que la tabla existiera. Estas pruebas la ejercitan de verdad, porque son los
        // CHECK y la FK los que respaldan las decisiones del servicio: si la base
        // aceptara un rechazo sin motivo, el servicio dejaria de ser quien protege ese
        // dato y no habria quien lo hiciera.
        // La FK de `propuesto_por` apunta a `usuarios`, y las migraciones no siembran
        // ninguno: los crea el bootstrap al arrancar, que esta prueba no ejecuta. Se
        // siembra uno minimo porque la tabla vacia haria que el INSERT fallara por la
        // FK y no por lo que se quiere comprobar.
        long idUsuario = jdbc.queryForObject("""
                INSERT INTO usuarios (username, email, password_hash, nombre, rol)
                VALUES ('moderador_prueba', 'moderador_prueba@test.com', 'hash', 'Moderador', 'COLABORADOR')
                RETURNING id
                """, Long.class);

        // Lo que si se acepta: una propuesta pendiente, sin revisor ni motivo.
        jdbc.update("""
                INSERT INTO propuestas_expresion
                    (titulo, categoria, country, desc_text, estado, propuesto_por)
                VALUES ('Cafe de la libertad', 'GASTRONOMIA', 'BO', 'Descripcion', 'PENDIENTE', ?)
                """, idUsuario);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM propuestas_expresion WHERE country = 'BO'", Integer.class))
                .isEqualTo(1);

        // Rechazo sin motivo: el CHECK lo veta. El servicio tambien lo comprueba, pero
        // el CHECK es lo que evita que un script deje el dato sucio.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO propuestas_expresion
                    (titulo, categoria, country, desc_text, estado, propuesto_por)
                VALUES ('Otra', 'MUSICA', 'BO', 'Descripcion', 'RECHAZADA', ?)
                """, idUsuario))
                .hasMessageContaining("ck_propuestas_expresion_rechazo_coherente");

        // Pais que no existe: la FK lo veta. Es el fallo que daria un 500 si el
        // servicio no comprobara el pais antes de guardar.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO propuestas_expresion
                    (titulo, categoria, country, desc_text, estado, propuesto_por)
                VALUES ('Otra', 'MUSICA', 'ZZ', 'Descripcion', 'PENDIENTE', ?)
                """, idUsuario))
                .hasMessageContaining("fk_propuestas");

        // Aprobada sin revisor ni fecha: lo veta el segundo CHECK de V9. Sin el, la
        // cola podria dar por buena una propuesta que nadie llego a mirar.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO propuestas_expresion
                    (titulo, categoria, country, desc_text, estado, propuesto_por)
                VALUES ('Otra', 'MUSICA', 'BO', 'Descripcion', 'APROBADA', ?)
                """, idUsuario))
                .hasMessageContaining("ck_propuestas_expresion_revision_coherente");

        // Y la fila valida sigue ahi: los rechazos de arriba no se la llevaron.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM propuestas_expresion", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("una expresion creada desde la base se lee por el servicio y la UNIQUE por pais y categoria manda")
    void unaExpresionCreadaSeLeeYLaUnicaPorPaisYCategoriaManda() {
        // El servicio unitario ya comprueba que aprobar inserte la expresion, pero solo
        // con dobles. Aqui se comprueba el otro lado: que la fila creada sea legible
        // por la via publica y que la UNIQUE (pais_code, categoria) haga su trabajo.
        String paisSinExpresion = paisService.listarTodos().stream()
                .map(PaisResponse::code)
                .filter(c -> !"PE".equals(c) && !"MX".equals(c) && !"AR".equals(c))
                .findFirst()
                .orElseThrow();

        String id = "baile_de_" + paisSinExpresion.toLowerCase();
        expresionService.crear(id, "Baile de muestra", CategoriaExpresion.DANZA,
                paisSinExpresion, "Descripcion", null, null);

        var creadas = expresionService.listarPorPais(paisSinExpresion);

        assertThat(creadas).hasSize(1);
        assertThat(creadas.get(0).id()).isEqualTo(id);
        assertThat(creadas.get(0).categoria()).isEqualTo("DANZA");

        // Una segunda expresion de la misma disciplina en el mismo pais no cabe, que
        // es lo que impide que la seccion crezca sin criterio.
        assertThatThrownBy(() -> expresionService.crear("otro_baile", "Otro baile",
                CategoriaExpresion.DANZA, paisSinExpresion, "Descripcion", null, null))
                .hasMessageContaining("ya tiene una expresion de categoria");
    }

    private CategoriaLugar categoriaDe(String idLugar) {
        String texto = jdbc.queryForObject(
                "SELECT category FROM lugares WHERE id = ?", String.class, idLugar);

        return CategoriaLugar.valueOf(texto);
    }

    private String regionEnLaBase(String code) {
        return jdbc.queryForObject(
                "SELECT region FROM paises WHERE code = ?", String.class, code);
    }
}
