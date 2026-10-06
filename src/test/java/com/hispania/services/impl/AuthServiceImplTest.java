package com.hispania.services.impl;

import com.hispania.config.SecurityConfig;
import com.hispania.exception.CredencialesInvalidasException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.response.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la renovacion del token al revalidar la sesion.
 *
 * <p>Existe por el defecto que cerro el Sprint III: el rol viaja dentro del JWT,
 * asi que al promover a alguien su token seguia diciendo {@code USUARIO} durante
 * las ocho horas de caducidad. La interfaz ya mostraba el rol nuevo, porque lo
 * leia de la base, pero cada llamada a la API devolvia 403.
 *
 * <p>El caso que mas importa es el ultimo: una cuenta desactivada no debe recibir
 * un token nuevo. Como la API no consulta la base en cada peticion, este endpoint
 * es la unica via por la que una desactivacion puede cortar el acceso antes de
 * que caduque el token.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Emision y renovacion del token")
class AuthServiceImplTest {

    private static final Long ID = 7L;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(usuarios, passwordEncoder, jwtEncoder, "hispania-atlas", 8);
    }

    @Test
    @DisplayName("el token renovado lleva el rol de la base, no el que traia el token anterior")
    void renuevaConElRolVigenteDeLaBase() {
        dadaUnaCuentaConRol(Rol.COLABORADOR);

        AuthResponse respuesta = service.usuarioActual(ID);

        JwtClaimsSet claims = claimsDelTokenEmitido();
        assertThat(claims.getClaimAsStringList(SecurityConfig.ROLES_CLAIM)).containsExactly("COLABORADOR");
        assertThat(respuesta.usuario().rol()).isEqualTo(Rol.COLABORADOR);
    }

    @Test
    @DisplayName("el token renovado conserva el identificador de la cuenta")
    void conservaElIdentificadorDeLaCuenta() {
        dadaUnaCuentaConRol(Rol.ADMIN);

        service.usuarioActual(ID);

        assertThat((Long) claimsDelTokenEmitido().getClaim("uid")).isEqualTo(ID);
    }

    @Test
    @DisplayName("devuelve un token utilizable con su tipo y su caducidad")
    void devuelveUnTokenUtilizable() {
        dadaUnaCuentaConRol(Rol.USUARIO);

        AuthResponse respuesta = service.usuarioActual(ID);

        assertThat(respuesta.token()).isEqualTo("token.de.prueba");
        assertThat(respuesta.tokenType()).isEqualTo("Bearer");
        assertThat(respuesta.expiresIn()).isEqualTo(8 * 3600);
    }

    @Test
    @DisplayName("los datos de la cuenta son los de la base, no los del token")
    void devuelveLosDatosDeLaBase() {
        dadaUnaCuentaConRol(Rol.COLABORADOR);

        AuthResponse respuesta = service.usuarioActual(ID);

        assertThat(respuesta.usuario().username()).isEqualTo("ana");
        assertThat(respuesta.usuario().email()).isEqualTo("ana@test.com");
        assertThat(respuesta.usuario().activo()).isTrue();
    }

    @Test
    @DisplayName("una cuenta desactivada recibe 401 y no un token nuevo")
    void noRenuevaElTokenDeUnaCuentaDesactivada() {
        when(usuarios.findById(ID)).thenReturn(Optional.of(usuario(ID, "ana", Rol.COLABORADOR, false)));

        assertThatThrownBy(() -> service.usuarioActual(ID))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(jwtEncoder, never()).encode(any(JwtEncoderParameters.class));
    }

    @Test
    @DisplayName("una cuenta que ya no existe responde 404 y no firma nada")
    void fallaSiLaCuentaNoExiste() {
        when(usuarios.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.usuarioActual(ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(jwtEncoder, never()).encode(any(JwtEncoderParameters.class));
    }

    private void dadaUnaCuentaConRol(Rol rol) {
        when(usuarios.findById(ID)).thenReturn(Optional.of(usuario(ID, "ana", rol, true)));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(tokenFalso());
    }

    /** Claims tal como se firmaron, que es lo que viaja dentro del JWT. */
    private JwtClaimsSet claimsDelTokenEmitido() {
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        return captor.getValue().getClaims();
    }

    private static Jwt tokenFalso() {
        return Jwt.withTokenValue("token.de.prueba")
                .header("alg", "HS256")
                .claim("sub", "ana")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(8 * 3600))
                .build();
    }

    /** Usuario con id forzado por reflexion, porque el id lo asigna la base. */
    private static Usuario usuario(Long id, String username, Rol rol, boolean activo) {
        Usuario u = new Usuario(username, username + "@test.com", "hash", username, rol);
        u.setActivo(activo);
        try {
            var campo = Usuario.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo asignar el id en la prueba", e);
        }
        return u;
    }
}
