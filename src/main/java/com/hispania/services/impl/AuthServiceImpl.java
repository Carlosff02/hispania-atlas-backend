package com.hispania.services.impl;

import com.hispania.config.SecurityConfig;
import com.hispania.exception.CredencialesInvalidasException;
import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.request.LoginRequest;
import com.hispania.presentation.dto.request.RegistroRequest;
import com.hispania.presentation.dto.response.AuthResponse;
import com.hispania.presentation.dto.response.UsuarioResponse;
import com.hispania.services.interfaces.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Registro, inicio de sesion y emision del token.
 *
 * <h2>El compromiso de un token sin estado</h2>
 * El token se firma aqui y se valida en la cadena de filtros, sin consultar la
 * base de datos en cada peticion. El precio de ese diseño es que un cambio de rol
 * o una desactivacion no surten efecto hasta que el token caduca. Por eso la
 * caducidad son 8 horas y no un mes: es la ventana maxima en la que un usuario
 * desactivado sigue pudiendo operar. En una API sin estado es una decision
 * consciente, no un descuido.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration expiracion;

    public AuthServiceImpl(UsuarioRepository usuarios,
                           PasswordEncoder passwordEncoder,
                           JwtEncoder jwtEncoder,
                           @Value("${jwt.issuer}") String issuer,
                           @Value("${jwt.expiration-hours:8}") long horasExpiracion) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expiracion = Duration.ofHours(horasExpiracion);
    }

    /**
     * Alta de cuenta. Toda cuenta nueva nace en {@link Rol#USUARIO}.
     *
     * <p>Las dos unicidades se comprueban por separado y con mensajes distintos
     * porque son dos campos distintos: con un mensaje generico, quien intentara
     * registrarse con un email ya usado no sabria cual de los dos choca.
     */
    @Override
    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        String username = normalizar(request.username());
        String email = normalizarEmail(request.email());

        if (usuarios.existsByUsername(username)) {
            throw new DuplicateResourceException("Ya existe una cuenta con el usuario '" + username + "'");
        }
        if (usuarios.existsByEmail(email)) {
            throw new DuplicateResourceException("Ya existe una cuenta con el email '" + email + "'");
        }

        Usuario usuario = new Usuario(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.nombre(),
                Rol.USUARIO
        );

        Usuario guardado = usuarios.save(usuario);
        log.info("Cuenta registrada: {} con rol {}", username, guardado.getRol());

        return emitirToken(guardado);
    }

    /**
     * Comprueba las credenciales y devuelve un token nuevo.
     *
     * <p>Una cuenta desactivada falla con el mismo error que una contrasena
     * equivocada: un mensaje propio ("tu cuenta esta desactivada") confirmaria
     * que el usuario existe y permitiria enumerar cuentas.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = normalizar(request.username());

        Usuario usuario = usuarios.findByUsername(username)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException();
        }

        // La comparacion va despues de comprobar que la cuenta existe y esta
        // activa. Al reves, se calcularia un BCrypt (lento a proposito) por cada
        // intento contra un usuario inexistente, que es denegacion de servicio
        // gratis para quien este probando contrasenas.
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        return emitirToken(usuario);
    }

    /**
     * Datos de la cuenta y token nuevo con el rol vigente.
     *
     * <p>El token se vuelve a firmar con lo que hay en la base, no con lo que
     * traia el token de la peticion. Es lo que hace que un ascenso surta efecto
     * sin pedirle a nadie que cierre sesion.
     *
     * <p>Una cuenta desactivada recibe 401 en vez de un token nuevo: renovar la
     * sesion seria justo lo contrario de lo que significa desactivar a alguien.
     * Mientras el token viejo siga siendo valido, esta llamada es la unica via
     * que puede cortar el acceso, porque el resto de la API no consulta la base.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse usuarioActual(Long id) {
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", id));

        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException();
        }

        return emitirToken(usuario);
    }

    /**
     * Firma el JWT con los datos minimos: identificador, nombre de sesion y rol.
     *
     * <p>No se incluyen el email ni el nombre. Un token vive horas en el cliente
     * y suele acabar en logs, en un proxy o en el historial del navegador: cuanto
     * menos lleve, menos datos personales se filtran. Para mostrarlos, el cliente
     * vuelve a llamar a {@code /api/auth/yo}.
     */
    private AuthResponse emitirToken(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant caduca = ahora.plus(expiracion);

        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(ahora)
                .expiresAt(caduca)
                .subject(usuario.getUsername())
                .claim("uid", usuario.getId())
                .claim(SecurityConfig.ROLES_CLAIM, List.of(usuario.getRol().name()))
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();

        return new AuthResponse(token, "Bearer", expiracion.toSeconds(), aResponse(usuario));
    }

    private static UsuarioResponse aResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.isActivo(),
                usuario.getCreatedAt(),
                usuario.getUpdatedAt()
        );
    }

    /**
     * Normaliza el nombre de usuario a minusculas.
     *
     * <p>Sin esto, "Ana" y "ana" serian dos cuentas distintas para el login y a la
     * vez violarian el CHECK de formato de la base: el usuario veria un 409
     * inexplicable al registrarse.
     */
    private static String normalizar(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
