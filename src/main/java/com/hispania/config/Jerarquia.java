package com.hispania.config;

import com.hispania.persistence.entity.Rol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Evalua la jerarquia de roles.
 *
 * <p>Existe como bean para poder invocarlo desde {@code @PreAuthorize} con
 * SpEL, por ejemplo:
 *
 * <pre>{@code
 * @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
 * }</pre>
 *
 * <p>La alternativa seria repetir la comparacion de rangos en cada anotacion
 * ({@code hasAnyRole('COLABORADOR', 'ADMIN', 'ADMIN_SISTEMA')}), que obliga a
 * enumerate todos los roles superiores cada vez que se añade uno. Como el
 * conjunto de permisos es lineal, comparar rangos no admite olvidos.
 *
 * <p>Con la salvedad de que <strong>no todas las reglas son umbrales</strong>.
 * Comparar rangos resuelve "al menos este poder", que es lo que casi todas quieren,
 * pero la de proponer va al reves: nadie por encima de colaborador puede proponer.
 * Esa lleva metodo propio, {@link #puedeProponer(Authentication)}, y no se fuerza
 * dentro de {@link #puede} porque el signo correcto depende de la regla.
 */
@Component("jerarquia")
public class Jerarquia {

    /** El nombre con el que se referencia el bean desde las anotaciones. */
    public static final String BEAN = "jerarquia";

    /**
     * Rol minimo necesario para la operacion.
     *
     * @param authentication autenticacion actual; {@code null} si no hay sesion
     * @param rolMinimo      nombre del rol exigido, por ejemplo {@code "ADMIN"}
     * @return {@code true} si el usuario tiene ese rol o uno superior
     */
    public boolean puede(Authentication authentication, String rolMinimo) {
        Rol actual = rolActual(authentication);
        if (actual == null) {
            return false;
        }
        return actual.incluye(Rol.valueOf(rolMinimo));
    }

    /**
     * Indica si el usuario actual puede proponer un lugar.
     *
     * <p>Es la unica regla del modulo que <strong>excluye</strong> en lugar de
     * incluir, y por eso no se expresa con {@link #puede}: {@code puede} siempre es
     * cierto al subir de rango, y aqui ocurre justo lo contrario. Un ADMIN no propone
     * porque es el rol que mas puede, no el que menos.
     *
     * <p>La logica es la de toda la cola de propuestas: es un rodeo para quien ya
     * puede escribir en {@code lugares} por la via directa. Si el admin puede crear el
     * lugar con un POST, esperar a que alguien se lo apruebe no le aporta nada.
     *
     * <p>Por eso lleva metodo propio. Reutilizar {@code puede} con el ADMIN como
     * minimo permitiria justo lo contrario de lo que se busca, y ese error es facil de
     * cometer porque el patron de las demas anotaciones es siempre "minimo exigido".
     */
    public boolean puedeProponer(Authentication authentication) {
        Rol actual = rolActual(authentication);
        if (actual == null) {
            return false;
        }
        return actual.noSupera(Rol.COLABORADOR);
    }

    /**
     * Indica si el usuario actual puede modificar la cuenta indicada.
     *
     * <p>Regla: hay que tener un rango <strong>estrictamente superior</strong> al
     * de la cuenta destino. Dos consecuencias, y las dos son intencionadas:
     *
     * <ul>
     *   <li>Nadie puede cambiarse a si mismo un rol, ni degradarse. Habria que
     *       hacerlo en dos pasos, y el paso intermedio se queda sin permisos.</li>
     *   <li>Dos ADMIN no pueden modificarse entre si, aunque en principio
     *       podrian. Asi un administrador comprometido no puede quitarle el rol a
     *       otro ni degradarlo para cubrirse.</li>
     * </ul>
     *
     * @param authentication autenticacion del que intenta el cambio
     * @param rolObjetivo    rol actual de la cuenta a modificar
     */
    public boolean puedeModificar(Authentication authentication, Rol rolObjetivo) {
        Rol actual = rolActual(authentication);
        if (actual == null) {
            return false;
        }
        return actual.supera(rolObjetivo);
    }

    /**
     * Indica si el usuario actual puede asignar el rol indicado a una cuenta.
     *
     * <p>Regla: no se puede delegar un poder que uno mismo no tiene. Sin esto,
     * un ADMIN podria promover a ADMIN_SISTEMA y obtener el control total, y
     * ningun ADMIN_SISTEMA seria imprescindible.
     */
    public boolean puedeAsignar(Authentication authentication, Rol rolNuevo) {
        Rol actual = rolActual(authentication);
        if (actual == null) {
            return false;
        }
        return actual.incluye(rolNuevo);
    }

    /**
     * Rol del usuario autenticado, o {@code null} si no hay ninguno.
     *
     * <p>Se lee del claim {@code roles} del token a traves de la autoridad
     * {@code ROLE_x} que genera {@link SecurityConfig}, y no de la base de
     * datos. Es coherente con el resto de la peticion: si un rol cambia, el
     * token deja de valer y el usuario tiene que volver a entrar.
     */
    public Rol rolActual(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        for (var authority : authentication.getAuthorities()) {
            String nombre = authority.getAuthority();
            if (nombre.startsWith("ROLE_")) {
                try {
                    return Rol.valueOf(nombre.substring("ROLE_".length()));
                } catch (IllegalArgumentException ex) {
                    // Rol que ya no existe en el enum. Se ignora en vez de fallar:
                    // un token emitido por una version anterior no debe tumbar
                    // la API, simplemente no concede ningun permiso.
                    return null;
                }
            }
        }
        return null;
    }

    /** Rol del usuario de la peticion en curso, leido del contexto de seguridad. */
    public Rol rolActual() {
        return rolActual(SecurityContextHolder.getContext().getAuthentication());
    }
}
