package com.hispania.exception;

/**
 * 401: las credenciales no sirven.
 *
 * <p>Se llama {@code CredencialesInvalidas} y no {@code BadCredentials} a
 * proposito: Spring Security ya tiene una
 * {@code org.springframework.security.authentication.BadCredentialsException} que
 * lanza su propia cadena de filtros, y tener dos clases con el mismo nombre
 * simple obliga a distinguirlas por el import en cualquier archivo que use las dos.
 *
 * <p>La lanza el servicio de autenticacion, no la capa web, para que el mensaje
 * no dependa de como se haya escrito la peticion.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        // Un unico mensaje para "el usuario no existe" y para "la contrasena no
        // coincide". Distinguirlos permitiria enumerar las cuentas registradas
        // probando contrasenas contra el endpoint de login, que es publico.
        super("Usuario o contrasena incorrectos");
    }
}
