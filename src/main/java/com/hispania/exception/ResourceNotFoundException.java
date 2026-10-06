package com.hispania.exception;

/**
 * El recurso solicitado no existe.
 *
 * <p>Se lanza desde la capa de servicios para el 404. Es de tipo
 * {@link RuntimeException} porque forzar a declararla en cada firma de la interfaz
 * llenaria de {@code throws} las interfaces de negocio, que es ruido: el
 * {@code @RestControllerAdvice} la traduce a 404 sin que el controller tenga que
 * saber nada de ella.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Atajo para el caso mas comun: buscar por clave primaria.
     *
     * <p>El identificador es {@code Object} y no {@code String} porque conviven
     * claves de texto ({@code "machu"}, el codigo ISO del pais) y numericas (el
     * id de un usuario o de una propuesta). Tiparlo como texto obligaria a
     * envolver los numeros con {@code String.valueOf()} en cada llamada, o a
     * cambiar la firma de todos los {@code throws} de las interfaces.
     *
     * @param recurso nombre del recurso, por ejemplo {@code "Pais"}
     * @param clave   valor buscado
     */
    public static ResourceNotFoundException de(String recurso, Object clave) {
        return new ResourceNotFoundException(recurso + " con identificador '" + clave + "' no encontrado");
    }
}
