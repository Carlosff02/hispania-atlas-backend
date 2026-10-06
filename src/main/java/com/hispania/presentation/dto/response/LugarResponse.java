package com.hispania.presentation.dto.response;

/**
 * Lugar cultural o turistico, en la forma que espera el frontend.
 *
 * <p>El campo {@code category} se serializa como el nombre del enumerado
 * ({@code "DANZA"}), no como su ordinal, porque asi lo espera el filtro de la vista
 * Cultura. La entidad lo declara con {@code @Enumerated(EnumType.STRING)}, de modo que
 * Jackson escribe la cadena directamente.
 *
 * @param id        identificador legible, usado como clave en el frontend
 * @param name      nombre del lugar
 * @param country   codigo ISO de dos letras del pais
 * @param lat       latitud
 * @param lng       longitud
 * @param category  categoria cultural
 * @param icon      nombre del icono de Lucide
 * @param period    periodo o fecha aproximada
 * @param descText  descripcion larga
 * @param img       URL de la imagen
 */
public record LugarResponse(
        String id,
        String name,
        String country,
        double lat,
        double lng,
        String category,
        String icon,
        String period,
        String descText,
        String img
) {
}
