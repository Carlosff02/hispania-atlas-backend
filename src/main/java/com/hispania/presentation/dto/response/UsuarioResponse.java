package com.hispania.presentation.dto.response;

import com.hispania.persistence.entity.Rol;

import java.time.Instant;

/**
 * Datos publicos de una cuenta.
 *
 * <p>Nunca incluye el hash de la contrasena. Es un record separado de la entidad
 * a proposito: serializar la entidad directamente publicaria
 * {@code passwordHash} en cuanto se anadiera un campo nuevo, y es el fallo clasico
 * al exponer JPA en la API.
 *
 * @param id        identificador interno
 * @param username  identificador de inicio de sesion
 * @param email     correo
 * @param nombre    nombre visible, opcional
 * @param rol       rol actual
 * @param activo    si la cuenta puede autenticarse
 * @param createdAt fecha de alta
 * @param updatedAt fecha de la ultima modificacion
 */
public record UsuarioResponse(
        Long id,
        String username,
        String email,
        String nombre,
        Rol rol,
        boolean activo,
        Instant createdAt,
        Instant updatedAt
) {
}
