package com.hispania.presentation.dto.request;

import java.util.Locale;

/**
 * Estado que puede enviar el moderador en una revision.
 *
 * <p>Es un enum propio y no el {@code EstadoPropuesta} de la entidad a proposito:
 * la entidad podria anadir estados internos (por ejemplo {@code FUSIONADA}) que
 * un cliente no deberia poder enviar. Aceptar solo {@code APROBADA} y
 * {@code RECHAZADA} deja el contrato de la API bajo control de la API.
 */
public enum EstadoPropuestaRequest {

    APROBADA,
    RECHAZADA;

    /** Normaliza el texto que llega del cliente a mayusculas. */
    public static EstadoPropuestaRequest de(String texto) {
        if (texto == null) {
            return null;
        }
        return valueOf(texto.trim().toUpperCase(Locale.ROOT));
    }
}
