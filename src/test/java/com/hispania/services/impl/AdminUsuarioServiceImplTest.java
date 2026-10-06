package com.hispania.services.impl;

import com.hispania.config.Jerarquia;
import com.hispania.exception.ForbiddenException;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la administracion de cuentas.
 *
 * <p>Las tres reglas se comprueban de forma independiente porque son la unica
 * defensa contra el escalamiento de privilegios: si una sola falla, un
 * ADMIN_SISTEMA autenticado puede ser degradado por cualquier ADMIN.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Administracion de usuarios")
class AdminUsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarios;

    private AdminUsuarioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminUsuarioServiceImpl(usuarios, new Jerarquia());
    }

    /** Usuario con id forzado por reflexion, porque el id lo asigna la base. */
    private static Usuario usuario(Long id, String username, Rol rol) {
        Usuario u = new Usuario(username, username + "@test.com", "hash", username, rol);
        try {
            var campo = Usuario.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo asignar el id en la prueba", e);
        }
        return u;
    }

    /** Autenticacion de la peticion, tal como la construye la cadena de filtros. */
    private static Authentication como(Long uid, Rol rol) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "quien", "uid", uid, "roles", List.of(rol.name())));

        return new JwtAuthenticationToken(jwt, List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(rol.getAuthority())));
    }

    @Test
    @DisplayName("un ADMIN cambia el rol de un USUARIO a COLABORADOR")
    void adminPromueve() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario(5L, "ana", Rol.USUARIO)));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.cambiarRol(5L, Rol.COLABORADOR, como(1L, Rol.ADMIN));

        assertThat(response.rol()).isEqualTo(Rol.COLABORADOR);
    }

    @Test
    @DisplayName("un ADMIN no puede degradar a otro ADMIN: mismo rango")
    void adminNoTocaAdmin() {
        when(usuarios.findById(6L)).thenReturn(Optional.of(usuario(6L, "luis", Rol.ADMIN)));

        assertThatThrownBy(() -> service.cambiarRol(6L, Rol.USUARIO, como(1L, Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("rango superior");

        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN no puede crear un ADMIN_SISTEMA: no delega lo que no tiene")
    void adminNoCreaAdminSistema() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario(5L, "ana", Rol.USUARIO)));

        assertThatThrownBy(() -> service.cambiarRol(5L, Rol.ADMIN_SISTEMA, como(1L, Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no lo tienes");
    }

    @Test
    @DisplayName("un ADMIN_SISTEMA si puede crear otro ADMIN_SISTEMA")
    void adminSistemaCreaAdminSistema() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario(5L, "ana", Rol.USUARIO)));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.cambiarRol(5L, Rol.ADMIN_SISTEMA, como(1L, Rol.ADMIN_SISTEMA));

        assertThat(response.rol()).isEqualTo(Rol.ADMIN_SISTEMA);
    }

    @Test
    @DisplayName("nadie se cambia a si mismo el rol")
    void noSeCambiaASiMismo() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1L, "quien", Rol.ADMIN)));

        assertThatThrownBy(() -> service.cambiarRol(1L, Rol.USUARIO, como(1L, Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("a ti mismo");
    }

    @Test
    @DisplayName("desactivar exige rango superior")
    void desactivarExigeRango() {
        when(usuarios.findById(6L)).thenReturn(Optional.of(usuario(6L, "luis", Rol.ADMIN)));

        assertThatThrownBy(() -> service.cambiarActivo(6L, false, como(1L, Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("un ADMIN no puede desactivar a un ADMIN_SISTEMA")
    void adminNoDesactivaAdminSistema() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario(7L, "root", Rol.ADMIN_SISTEMA)));

        assertThatThrownBy(() -> service.cambiarActivo(7L, false, como(1L, Rol.ADMIN_SISTEMA)))
                .isInstanceOf(ForbiddenException.class);

        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN desactiva a un USUARIO")
    void adminDesactivaUsuario() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario(5L, "ana", Rol.USUARIO)));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.cambiarActivo(5L, false, como(1L, Rol.ADMIN));

        assertThat(response.activo()).isFalse();
    }

    @Test
    @DisplayName("nadie desactiva su propia cuenta")
    void noSeDesactivaASiMismo() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1L, "quien", Rol.ADMIN)));

        assertThatThrownBy(() -> service.cambiarActivo(1L, false, como(1L, Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("propia cuenta");
    }

    @Test
    @DisplayName("un COLABORADOR no llega a la administracion de usuarios")
    void colaboradorNoAdministra() {
        // Ni siquiera se busca la cuenta objetivo: el servicio exige el rango de
        // ADMIN antes de tocar la base de datos. La anotacion de la ruta ya lo
        // bloquea igual, pero la defensa no depende de que nadie se salte un
        // endpoint. Sin este stub a proposito, para comprobar que el orden es el
        // correcto.
        assertThatThrownBy(() -> service.cambiarRol(5L, Rol.COLABORADOR, como(3L, Rol.COLABORADOR)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("requiere el rango ADMIN");

        verify(usuarios, never()).findById(any());
    }
}
