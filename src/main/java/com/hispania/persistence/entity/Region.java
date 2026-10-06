package com.hispania.persistence.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Region geografica a la que pertenece un pais.
 *
 * <p>El nombre de la constante NO es el valor que se persiste ni el que viaja en el
 * JSON: ese es {@link #getValor()}, la cadena acentuada y con mayusculas que espera el
 * frontend ({@code "Norteamerica"} con e acentuada, {@code "Cono Sur"} con espacio).
 *
 * <p>Estan separados porque el valor no puede ser el identificador: {@code "Cono Sur"}
 * lleva espacio y no es un identificador de Java valido. Ademas las migraciones
 * (V3) sembraron los valores acentuados y con espacios, asi que el texto es el dato
 * que ya existe en la base y en el cliente; la constante es solo la version ASCII
 * que se puede escribir en Java.
 *
 * <p>La conversion en ambos sentidos la hace {@code RegionConvertidor} para JPA y
 * {@link #getValor()} para el mapper de respuesta.
 */
public enum Region {
    NORTEAMERICA("Norteamérica"),
    CENTROAMERICA("Centroamérica"),
    CARIBE("Caribe"),
    ANDINA("Andina"),
    CONO_SUR("Cono Sur");

    private final String valor;

    Region(String valor) {
        this.valor = valor;
    }

    /** Valor persistido en la columna {@code region} y devuelto en el JSON. */
    @JsonValue
    public String getValor() {
        return valor;
    }

    /**
     * Resuelve el enum desde su valor textual, que es como llega desde la base de datos
     * y desde el JSON de entrada.
     *
     * @throws IllegalArgumentException si el texto no corresponde a ninguna region
     */
    @JsonCreator
    public static Region fromValor(String valor) {
        for (Region region : values()) {
            if (region.valor.equals(valor)) {
                return region;
            }
        }
        throw new IllegalArgumentException("Region desconocida: " + valor);
    }
}
