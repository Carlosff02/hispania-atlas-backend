package com.hispania.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de inicio de sesion.
 */
public record LoginRequest(

        @NotBlank(message = "el usuario es obligatorio")
        String username,

        @NotBlank(message = "la contrasena es obligatoria")
        String password
) {
}
