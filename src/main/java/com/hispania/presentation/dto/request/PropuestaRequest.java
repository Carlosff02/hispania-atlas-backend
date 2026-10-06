package com.hispania.presentation.dto.request;

import com.hispania.persistence.entity.CategoriaLugar;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Propuesta de un lugar nuevo.
 *
 * <p>Coincide con los campos de {@code LugarRequest} salvo el {@code id}: aqui
 * no se pide identificador porque se genera al aprobar. Asi el frontend puede
 * reutilizar casi el mismo formulario de alta para proponer y para crear.
 */
public record PropuestaRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150, message = "el nombre es demasiado largo")
        String nombre,

        @NotBlank(message = "El codigo de pais es obligatorio")
        @Size(min = 2, max = 2, message = "El codigo de pais debe tener exactamente 2 caracteres")
        String country,

        @NotNull(message = "la latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "la latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "la latitud debe estar entre -90 y 90")
        Double lat,

        @NotNull(message = "la longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "la longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "la longitud debe estar entre -180 y 180")
        Double lng,

        @NotNull(message = "la categoria es obligatoria")
        CategoriaLugar category,

        @Size(max = 50, message = "el icono es demasiado largo")
        String icon,

        @Size(max = 50, message = "el periodo es demasiado largo")
        String period,

        @Size(max = 1000, message = "la descripcion es demasiado larga")
        String descText,

        @Size(max = 500, message = "la URL de la imagen es demasiado larga")
        String img
) {
}
