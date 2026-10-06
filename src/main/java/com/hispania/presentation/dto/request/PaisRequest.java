package com.hispania.presentation.dto.request;

import com.hispania.persistence.entity.Region;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

/**
 * Entrada para crear o actualizar un pais.
 *
 * <p>Mismo esquema de grupos que {@link LugarRequest}: el {@code code} es obligatorio
 * en el alta y lo impone la ruta en la modificacion.
 *
 * @param code     codigo ISO de dos letras en mayusculas
 * @param name     nombre del pais
 * @param capital  capital
 * @param lat      latitud
 * @param lng      longitud
 * @param region   region geografica
 * @param descText descripcion, opcional
 */
public record PaisRequest(

        @NotBlank(message = "El codigo es obligatorio", groups = OnCreate.class)
        @Pattern(regexp = "^[A-Z]{2}$",
                 message = "El codigo debe ser exactamente 2 letras en mayusculas",
                 groups = OnCreate.class)
        String code,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name,

        @NotBlank(message = "La capital es obligatoria")
        @Size(max = 100, message = "La capital no puede superar los 100 caracteres")
        String capital,

        @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
        Double lat,

        @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
        Double lng,

        @NotNull(message = "La region es obligatoria")
        Region region,

        @Size(max = 1000, message = "La descripcion no puede superar los 1000 caracteres")
        String descText
) {

    /** Ver {@link LugarRequest.OnCreate} para por que los grupos extienden {@code Default}. */
    public interface OnCreate extends Default {
    }

    /** El {@code code} lo decide la ruta al actualizar. */
    public interface OnUpdate extends Default {
    }
}
