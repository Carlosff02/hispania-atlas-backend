package com.hispania.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuracion de seguridad de la API REST.
 *
 * <h2>Por que JWT y no sesion</h2>
 * El frontend es una SPA que consume la API desde otro origen (dev server en el
 * puerto 4200). Con sesion habria que activar CORS con credenciales y el servidor
 * guardaria estado por usuario. Con JWT cada peticion lleva su credencial y el
 * servidor no guarda nada.
 *
 * <h2>Division de responsabilidades</h2>
 * Esta clase decide <strong>quien esta autenticado</strong>; decide
 * <strong>que puede hacer</strong> el bean {@link Jerarquia} mediante
 * {@code @PreAuthorize}. Deliberadamente no se replica la jerarquia de roles en
 * los matchers de URL: si se hiciera, las reglas estarian en dos sitios y
 * cambiarlas exigiria tocar ambos. Aqui solo se separa lo publico del resto.
 *
 * <h2>Nota sobre el orden de los errores</h2>
 * Bean Validation se ejecuta antes que {@code @PreAuthorize}, porque la
 * validacion de los argumentos ocurre al resolver la peticion y la anotacion al
 * invocar el metodo. Un USUARIO que escriba en un endpoint de colaborador con un
 * cuerpo incompleto recibe por tanto 400 y no 403. No es un agujero: no se escribe * nada y no se filtra ningun dato del negocio, solo que la validacion gano la
 * carrera. Si molesta al cliente, la solucion es declarar el rol en los matchers
 * de esta clase, asumiendo entonces la duplicacion de la jerarquia.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Claim del token que lleva los roles, sin el prefijo {@code ROLE_}. */
    public static final String ROLES_CLAIM = "roles";

    private final JwtAuthenticationConverter jwtAuthenticationConverter = crearJwtAuthenticationConverter();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveSecreta()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(claveSecreta()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Coste por defecto (10). Es lento a proposito: encarece los ataques por
        // fuerza bruta si alguien roba la tabla de hashes.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/countries/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/places/**").permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/registro"
                        ).permitAll()

                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * Convierte el claim {@code "roles": ["ADMIN"]} en la autoridad
     * {@code ROLE_ADMIN}, que es lo que entienden {@code @PreAuthorize} y
     * {@code hasRole}. El prefijo se añade aqui y no al emitir el token, para no
     * duplicarlo en dos sitios.
     */
    private static JwtAuthenticationConverter crearJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private SecretKey claveSecreta() {
        // HMAC-SHA256 con una clave simetrica compartida. Para una API interna
        // es suficiente y evita el par de claves asimetricas de RSA.
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret debe tener al menos 32 bytes para HS256; tiene " + bytes.length
                            + ". Genera una con: openssl rand -base64 48");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
