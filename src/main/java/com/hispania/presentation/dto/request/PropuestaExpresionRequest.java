package com.hispania.presentation.dto.request;

import com.hispania.persistence.entity.CategoriaExpresion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Proposal de expresion cultural.
 *
 * <p>Coincide con los campos de {@code ExpresionCultural} salvo el {@code id}: aqui
 * no se pide identificador porque se genera al aprobar, cuando ya se puede
 * comprobar que no choca con ninguna existente.
 *
 * <p>No hay latitud, longitud, icono ni periodo, a diferencia de
 * {@code PropuestaRequest}: una expresion cultural no se visita ni tiene
 * fundacion.
 *
 * <p>{@code imagen} es un NOMBRE de fichero, no una URL. No hay endpoint de
 * subida, asi que quien proponga con imagen tiene que haber dejado el fichero en
 * {@code static/expresiones/}. Es una limitacion declarada, no un descuido.
 *
 * @param titulo    nombre de la expresion
 * @param categoria disciplina
 * @param country   codigo ISO de dos letras
 * @param descText  descripcion
 * @param imagen    nombre del fichero de imagen, opcional
 * @param creditos  autor y licencia, obligatorio si hay imagen
 */
public record PropuestaExpresionRequest(

        @NotBlank(message = "el titulo es obligatorio")
        @Size(max = 150, message = "el titulo es demasiado largo")
        String titulo,

        @NotNull(message = "la categoria es obligatoria")
        CategoriaExpresion categoria,

        @NotBlank(message = "El codigo de pais es obligatorio")
        @Size(min = 2, max = 2, message = "El codigo de pais debe tener exactamente 2 caracteres")
        String country,

        @NotBlank(message = "la descripcion es obligatoria")
        @Size(max = 1000, message = "la descripcion es demasiado larga")
        String descText,

        @Size(max = 100, message = "el nombre de la imagen es demasiado largo")
        String imagen,

        @Size(max = 300, message = "los creditos son demasiado largos")
        String creditos
) {
}
