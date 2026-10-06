package com.hispania.exception;

/**
 * 403: la peticion esta autenticada pero el rol no llega.
 *
 * <p>Se distingue de un 401 a proposito. El 401 dice "no se quien eres, identificate";
 * el 403 dice "se quien eres y no te vale". Mezclarlos hace que el frontend muestre
 * "sesion caducada" a un USUARIO que solo intento aprobar una propuesta.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
