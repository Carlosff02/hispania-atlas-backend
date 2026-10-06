package com.hispania.presentation.dto.response;

import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.EstadoPropuesta;

import java.time.Instant;

/**
 * Propuesta de lugar expuesta al cliente.
 *
 * <p>Los autores se devuelven como identificadores y nombres sueltos en vez de
 * como objetos: el cliente solo necesita pintar "propuesto por Ana" y no
 * arrastrar la relacion de JPA al JSON.
 *
 * @param id             identificador de la propuesta
 * @param nombre         nombre propuesto
 * @param country        codigo ISO del pais
 * @param lat            latitud
 * @param lng            longitud
 * @param category       categoria cultural
 * @param icon           icono, opcional
 * @param period         periodo, opcional
 * @param descText       descripcion, opcional
 * @param img            URL de imagen, opcional
 * @param estado         estado de la propuesta
 * @param propuestoPorId id del autor
 * @param propuestoPor   nombre de inicio de sesion del autor
 * @param revisadoPor    nombre de quien la reviso, o {@code null} si sigue pendiente
 * @param revisadoAt     fecha de la revision, o {@code null}
 * @param motivoRechazo  motivo, si fue rechazada
 * @param createdAt      fecha de la propuesta
 */
public record PropuestaResponse(
        Long id,
        String nombre,
        String country,
        double lat,
        double lng,
        CategoriaLugar category,
        String icon,
        String period,
        String descText,
        String img,
        EstadoPropuesta estado,
        Long propuestoPorId,
        String propuestoPor,
        String revisadoPor,
        Instant revisadoAt,
        String motivoRechazo,
        Instant createdAt
) {
}
