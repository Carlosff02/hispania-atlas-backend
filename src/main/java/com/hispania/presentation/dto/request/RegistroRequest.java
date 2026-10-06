package com.hispania.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de alta de una cuenta nueva.
 *
 * <p>No hay campo {@code rol}: el registro es publico y toda cuenta nace en
 * {@code USUARIO}. Aceptar un rol aqui seria la forma de que cualquiera se
 * autoproclamara administrador.
 */
public record RegistroRequest(

        @NotBlank(message = "el usuario es obligatorio")
        @Size(min = 3, max = 30, message = "el usuario debe tener entre 3 y 30 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$",
                message = "el usuario solo admite letras, numeros y guion bajo")
        String username,

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato valido")
        @Size(max = 150, message = "el email es demasiado largo")
        String email,

        @NotBlank(message = "la contrasena es obligatoria")
        @Size(min = 8, max = 72, message = "la contrasena debe tener entre 8 y 72 caracteres")
        String password,

        @Size(max = 100, message = "el nombre es demasiado largo")
        String nombre
) {
}
