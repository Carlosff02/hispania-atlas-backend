package com.hispania.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decision del moderador sobre una propuesta.
 *
 * <p>Un solo record para aprobar y para rechazar. El motivo solo se exige cuando
 * el estado es {@code RECHAZADA}, y esa condicion la comprueba el servicio, que
 * es quien decide si el dato es coherente. El CHECK de la base de datos la
 * vuelve a exigir por si el servicio llegara a saltarsela.
 */
public record RevisionRequest(

        @NotNull(message = "El estado es obligatorio: PENDIENTE, APROBADA o RECHAZADA")
        EstadoPropuestaRequest estado,

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo
) {
}
