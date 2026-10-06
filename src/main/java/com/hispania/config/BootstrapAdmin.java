package com.hispania.config;

import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Crea el primer administrador del sistema si todavia no hay ninguno.
 *
 * <h2>Por que no va en una migracion</h2>
 * Una cuenta sembrada por SQL queda escrita en el historial de git para siempre, y
 * su hash es legible por cualquiera que clones el repositorio. Enseñar como
 * "contrasena de ejemplo" no cambia nada: la cuenta existe en todos los entornos y
 * en todos los despliegues futuros. Aqui, en cambio, la contrasena solo existe en
 * la variable de entorno del servidor donde se ejecuta, y si no se define, la
 * aplicacion arranca sin administrador en lugar de con uno publico.
 *
 * <h2>Por que no duplica cuentas</h2>
 * Solo se crea si no hay ningun ADMIN_SISTEMA. Es idempotente en cuanto al
 * resultado: reiniciar el servidor no crea cuentas nuevas, y borrar despues las
 * variables de arranque no deja la jerarquia vacia.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    private final String username;
    private final String email;
    private final String password;

    public BootstrapAdmin(UsuarioRepository usuarios,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap.admin-username:}") String username,
                          @Value("${app.bootstrap.admin-email:}") String email,
                          @Value("${app.bootstrap.admin-password:}") String password) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.existsByRol(Rol.ADMIN_SISTEMA)) {
            log.debug("Ya existe un ADMIN_SISTEMA; no se crea nada al arrancar");
            return;
        }

        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            log.warn("""
                    No hay ningun ADMIN_SISTEMA y tampoco las variables ADMIN_USERNAME,
                    ADMIN_EMAIL y ADMIN_PASSWORD, asi que no se ha creado ninguna cuenta de
                    administracion. Nadie podra promover a la primera cuenta: definas las tres
                    variables y reinicie, o inserte el administrador directamente en la base.
                    """);
            return;
        }

        String usernameNormalizado = username.trim().toLowerCase(Locale.ROOT);
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);

        if (usuarios.existsByUsername(usernameNormalizado)) {
            log.warn("La cuenta '{}' ya existe pero no es ADMIN_SISTEMA; no se modifica su rol "
                    + "automaticamente para no conceder permisos sin confirmacion", usernameNormalizado);
            return;
        }

        Usuario admin = new Usuario(
                usernameNormalizado,
                emailNormalizado,
                passwordEncoder.encode(password),
                "Administrador del sistema",
                Rol.ADMIN_SISTEMA
        );

        usuarios.save(admin);
        log.info("Creada la cuenta inicial '{}' con rol ADMIN_SISTEMA", usernameNormalizado);
    }
}
