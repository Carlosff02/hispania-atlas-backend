package com.hispania.presentation.dto.request;

import com.hispania.persistence.entity.CategoriaLugar;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

/**
 * Entrada para crear o actualizar un lugar.
 *
 * <p>Un solo record para las dos operaciones, con grupos de validacion: en un alta el
 * {@code id} lo elige el cliente ({@code OnCreate}), y en una modificacion lo impone
 * la ruta y el cuerpo lo ignora ({@code OnUpdate}). Duplicar el record en dos
 * classes habia que repetir ocho campos que solo cambian en esa regla.
 *
 * <p>Las anotaciones de Bean Validation se ejecutan antes de entrar al servicio, en
 * el controller con {@code @Valid}. Si falta una, el cliente recibe un 400 con el
 * detalle de todos los campos invalidos, en lugar de un 500 desde la base de datos.
 *
 * <p>La categoria se declara como el enumerado: si el cliente manda {@code "DANZAA"},
 * Jackson responde 400 antes de tocar la base de datos, en lugar de fallar contra
 * el CHECK de Postgres.
 *
 * @param id        identificador legible; obligatorio solo en el alta
 * @param name      nombre del lugar
 * @param country   codigo ISO de dos letras del pais
 * @param lat       latitud
 * @param lng       longitud
 * @param category  categoria cultural, opcional
 * @param icon      nombre del icono, opcional
 * @param period    periodo, opcional
 * @param descText  descripcion, opcional
 * @param img       URL de la imagen, opcional
 */
public record LugarRequest(

        @NotBlank(message = "El id es obligatorio", groups = OnCreate.class)
        @Pattern(regexp = "^[a-z0-9_]+$",
                 message = "El id solo admite minusculas, digitos y guion bajo",
                 groups = OnCreate.class)
        @Size(max = 50, message = "El id no puede superar los 50 caracteres",
              groups = OnCreate.class)
        String id,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String name,

        @NotBlank(message = "El codigo de pais es obligatorio")
        @Size(min = 2, max = 2, message = "El codigo de pais debe tener exactamente 2 caracteres")
        String country,

        @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
        Double lat,

        @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
        Double lng,

        CategoriaLugar category,

        @Size(max = 50, message = "El icono no puede superar los 50 caracteres")
        String icon,

        @Size(max = 50, message = "El periodo no puede superar los 50 caracteres")
        String period,

        @Size(max = 1000, message = "La descripcion no puede superar los 1000 caracteres")
        String descText,

        @Size(max = 500, message = "La URL de imagen no puede superar los 500 caracteres")
        String img
) {

    /**
     * Se valida solo en el alta, y arrastra tambien las reglas comunes.
     *
     * <p>Los grupos de Bean Validation heredan: al pedir {@code OnCreate} se
     * ejecutan tambien las anotaciones declaradas en {@code Default}, que son las de
     * {@code name}, {@code country} y las coordenadas. Si estos grupos no
     * extendieran {@code Default}, un {@code @Validated(OnCreate.class)} validaria
     * unicamente el {@code id} y dejaria pasar el resto.
     */
    public interface OnCreate extends Default {
    }

    /**
     * En una actualizacion el {@code id} lo impone la ruta y el cuerpo lo ignora, asi
     * que sus reglas quedan fuera. El resto de campos si se validan.
     */
    public interface OnUpdate extends Default {
    }
}
