package com.hispania.config;

import com.hispania.persistence.entity.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la jerarquia de permisos.
 *
 * <p>Es la logica con mas riesgo de todo el modulo de usuarios: un error aqui no
 * produce una excepcion, produce un agujero. Un {@code >=} donde deberia haber un
 * {@code >} permitiria que un administrador degradara a otro y, con ello, que
 * cualquiera escalase privilegios hasta el control total. Por eso se cubren las
 * igualdades, no solo los casos evidentes.
 */
@DisplayName("Jerarquia de roles")
class JerarquiaTest {

    private final Jerarquia jerarquia = new Jerarquia();

    private static Authentication como(Rol rol) {
        return new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority(rol.getAuthority())));
    }

    private static Authentication anonimo() {
        // Una sesion anonima esta autenticada a proposito (`isAuthenticated()` da
        // true) pero no lleva ninguna autoridad ROLE_, que es justo lo que se
        // comprueba aqui: que estar "autenticado" no es lo mismo que tener permisos.
        return new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("SCOPE_anonymous")));
    }

    @Nested
    @DisplayName("Rol")
    class OrdenDeRangos {

        @Test
        @DisplayName("los rangos son 0, 1, 2 y 3 y cada uno supera al anterior")
        void rangosConsecutivos() {            assertThat(Rol.USUARIO.getRango()).isLessThan(Rol.COLABORADOR.getRango());
            assertThat(Rol.COLABORADOR.getRango()).isLessThan(Rol.ADMIN.getRango());
            assertThat(Rol.ADMIN.getRango()).isLessThan(Rol.ADMIN_SISTEMA.getRango());
        }

        @Test
        @DisplayName("cada rol incluye a si mismo y a todos los inferiores")
        void incluyeInferiores() {
            assertThat(Rol.ADMIN_SISTEMA.incluye(Rol.USUARIO)).isTrue();
            assertThat(Rol.ADMIN_SISTEMA.incluye(Rol.ADMIN_SISTEMA)).isTrue();
            assertThat(Rol.USUARIO.incluye(Rol.COLABORADOR)).isFalse();
            assertThat(Rol.USUARIO.incluye(Rol.USUARIO)).isTrue();
        }

        @Test
        @DisplayName("superar() excluye al propio rango: no es lo mismo que incluye()")
        void superarEsEstricto() {
            assertThat(Rol.ADMIN.supera(Rol.ADMIN)).isFalse();
            assertThat(Rol.ADMIN.incluye(Rol.ADMIN)).isTrue();
            assertThat(Rol.ADMIN_SISTEMA.supera(Rol.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("noSupera() incluye la igualdad: es el espejo exacto de supera()")
        void noSuperaEsElEspejoDeSupera() {
            for (Rol actual : Rol.values()) {
                for (Rol otro : Rol.values()) {
                    assertThat(actual.noSupera(otro))
                            .as("%s no supera a %s", actual, otro)
                            .isEqualTo(!actual.supera(otro));
                }
            }
        }

        @Test
        @DisplayName("la autoridad lleva el prefijo ROLE_ que espera Spring Security")
        void autoridad() {
            assertThat(Rol.ADMIN.getAuthority()).isEqualTo("ROLE_ADMIN");
        }
    }

    @Nested
    @DisplayName("puede(autenticacion, rolMinimo)")
    class PermisoMinimo {

        @Test
        @DisplayName("un USUARIO no puede hacer lo de un colaborador")
        void usuarioNoColabora() {
            assertThat(jerarquia.puede(como(Rol.USUARIO), "COLABORADOR")).isFalse();
        }

        @Test
        @DisplayName("un ADMIN puede hacer lo de un colaborador, por jerarquia")
        void adminIncluyeColaborador() {
            assertThat(jerarquia.puede(como(Rol.ADMIN), "COLABORADOR")).isTrue();
        }

        @Test
        @DisplayName("un ADMIN no puede hacer lo de un ADMIN_SISTEMA")
        void adminNoEsAdminSistema() {
            assertThat(jerarquia.puede(como(Rol.ADMIN), "ADMIN_SISTEMA")).isFalse();
        }

        @Test
        @DisplayName("un USUARIO puede siempre lo de un USUARIO")
        void usuarioPuedeLoDeUsuario() {
            assertThat(jerarquia.puede(como(Rol.USUARIO), "USUARIO")).isTrue();
        }

        @Test
        @DisplayName("sin sesion no hay permiso, ni siquiera el minimo")
        void sinSesion() {
            assertThat(jerarquia.puede(null, "USUARIO")).isFalse();
        }

        @Test
        @DisplayName("una sesion anonima no concede el minimo")
        void anonima() {
            assertThat(jerarquia.puede(anonimo(), "USUARIO")).isFalse();
        }
    }

    @Nested
    @DisplayName("puedeProponer(autenticacion)")
    class PermisoParaProponer {

        @Test
        @DisplayName("un USUARIO propone: es justo lo que le diferencia de un visitante")
        void usuarioPropone() {
            assertThat(jerarquia.puedeProponer(como(Rol.USUARIO))).isTrue();
        }

        @Test
        @DisplayName("un COLABORADOR tambien propone, y tambien puede crearlos directo")
        void colaboradorPropone() {
            // Es la frontera de la regla: el maximo autorizado. Si el dia que se anada
            // un rol intermedio se cambia el corte, este es el test que se rompe.
            assertThat(jerarquia.puedeProponer(como(Rol.COLABORADOR))).isTrue();
        }

        @Test
        @DisplayName("un ADMIN NO propone: ya puede crear el lugar directamente")
        void adminNoPropone() {
            assertThat(jerarquia.puedeProponer(como(Rol.ADMIN))).isFalse();
        }

        @Test
        @DisplayName("un ADMIN_SISTEMA tampoco propone")
        void adminSistemaNoPropone() {
            assertThat(jerarquia.puedeProponer(como(Rol.ADMIN_SISTEMA))).isFalse();
        }

        @Test
        @DisplayName("la regla es el complemento exacto de exigir ADMIN, no un caso suelta")
        void esLaInversaDelUmbralEquivocado() {
            // Si alguien escribiera esta regla como `puede(authentication, 'ADMIN')`,
            // que es el patron de todas las demas anotaciones, entrarian justo los dos
            // roles que deben quedar fuera. Se fija aqui que la relacion es de
            // complemento estricto: proponer es lo contrario de tener rango de ADMIN.
            for (Rol rol : Rol.values()) {
                assertThat(jerarquia.puedeProponer(como(rol)))
                        .as("%s frente a puede(ADMIN)", rol)
                        .isEqualTo(!jerarquia.puede(como(rol), "ADMIN"));
            }
        }

        @Test
        @DisplayName("sin sesion no se propone")
        void sinSesion() {
            assertThat(jerarquia.puedeProponer(null)).isFalse();
        }

        @Test
        @DisplayName("una sesion anonima no propone")
        void anonima() {
            assertThat(jerarquia.puedeProponer(anonimo())).isFalse();
        }
    }

    @Nested
    @DisplayName("puedeModificar(autenticacion, rolObjetivo)")
    class PermisoSobreCuenta {

        @Test
        @DisplayName("un ADMIN puede tocar a un USUARIO y a un COLABORADOR")
        void adminSobreInferiores() {
            assertThat(jerarquia.puedeModificar(como(Rol.ADMIN), Rol.USUARIO)).isTrue();
            assertThat(jerarquia.puedeModificar(como(Rol.ADMIN), Rol.COLABORADOR)).isTrue();
        }

        @Test
        @DisplayName("un ADMIN NO puede tocar a otro ADMIN: mismo rango, no superior")
        void adminSobreAdmin() {
            assertThat(jerarquia.puedeModificar(como(Rol.ADMIN), Rol.ADMIN)).isFalse();
        }

        @Test
        @DisplayName("un ADMIN NO puede tocar a un ADMIN_SISTEMA")
        void adminSobreAdminSistema() {
            assertThat(jerarquia.puedeModificar(como(Rol.ADMIN), Rol.ADMIN_SISTEMA)).isFalse();
        }

        @Test
        @DisplayName("un ADMIN_SISTEMA si puede tocar a un ADMIN")
        void adminSistemaSobreAdmin() {
            assertThat(jerarquia.puedeModificar(como(Rol.ADMIN_SISTEMA), Rol.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("un USUARIO no puede tocar a nadie")
        void usuarioSobreNadie() {
            assertThat(jerarquia.puedeModificar(como(Rol.USUARIO), Rol.USUARIO)).isFalse();
        }
    }

    @Nested
    @DisplayName("puedeAsignar(autenticacion, rolNuevo)")
    class PermisoParaDelegar {

        @Test
        @DisplayName("un ADMIN no puede crear un ADMIN_SISTEMA")
        void adminNoCreaAdminSistema() {
            assertThat(jerarquia.puedeAsignar(como(Rol.ADMIN), Rol.ADMIN_SISTEMA)).isFalse();
        }

        @Test
        @DisplayName("un ADMIN si puede crear un COLABORADOR o un ADMIN")
        void adminCreaInferiores() {
            assertThat(jerarquia.puedeAsignar(como(Rol.ADMIN), Rol.COLABORADOR)).isTrue();
            assertThat(jerarquia.puedeAsignar(como(Rol.ADMIN), Rol.ADMIN)).isTrue();
        }

        @Test
        @DisplayName("un ADMIN_SISTEMA puede crear cualquier rol")
        void adminSistemaCreaTodos() {
            for (Rol rol : Rol.values()) {
                assertThat(jerarquia.puedeAsignar(como(Rol.ADMIN_SISTEMA), rol))
                        .as("ADMIN_SISTEMA creando %s", rol)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("rolActual(autenticacion)")
    class LecturaDelToken {

        @Test
        @DisplayName("devuelve el rol de la autoridad")
        void leeRol() {
            assertThat(jerarquia.rolActual(como(Rol.COLABORADOR))).isEqualTo(Rol.COLABORADOR);
        }

        @Test
        @DisplayName("devuelve null si no hay autoridad con el prefijo ROLE_")
        void sinPrefijo() {
            Authentication sinRol = new UsernamePasswordAuthenticationToken(
                    "ana", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_read")));

            assertThat(jerarquia.rolActual(sinRol)).isNull();
        }

        @Test
        @DisplayName("un rol que ya no existe en el enum no tumba nada: no concede permiso")
        void rolObsoleto() {
            Authentication obsoleto = new UsernamePasswordAuthenticationToken(
                    "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));

            assertThat(jerarquia.rolActual(obsoleto)).isNull();
        }
    }
}
