package com.hispania.presentation.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo de error uniforme para toda la API.
 *
 * <p>Spring devuelve tres formatos distintos segun donde falle la peticion: uno
 * para el 404 de DispatcherServlet, otro para un {@code @ExceptionHandler} propio y
 * otro para los errores de validacion. El frontend tendria que parsing los tres.
 * Este record los unifica.
 *
 * @param timestamp momento de la respuesta
 * @param status    codigo HTTP
 * @param error     nombre de la condicion, por ejemplo {@code "Not Found"}
 * @param message   descripcion legible
 * @param path      ruta que fallo
 * @param details   detalle campo a campo; vacio salvo en errores de validacion
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> details
) {

    /**
     * Un campo concreto que no paso la validacion.
     *
     * @param field   nombre del campo
     * @param message motivo del rechazo
     */
    public record FieldViolation(String field, String message) {
    }
}
