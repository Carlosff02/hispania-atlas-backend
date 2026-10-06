package com.hispania.persistence.entity;

/**
 * Estado del flujo de moderacion de una propuesta de lugar.
 *
 * <p>Solo {@link #PENDIENTE} aparece en la cola de trabajo: en cuanto un
 * colaborador decide, la propuesta sale de la cola para siempre. No hay estado
 * "reabierta" porque reabrirla equivaldria a que el usuario propusiera otra vez
 * lo mismo.
 */
public enum EstadoPropuesta {

    /** Propuesta esperando revision. Es el unico estado abierto. */
    PENDIENTE,

    /** Aceptada: ya existe una fila equivalente en la tabla {@code lugares}. */
    APROBADA,

    /** Rechazada. Siempre lleva motivo, por CHECK en la base de datos. */
    RECHAZADA;

    public boolean estaAbierta() {
        return this == PENDIENTE;
    }
}
