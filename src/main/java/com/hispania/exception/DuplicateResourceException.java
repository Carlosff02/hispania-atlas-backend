package com.hispania.exception;

/**
 * El recurso ya existe y la operacion lo crearia de nuevo.
 *
 * <p>Se distingue del 400 generico porque el arreglo es distinto: en un 400 el
 * cliente debe corregir el valor enviado, pero en un 409 tiene que elegir otro
 * identificador. La API de Quarkus no tenia forma de expresar ese caso, y acababa
 * devolviendo un 500 desde la restriccion de clave primaria de Postgres.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    /** Atajo para el conflicto de clave primaria. */
    public static DuplicateResourceException de(String recurso, String clave) {
        return new DuplicateResourceException(recurso + " con identificador '" + clave + "' ya existe");
    }
}
