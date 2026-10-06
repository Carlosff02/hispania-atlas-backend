package com.hispania.presentation.dto.response;

import java.time.Instant;

/**
 * Respuesta de un inicio de sesion o de un registro correcto.
 *
 * <p>El token es un JWT firmado por el servidor: el cliente lo envia en la
 * cabecera {@code Authorization: Bearer ...} y no necesita guardar sesion.
 *
 * <p>Incluye tambien los datos del usuario para que el frontend pueda ajustar
 * su interfaz (que botones mostrar segun el rol) sin una segunda peticion.
 *
 * @param token      JWT de acceso
 * @param tokenType  siempre {@code "Bearer"}, como espera la cabecera
 * @param expiresIn  segundos de validez restantes
 * @param usuario    datos de la cuenta autenticada
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        UsuarioResponse usuario
) {
}
