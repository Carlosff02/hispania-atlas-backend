package com.hispania.presentation.controllers;

import com.hispania.config.Jerarquia;
import com.hispania.persistence.entity.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El umbral de escritura de cada endpoint, deducido de su propio {@code @PreAuthorize}.
 *
 * <p>Existe por un motivo concreto: los umbrales son el unico sitio del modulo donde
 * un error no lanza una excepcion, produce un agujero silencioso. Si el borrado de
 * lugares vuelve a escribirse con {@code 'COLABORADOR'} para "mantener el patron" de
 * la creacion, nada falla en los tests existentes: la anotacion es correcta para
 * Spring, el endpoint responde, y lo que cambia es que un colaborador puede borrar
 * un lugar curado.
 *
 * <p>No se comparan las cadenas de las anotaciones, que se rompen con un cambio de
 * formato sin que cambie el comportamiento. Lo que se evalua es la expresion tal cual,
 * con el bean {@code jerarquia} de verdad, para cada rol. Asi el test no puede
 * desincronizarse de lo que hace la API: si el umbral se equivoca, falla; si solo
 * cambia el texto, no.
 *
 * <p>La seguridad de metodo no se carga en {@code @WebMvcTest}, asi que
 * {@code LugarControllerTest} no puede comprobar esto por HTTP. Aqui se resuelve la
 * expresion directamente, que es exactamente lo que Spring hara al invocarla.
 */
@DisplayName("Umbrales de escritura por endpoint")
class PermisosDeEscrituraTest {

    private final Jerarquia jerarquia = new Jerarquia();
    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static Authentication como(Rol rol) {
        return new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority(rol.getAuthority())));
    }

    /**
     * Devuelve la expresion de {@code @PreAuthorize} tal cual, sin interpretarla.
     *
     * <p>Se busca por nombre y se exige que haya una sola coincidencia: si alguien
     * anade una sobrecarga, el test avisa en vez de comprobar la que le parezca.
     */
    private static String expresionDe(Class<?> controller, String metodo) {
        Method[] candidatos = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals(metodo))
                .toArray(Method[]::new);

        assertThat(candidatos)
                .as("metodos llamados '%s' en %s", metodo, controller.getSimpleName())
                .hasSize(1);

        PreAuthorize anotacion = candidatos[0].getAnnotation(PreAuthorize.class);
        assertThat(anotacion)
                .as("@PreAuthorize en %s.%s()", controller.getSimpleName(), metodo)
                .isNotNull();

        return anotacion.value();
    }

    /**
     * Evalua la expresion con un rol dado.
     *
     * <p>La raiz es un mapa con {@code authentication}, que es como Spring expone el
     * objeto de seguridad dentro de {@code @PreAuthorize}, y el bean se registra con
     * el nombre que usa la anotacion. Asi la expresion no se toca en absoluto.
     */
    private boolean concede(String expresion, Rol rol) {
        StandardEvaluationContext contexto =
                new StandardEvaluationContext(Map.of("authentication", como(rol)));
        // Sin esto, SpEL busca `authentication` como una propiedad del mapa y falla:
        // el acceso por clave hay que registrarlo a mano.
        contexto.addPropertyAccessor(new MapAccessor());
        // Un solo bean en el contexto, asi que el resolutor devuelve siempre el mismo
        // sin mirar el nombre. Es el equivalente minimo de lo que hace Spring, donde
        // `@jerarquia` se resuelve contra el contexto de la aplicacion.
        contexto.setBeanResolver((BeanResolver) (fabrica, nombre) -> jerarquia);

        Object resultado = parser.parseExpression(expresion).getValue(contexto);
        assertThat(resultado).as("'%s' deberia devolver un booleano", expresion).isInstanceOf(Boolean.class);
        return (Boolean) resultado;
    }

    @Test
    @DisplayName("crear un lugar exige COLABORADOR")
    void crearExigeColaborador() {
        String expresion = expresionDe(LugarController.class, "crear");

        assertThat(concede(expresion, Rol.USUARIO)).isFalse();
        for (Rol rol : List.of(Rol.COLABORADOR, Rol.ADMIN, Rol.ADMIN_SISTEMA)) {
            assertThat(concede(expresion, rol)).as("crear siendo %s", rol).isTrue();
        }
    }

    @Test
    @DisplayName("editar un lugar exige COLABORADOR")
    void editarExigeColaborador() {
        String expresion = expresionDe(LugarController.class, "actualizar");

        assertThat(concede(expresion, Rol.USUARIO)).isFalse();
        for (Rol rol : List.of(Rol.COLABORADOR, Rol.ADMIN, Rol.ADMIN_SISTEMA)) {
            assertThat(concede(expresion, rol)).as("editar siendo %s", rol).isTrue();
        }
    }

    @Test
    @DisplayName("borrar un lugar exige ADMIN, no COLABORADOR: es el unico umbral que no coincide")
    void borrarExigeAdmin() {
        String expresion = expresionDe(LugarController.class, "eliminar");

        // La frontera esta entre ADMIN y COLABORADOR, y los dos lados se comprueban.
        // Si alguien "corrige" el umbral para igualarlo con el de la creacion, este
        // test es el que lo dice.
        assertThat(concede(expresion, Rol.USUARIO)).isFalse();
        assertThat(concede(expresion, Rol.COLABORADOR))
                .as("un colaborador no debe poder borrar: el borrado no se puede deshacer")
                .isFalse();
        assertThat(concede(expresion, Rol.ADMIN)).isTrue();
        assertThat(concede(expresion, Rol.ADMIN_SISTEMA)).isTrue();
    }

    @Test
    @DisplayName("proponer exige NO superar COLABORADOR: la unica regla que excluye")
    void proponerExcluyeALosAdministradores() {
        String expresion = expresionDe(PropuestaController.class, "proponer");

        assertThat(concede(expresion, Rol.USUARIO)).isTrue();
        assertThat(concede(expresion, Rol.COLABORADOR)).isTrue();
        assertThat(concede(expresion, Rol.ADMIN))
                .as("un administrador crea el lugar directamente, no lo propone")
                .isFalse();
        assertThat(concede(expresion, Rol.ADMIN_SISTEMA)).isFalse();
    }

    @Test
    @DisplayName("moderar sigue siendo COLABORADOR+, aunque proponer ya no lo sea")
    void moderarNoCambia() {
        String expresion = expresionDe(PropuestaController.class, "revisar");

        assertThat(concede(expresion, Rol.USUARIO)).isFalse();
        for (Rol rol : List.of(Rol.COLABORADOR, Rol.ADMIN, Rol.ADMIN_SISTEMA)) {
            assertThat(concede(expresion, rol)).as("moderar siendo %s", rol).isTrue();
        }
    }

    @Test
    @DisplayName("leer la cola de pendientes sigue siendo COLABORADOR+")
    void verLaColaNoCambia() {
        String expresion = expresionDe(PropuestaController.class, "pendientes");

        assertThat(concede(expresion, Rol.USUARIO)).isFalse();
        assertThat(concede(expresion, Rol.COLABORADOR)).isTrue();
        assertThat(concede(expresion, Rol.ADMIN)).isTrue();
    }
}
