package com.hispania.presentation.dto.request;

import com.hispania.persistence.entity.Rol;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de rol de una cuenta.
 *
 * <p>El servicio comprueba que el solicitante tenga un rango superior al de la
 * cuenta destino y que no delegue un poder que no posee. Un endpoint de
 * actualizacion completo (con email, nombre y contrasena) no se expone aqui a
 * proposito: la administracion de usuarios y la edicion de perfil son cosas
 * distintas y mezclarlas abriria la puerta a que un administrador cambiase
 * contrasenas ajenas.
 */
public record RolRequest(

        @NotNull(message = "El rol es obligatorio")
        Rol rol
) {
}
