package com.hispania.presentation.dto.response;

/**
 * Expresion cultural de un pais.
 *
 * <p>Es la respuesta de {@code GET /api/expresiones} y de
 * {@code GET /api/expresiones/{code}}.
 *
 * <p>El pais viaja como {@code paisCode} mas {@code paisNombre}, y no como el
 * nombre suelto que tenia el frontend ({@code 'Peru'}, {@code 'Mexico'}). El
 * frontend ya tiene la lista de paises por codigo, asi que necesita el codigo
 * para poder cruzar, y el nombre solo para pintar. Enviar el nombre sin el
 * codigo obligaria al frontend a buscar por texto, que es donde aparecen los
 * duplicados por tilde o por mayuscula.
 *
 * <p>{@code imagenUrl} ya viene con la ruta servida ({@code /expresiones/...})
 * porque es lo unico que el frontend pone en el {@code src}. El nombre de
 * fichero se queda en la base, no en el JSON.
 *
 * @param id          slug legible, el mismo que va en la URL
 * @param titulo      nombre de la expresion
 * @param categoria   disciplina, uno de los valores de {@code CategoriaExpresion}
 * @param paisCode    codigo ISO de dos letras
 * @param paisNombre  nombre del pais, para pintar la etiqueta
 * @param descText    descripcion
 * @param imagenUrl   ruta de la imagen, o {@code null} si no tiene
 * @param creditos    autor y licencia de la imagen, obligatorio si hay imagen
 */
public record ExpresionCulturalResponse(
        String id,
        String titulo,
        String categoria,
        String paisCode,
        String paisNombre,
        String descText,
        String imagenUrl,
        String creditos
) {
}
