package com.hispania.persistence.repository;

import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code usuarios}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca por identificador de inicio de sesion.
     *
     * <p>El servicio normaliza el nombre a minusculas antes de llamar, y el
     * CHECK de la tabla garantiza que no haya dos cuentas que solo se diferencien
     * en mayusculas.
     */
    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<Usuario> findAllByOrderByUsernameAsc();

    List<Usuario> findAllByRolOrderByUsernameAsc(Rol rol);

    /**
     * Comprueba si ya existe alguna cuenta con el rol indicado. La usa el
     * arranque de la aplicacion para no crear un segundo ADMIN_SISTEMA en cada
     * reinicio, y para no vaciar la jerarquia si se borran las variables de
     * entorno de arranque.
     */
    boolean existsByRol(Rol rol);
}
