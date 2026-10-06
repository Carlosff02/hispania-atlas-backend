package com.hispania.presentation.dto.response;

import com.hispania.persistence.entity.EstadoPropuesta;

import java.time.Instant;

/**
 * Proposal de expresion cultural tal como la devuelve la cola de moderacion.
 *
 * <p>Los mismos campos que la propuesta enviada, mas quien la propuso, quien la
 * reviso, cuando y con que resultado. Los nombres de los dos usuarios vienen
 * porque el moderador decide leyendo, y con el identificador numerico no puede.
 *
 * @param id           identificador de la PROPUESTA, no el de la expresion: este
 *                     ultimo no existe hasta que se aprueba
 * @param titulo       nombre propuesto
 * @param categoria    disciplina
 * @param country      codigo ISO de dos letras
 * @param descText     descripcion
 * @param imagen       nombre del fichero, o {@code null}
 * @param creditos     autor y licencia, o {@code null}
 * @param estado       resultado de la revision
 * @param autor        quien propuso
 * @param revisor      quien reviso, o {@code null} si sigue pendiente
 * @param revisadoAt   momento de la revision, o {@code null}
 * @param motivoRechazo motivo del rechazo, o {@code null}
 * @param createdAt    momento de la propuesta
 */
public record PropuestaExpresionResponse(
        Long id,
        String titulo,
        String categoria,
        String country,
        String descText,
        String imagen,
        String creditos,
        EstadoPropuesta estado,
        String autor,
        String revisor,
        Instant revisadoAt,
        String motivoRechazo,
        Instant createdAt
) {
}
