package com.hispania.presentation.dto.response;

/**
 * Error de autenticacion: credenciales incorrectas.
 *
 * <p>Es un cuerpo propio, y no un {@link ApiError} generico, para no filtrar
 * informacion: con el mismo mensaje para "el usuario no existe" y "la contrasena
 * no es la correcta" no se puede enumerar cuentas ajenas. Esa es una de las
 * razones por las que el registro de la API es publico.
 */
public record CredencialesInvalidasResponse(
        int status,
        String error,
        String message
) {
}
