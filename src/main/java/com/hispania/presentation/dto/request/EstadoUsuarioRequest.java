package com.hispania.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Cambio del estado de una cuenta (activar o desactivar).
 *
 * <p>No existe un endpoint para borrar usuarios: las propuestas y revisiones
 * guardan la referencia a su autor, y borrar la fila dejaria el historial huerfano
 * o impediria la operacion por la clave foranea.
 */
public record EstadoUsuarioRequest(

        @NotNull(message = "El estado es obligatorio")
        Boolean activo
) {
}
