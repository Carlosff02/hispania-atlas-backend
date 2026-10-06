package com.hispania.presentation.controllers;

import com.hispania.presentation.dto.request.PropuestaRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.presentation.dto.response.PropuestaResponse;
import com.hispania.services.interfaces.PropuestaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * Endpoints de {@code /api/propuestas}.
 *
 * <p>Las anotaciones {@code @PreAuthorize} son la unica fuente de verdad de los
 * permisos de este recurso. Expresan la regla con el bean {@code jerarquia}, que
 * compara rangos, en vez de enumerar roles: escribir
 * {@code hasAnyRole('COLABORADOR','ADMIN','ADMIN_SISTEMA')} obligaria a anadir el
 * nuevo rol a cada anotacion el dia que se cree.
 */
@RestController
@RequestMapping("/api/propuestas")
public class PropuestaController {

    private final PropuestaService propuestaService;

    public PropuestaController(PropuestaService propuestaService) {
        this.propuestaService = propuestaService;
    }

    /**
     * {@code POST /api/propuestas} -&gt; 201.
     *
     * <p>Para USUARIO y COLABORADOR. El ADMIN queda fuera a proposito: puede crear el
     * lugar directamente con {@code POST /api/places}, asi que proponerlo seria un
     * rodeo que ademas le impone esperar a que otra persona lo apruebe. La regla se
     * apoya en {@code puedeProponer} y no en {@code puede}, porque es la unica que
     * excluye por rango en vez de exigir un minimo.
     */
    @PostMapping
    @PreAuthorize("@jerarquia.puedeProponer(authentication)")
    public ResponseEntity<PropuestaResponse> proponer(@Valid @RequestBody PropuestaRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        PropuestaResponse creada = propuestaService.proponer(request, usuarioId(jwt));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creada.id())
                .toUri();

        return ResponseEntity.created(location).body(creada);
    }

    /**
     * {@code GET /api/propuestas/pendientes}.
     *
     * <p>La cola de moderacion. Solo para colaboradores: un USUARIO no tiene por
     * que ver las propuestas ajenas, que pueden incluir descripciones todavia sin
     * revisar.
     */
    @GetMapping("/pendientes")
    @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
    public ResponseEntity<List<PropuestaResponse>> pendientes() {
        return ResponseEntity.ok(propuestaService.listarPendientes());
    }

    /**
     * {@code GET /api/propuestas/mias}.
     *
     * <p>El identificador sale del token y no de la URL. Aceptar un
     * {@code ?usuarioId=} permitiria ver el historial de propuestas de cualquiera
     * con una llamada parecida.
     */
    @GetMapping("/mias")
    public ResponseEntity<List<PropuestaResponse>> mias(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(propuestaService.misPropuestas(usuarioId(jwt)));
    }

    /**
     * {@code PUT /api/propuestas/{id}/revision} -&gt; 200.
     *
     * <p>Es un PUT y no un PATCH porque la operacion es atomica: la propuesta pasa
     * a APROBADA o RECHAZADA en un solo paso y no existe un estado intermedio
     * guardable.
     *
     * <p>El ADMIN mantiene la capacidad de moderar aunque no pueda proponer. Si se
     * le quitara, dejaria de haber hueco cuando no hay ningun colaborador dado de
     * alta: las propuestas se acumularian sin que nadie las atendiera, y la unica
     * cuenta con permiso para crearlas seria justo la que no podria gestionarlas.
     *
     * <p>Quien puede revisar puede revisar tambien lo suyo. La excepcion vive en el
     * servicio y se aplica solo a quien no puede escribir en {@code lugares} por la
     * via directa, que es el USUARIO.
     */
    @PutMapping("/{id}/revision")
    @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
    public ResponseEntity<PropuestaResponse> revisar(@PathVariable Long id,
                                                     @Valid @RequestBody RevisionRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(propuestaService.revisar(id, request, usuarioId(jwt)));
    }

    /**
     * Identificador numerico del usuario autenticado.
     *
     * <p>Se lee del claim {@code uid}. Si el token no lo trajera, es que se emitio
     * con una version antigua del emisor, y es mejor fallar con un error claro que
     * dejar que un identificador nulo llegue hasta la base de datos.
     */
    private static Long usuarioId(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (uid == null) {
            throw new IllegalStateException("El token no contiene el identificador de usuario");
        }
        return Long.valueOf(uid.toString());
    }
}
