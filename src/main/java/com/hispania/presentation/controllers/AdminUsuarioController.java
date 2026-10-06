package com.hispania.presentation.controllers;

import com.hispania.persistence.entity.Rol;
import com.hispania.presentation.dto.request.EstadoUsuarioRequest;
import com.hispania.presentation.dto.request.RolRequest;
import com.hispania.presentation.dto.response.UsuarioResponse;
import com.hispania.services.interfaces.AdminUsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de {@code /api/admin/usuarios}.
 *
 * <p>La anotacion de la clase exige el rango de ADMIN, que es la puerta al recurso.
 * Las reglas que dependen de a quien se afecta (rango superior, no delegar poder,
 * no tocar a un par) no se pueden expresar aqui y las comprueba
 * {@code AdminUsuarioServiceImpl}.
 */
@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("@jerarquia.puede(authentication, 'ADMIN')")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    public AdminUsuarioController(AdminUsuarioService adminUsuarioService) {
        this.adminUsuarioService = adminUsuarioService;
    }

    /**
     * {@code GET /api/admin/usuarios} y {@code GET /api/admin/usuarios?rol=ADMIN}.
     *
     * <p>El filtro es opcional y va en el mismo metodo que la lista completa, como
     * en {@code GET /api/places}: un endpoint aparte duplicaria el mapeo sin
     * aportar nada.
     */
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar(@RequestParam(required = false) Rol rol) {
        return ResponseEntity.ok(rol == null
                ? adminUsuarioService.listar()
                : adminUsuarioService.listarPorRol(rol));
    }

    /** {@code GET /api/admin/usuarios/{id}}. */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(adminUsuarioService.buscar(id));
    }

    /**
     * {@code PUT /api/admin/usuarios/{id}/rol} -&gt; 200 con la cuenta actualizada.
     *
     * <p>Un PUT porque el recurso "rol de la cuenta" se reemplaza entero: no tiene
     * sentido un PATCH que, aplicado dos veces, llevaria al mismo sitio.
     */
    @PutMapping("/{id}/rol")
    public ResponseEntity<UsuarioResponse> cambiarRol(@PathVariable Long id,
                                                       @Valid @RequestBody RolRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(adminUsuarioService.cambiarRol(id, request.rol(), authentication));
    }

    /**
     * {@code PATCH /api/admin/usuarios/{id}/estado} -&gt; 200.
     *
     * <p>Un PATCH y no un PUT porque el cuerpo solo lleva {@code activo}: es un
     * subconjunto de la cuenta, no la cuenta entera.
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<UsuarioResponse> cambiarEstado(@PathVariable Long id,
                                                         @Valid @RequestBody EstadoUsuarioRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(
                adminUsuarioService.cambiarActivo(id, request.activo(), authentication));
    }
}
