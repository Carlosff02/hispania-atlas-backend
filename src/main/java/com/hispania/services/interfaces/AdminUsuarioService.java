package com.hispania.services.interfaces;

import com.hispania.persistence.entity.Rol;
import com.hispania.presentation.dto.response.UsuarioResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Gestion de cuentas desde la administracion.
 *
 * <p>Las operaciones reciben la {@link Authentication} de la peticion porque la
 * regla depende de quien pregunta y de a quien afecta a la vez: "este administrador
 * no puede tocar a ese otro" no se puede expresar como un permiso sobre la ruta.
 */
public interface AdminUsuarioService {

    /** Todas las cuentas, ordenadas por nombre de usuario. */
    List<UsuarioResponse> listar();

    /** Cuentas con un rol concreto. */
    List<UsuarioResponse> listarPorRol(Rol rol);

    /** Una cuenta por identificador. */
    UsuarioResponse buscar(Long id);

    /**
     * Cambia el rol de una cuenta.
     *
     * @throws com.hispania.exception.ForbiddenException
     *         si quien pide no tiene rango superior al de la cuenta, si se intenta
     *         cambiar uno mismo, o si se delega un rol que no se tiene
     */
    UsuarioResponse cambiarRol(Long id, Rol nuevoRol, Authentication quien);

    /**
     * Activa o desactiva una cuenta. No existe el borrado: las propuestas
     * referencia a su autor.
     *
     * @throws com.hispania.exception.ForbiddenException si la jerarquia no lo permite
     */
    UsuarioResponse cambiarActivo(Long id, boolean activo, Authentication quien);
}
