package com.hispania.services.interfaces;

import com.hispania.presentation.dto.request.LoginRequest;
import com.hispania.presentation.dto.request.RegistroRequest;
import com.hispania.presentation.dto.response.AuthResponse;

/**
 * Alta de cuentas, inicio de sesion y consulta del perfil propio.
 */
public interface AuthService {

    /** Registra una cuenta nueva con rol USUARIO y devuelve su token. */
    AuthResponse registrar(RegistroRequest request);

    /** Comprueba las credenciales y devuelve un token nuevo. */
    AuthResponse login(LoginRequest request);

    /**
     * Datos de la cuenta a la que pertenece el token de la peticion, junto a un
     * token nuevo emitido con el rol que figura ahora en la base.
     *
     * <p>Devolver el token es lo que cierra el ascenso de usuarios: el rol viaja
     * dentro del JWT, así que sin renovarlo una persona promovida conserva el rol
     * anterior hasta que caduca, y su menú ofrece opciones que el servidor responde
     * con 403. Como el token se firma con los datos de la base en cada llamada,
     * revalidar la sesión es también la vía por la que una desactivación surte
     * efecto sin esperar a la caducidad.
     *
     * @throws com.hispania.exception.CredencialesInvalidasException si la cuenta
     *         está desactivada; no se renueva el token de una cuenta que ya no
     *         debería operar
     */
    AuthResponse usuarioActual(Long id);
}
