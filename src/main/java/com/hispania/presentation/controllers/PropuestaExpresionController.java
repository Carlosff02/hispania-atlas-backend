package com.hispania.presentation.controllers;

import com.hispania.presentation.dto.request.PropuestaExpresionRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.presentation.dto.response.PropuestaExpresionResponse;
import com.hispania.services.interfaces.PropuestaExpresionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de {@code /api/expresiones/propuestas}.
 *
 * <p>Escalado de {@code PropuestaController} sin cambiar las reglas de permiso:
 * las anotaciones {@code @PreAuthorize} siguen siendo la unica fuente de verdad y
 * siguen apoyandose en el bean {@code jerarquia}, que compara rangos, para no
 * tener que tocar cada anotacion el dia que se cree un rol nuevo.
 *
 * <p>El prefijo es {@code /api/expresiones} y no {@code /api/propuestas-expresion}
 * porque estas propuestas son la unica via de escritura de ese recurso: agruparlas
 * deja claro que las expresiones no se crean por la via directa.
 */
@RestController
@RequestMapping("/api/expresiones/propuestas")
public class PropuestaExpresionController {

    private final PropuestaExpresionService propuestaService;

    public PropuestaExpresionController(PropuestaExpresionService propuestaService) {
        this.propuestaService = propuestaService;
    }

    /**
     * {@code POST /api/expresiones/propuestas} -&gt; 201.
     *
     * <p>Para USUARIO y COLABORADOR, igual que en los lugares: quien ya puede
     * crearla por la via directa no gana nada pasando por la cola.
     */
    @PostMapping
    @PreAuthorize("@jerarquia.puedeProponer(authentication)")
    public ResponseEntity<PropuestaExpresionResponse> proponer(
            @Valid @RequestBody PropuestaExpresionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        PropuestaExpresionResponse creada = propuestaService.proponer(request, usuarioId(jwt));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creada.id())
                .toUri();

        return ResponseEntity.created(location).body(creada);
    }

    /**
     * {@code GET /api/expresiones/propuestas/pendientes}.
     *
     * <p>Solo para colaboradores, porque las descripciones pendientes todavia no
     * estan revisadas y no son material que deba ver cualquiera.
     */
    @GetMapping("/pendientes")
    @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
    public ResponseEntity<List<PropuestaExpresionResponse>> pendientes() {
        return ResponseEntity.ok(propuestaService.listarPendientes());
    }

    /**
     * {@code GET /api/expresiones/propuestas/mias}.
     *
     * <p>El identificador sale del token y no de la URL, para no dejar una forma de
     * leer el historial de propuestas de otra persona.
     */
    @GetMapping("/mias")
    public ResponseEntity<List<PropuestaExpresionResponse>> mias(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(propuestaService.misPropuestas(usuarioId(jwt)));
    }

    /**
     * {@code PUT /api/expresiones/propuestas/{id}/revision} -&gt; 200.
     *
     * <p>PUT y no PATCH porque la operacion es atomica: la propuesta pasa a APROBADA
     * o RECHAZADA en un solo paso. Al aprobar ademas se inserta la expresion, y
     * ambas cosas o ocurren o no ocurre ninguna.
     *
     * <p>El ADMIN mantiene la capacidad de moderar aunque no pueda proponer, por el
     * mismo motivo que en la cola de lugares: si no, no habria hueco para moderar
     * cuando no quedara ningun colaborador dado de alta.
     */
    @PutMapping("/{id}/revision")
    @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
    public ResponseEntity<PropuestaExpresionResponse> revisar(
            @PathVariable Long id,
            @Valid @RequestBody RevisionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(propuestaService.revisar(id, request, usuarioId(jwt)));
    }

    /**
     * Identificador numerico del usuario autenticado, leido del claim {@code uid}.
     *
     * <p>Si el token no lo trajera se habria emitido con una version antigua del
     * emisor, y es mejor fallar con un error claro que dejar que un identificador
     * nulo llegue hasta la base de datos.
     */
    private static Long usuarioId(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (uid == null) {
            throw new IllegalStateException("El token no contiene el identificador de usuario");
        }
        return Long.valueOf(uid.toString());
    }
}
