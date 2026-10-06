package com.hispania.persistence.converter;

import com.hispania.persistence.entity.Region;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link Region} con su valor textual ({@code "Norteamerica"} acentuada) en
 * vez de con el nombre de la constante.
 *
 * <p>Es necesario porque {@code @Enumerated(EnumType.STRING)} guardaria
 * {@code NORTEAMERICA}, que no es lo que escribio la migracion V3: al leer esas filas
 * Hibernate fallaba con {@code No enum constant Region.Norteamerica} y la consulta de
 * paises devolvia 500. Con este converter la base y el JSON comparten el mismo texto,
 * que es el que ya consumia el frontend.
 */
@Converter
public class RegionConvertidor implements AttributeConverter<Region, String> {

    @Override
    public String convertToDatabaseColumn(Region region) {
        return region == null ? null : region.getValor();
    }

    @Override
    public Region convertToEntityAttribute(String valor) {
        return valor == null ? null : Region.fromValor(valor);
    }
}
