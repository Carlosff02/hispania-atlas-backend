package com.hispania.services.impl;

import com.hispania.config.Jerarquia;
import com.hispania.exception.ForbiddenException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.response.UsuarioResponse;
import com.hispania.services.interfaces.AdminUsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion de cuentas: cambio de rol y activacion o desactivacion.
 *
 * <h2>Las tres reglas</h2>
 * <ol>
 *   <li><strong>Rango superior.</strong> Solo se puede modificar a quien tiene un
 *       rango menor. Por eso dos administradores no pueden tocarse entre si.</li>
 *   <li><strong>No delegar poder que no se tiene.</strong> Un ADMIN no puede
 *       promover a ADMIN_SISTEMA. Sin esta regla, el sistema no tendria ningun
 *       administrador del sistema y bastaria con comprometer una cuenta intermedia.</li>
 *   <li><strong>Nadie se modifica a si mismo.</strong> Evita que un administrador
 *       se degrade por error, o que se quede sin permisos sin querer.</li>
 * </ol>
 *
 * <p>Las tres se comprueban aqui y no solo con {@code @PreAuthorize}. La anotacion
 * puede exigir "se ADMIN" a la entrada del endpoint, pero no puede expresar con
 * claridad "este ADMIN concreto no puede tocar a este otro ADMIN concreto", que es
 * una relacion entre dos datos y no un permiso sobre la ruta.
 */
@Service
public class AdminUsuarioServiceImpl implements AdminUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(AdminUsuarioServiceImpl.class);

    private final UsuarioRepository usuarios;
    private final Jerarquia jerarquia;

    public AdminUsuarioServiceImpl(UsuarioRepository usuarios, Jerarquia jerarquia) {
        this.usuarios = usuarios;
        this.jerarquia = jerarquia;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarios.findAllByOrderByUsernameAsc().stream()
                .map(AdminUsuarioServiceImpl::aResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarPorRol(Rol rol) {
        return usuarios.findAllByRolOrderByUsernameAsc(rol).stream()
                .map(AdminUsuarioServiceImpl::aResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Long id) {
        return aResponse(usuarios.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", id)));
    }

    /**
     * Cambia el rol de una cuenta.
     *
     * <p>Un cambio a un rol superior (una promocion) exige ADMIN_SISTEMA, no solo
     * ADMIN, y la comprobacion se hace de forma explicita en lugar de fiarse del
     * {@code @PreAuthorize} de la ruta: el endpoint lo comparte todo ADMIN, y la
     * diferencia entre "crear un colaborador" y "crear otro administrador del
     * sistema" solo se puede decidir mirando los dos roles a la vez.
     */
    @Override
    @Transactional
    public UsuarioResponse cambiarRol(Long id, Rol nuevoRol, Authentication quien) {
        exigirRangoAdmin(quien);

        Usuario objetivo = usuarios.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", id));

        Rol actualQuien = jerarquia.rolActual(quien);
        if (actualQuien == null) {
            throw new ForbiddenException("No hay una sesion valida para esta operacion");
        }

        if (objetivo.getId().equals(usuarioIdDe(quien))) {
            throw new ForbiddenException("No puedes cambiarte a ti mismo el rol");
        }

        if (!jerarquia.puedeModificar(quien, objetivo.getRol())) {
            throw new ForbiddenException("Necesitas un rango superior al de '" + objetivo.getUsername()
                    + "' (" + objetivo.getRol() + ") para cambiarlo");
        }

        if (!jerarquia.puedeAsignar(quien, nuevoRol)) {
            throw new ForbiddenException("No puedes asignar el rol " + nuevoRol
                    + " porque no lo tienes: delegarias un poder que no posees");
        }

        // Una promocion por encima de ADMIN cambia la naturaleza de la cuenta, no
        // solo sus permisos de edicion. Se limita a ADMIN_SISTEMA de forma explicita
        // para que la jerarquia no dependa solo del rango: "ADMIN" podria crecer
        // en permisos en el futuro y abrir esta puerta sin que nadie lo decidiera.
        if (nuevoRol.incluye(Rol.ADMIN_SISTEMA) && !actualQuien.incluye(Rol.ADMIN_SISTEMA)) {
            throw new ForbiddenException("Solo un ADMIN_SISTEMA puede crear o degradar administradores del sistema");
        }

        if (objetivo.getRol() == nuevoRol) {
            // No es un error: hace que la interfaz pueda reenviar el formulario sin
            // tratar el caso aparte, y evita ensuciar el log con cambios que no
            // cambian nada.
            return aResponse(objetivo);
        }

        Rol anterior = objetivo.getRol();
        objetivo.setRol(nuevoRol);
        Usuario guardada = usuarios.save(objetivo);

        log.info("Rol de {} cambiado de {} a {} por {}",
                guardada.getUsername(), anterior, nuevoRol, quien.getName());

        return aResponse(guardada);
    }

    /**
     * Activa o desactiva una cuenta.
     *
     * <p>Desactivar no borra nada: las propuestas y revisiones guardan la referencia
     * a su autor, y la FK de la base impediria el borrado.
     */
    @Override
    @Transactional
    public UsuarioResponse cambiarActivo(Long id, boolean activo, Authentication quien) {
        exigirRangoAdmin(quien);

        Usuario objetivo = usuarios.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", id));

        if (objetivo.getId().equals(usuarioIdDe(quien))) {
            throw new ForbiddenException("No puedes desactivar tu propia cuenta");
        }

        if (!jerarquia.puedeModificar(quien, objetivo.getRol())) {
            throw new ForbiddenException("Necesitas un rango superior al de '" + objetivo.getUsername()
                    + "' (" + objetivo.getRol() + ") para cambiar su estado");
        }

        // Desactivar a otro ADMIN_SISTEMA desde una cuenta ADMIN deja el sistema sin
        // un administrador del sistema valido. Se exige el rango mas alto para
        // deshabilitar el nivel mas alto.
        if (!activo && objetivo.getRol().incluye(Rol.ADMIN_SISTEMA)
                && !jerarquia.rolActual(quien).incluye(Rol.ADMIN_SISTEMA)) {
            throw new ForbiddenException(
                    "Solo un ADMIN_SISTEMA puede desactivar a otro administrador del sistema");
        }

        if (objetivo.isActivo() == activo) {
            return aResponse(objetivo);
        }

        objetivo.setActivo(activo);
        Usuario guardado = usuarios.save(objetivo);

        log.info("Cuenta {} {} por {}",
                guardado.getUsername(), activo ? "activada" : "desactivada", quien.getName());

        return aResponse(guardado);
    }

    /**
     * Exige el rango de ADMIN para cualquier operacion de escritura.
     *
     * <p>La anotacion {@code @PreAuthorize} del controller ya lo bloquea, pero se
     * repite aqui de forma explicita. La razon es que las reglas siguientes
     * comparan rangos entre dos cuentas, y un COLABORADOR que llegase hasta aqui
     * superaria el rango de un USUARIO: la comprobacion de "rango superior" sola
     * no detiene la escalada. Depender unicamente de la anotacion haria que la
     * seguridad de toda la administracion colgara de que nadie invoque el servicio
     * desde otro sitio.
     */
    private void exigirRangoAdmin(Authentication quien) {
        if (!jerarquia.puede(quien, Rol.ADMIN.name())) {
            Rol actual = jerarquia.rolActual(quien);
            throw new ForbiddenException("Esta operacion requiere el rango ADMIN"
                    + (actual == null ? "" : "; tu rol es " + actual));
        }
    }

    /**
     * Identificador del usuario autenticado.
     *
     * <p>Se lee del claim {@code uid} que se metio al emitir el token. Se comparan
     * los identificadores numericos y no los nombres de sesion para que un cambio de
     * nombre no altere las comparaciones.
     */
    private Long usuarioIdDe(Authentication quien) {
        Object uid = quien.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                ? jwt.getClaim("uid")
                : null;

        if (uid == null) {
            return null;
        }
        return Long.valueOf(uid.toString());
    }

    private static UsuarioResponse aResponse(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getNombre(),
                u.getRol(),
                u.isActivo(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
