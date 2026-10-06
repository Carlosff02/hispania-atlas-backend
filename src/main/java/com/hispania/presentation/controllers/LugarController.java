package com.hispania.presentation.controllers;

import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.response.LugarResponse;
import com.hispania.services.interfaces.LugarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de {@code /api/places}.
 *
 * <p>Es una copia fiel del contrato que consumia el frontend, mas un {@code PUT} y
 * los filtros de consulta. No hay logica de negocio aqui: el controller traduce HTTP
 * a llamadas del servicio y viceversa, y {@code GlobalExceptionHandler} se encarga
 * de los errores.
 */
@RestController
@RequestMapping("/api/places")
public class LugarController {

    private final LugarService lugarService;

    /**
     * Inyeccion por constructor. Es la unica forma que funciona con campos
     * {@code final}, y deja la dependencia explicita en la firma en lugar de
     * esconderla en un {@code @Autowired} sobre un campo.
     */
    public LugarController(LugarService lugarService) {
        this.lugarService = lugarService;
    }

    /**
     * {@code GET /api/places} y {@code GET /api/places?category=DANZA}.
     *
     * <p>El filtro es opcional y se declara en el mismo metodo que la lista completa:
     * cuando el parametro no viene, el repositorio devuelve todos los lugares y la
     * respuesta es identica a la de antes. Un endpoint aparte habria duplicado el
     * mapeo sin aportar nada.
     */
    @GetMapping
    public List<LugarResponse> listar(@RequestParam(required = false) CategoriaLugar category) {
        return category == null ? lugarService.listarTodos() : lugarService.listarPorCategoria(category);
    }

    /**
     * {@code GET /api/places/{id}}.
     *
     * <p>Devuelve 404 con cuerpo {@code ApiError} si no existe, en vez de un
     * {@code null} que Jackson serializaria como un 200 con cuerpo vacio.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LugarResponse> buscarPorId(@PathVariable String id) {
        LugarResponse lugar = lugarService.buscarPorId(id);
        return lugar == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(lugar);
    }

    /**
     * {@code GET /api/places/pais/{code}}.
     *
     * <p>El servicio devuelve {@code null} si el pais no existe y una lista (posiblemente
     * vacia) si existe, que es la distincion que permite responder 404 o 200.
     */
    @GetMapping("/pais/{code}")
    public ResponseEntity<List<LugarResponse>> listarPorPais(@PathVariable String code) {
        List<LugarResponse> lugares = lugarService.listarPorPais(code);
        return lugares == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(lugares);
    }

    /**
     * {@code POST /api/places} -&gt; 201 con la cabecera {@code Location}.
     *
     * <p>El grupo {@code OnCreate} exige el {@code id} en el cuerpo. En un alta el
     * cliente elige la clave, porque es una cadena legible que el frontend ya usa en
     * sus rutas.
     *
     * <p>Exige el rango de COLABORADOR. Un USUARIO no escribe directamente en
     * {@code lugares}: propone, y otro lo aprueba. Si se permitiera, la moderacion
     * seria opcional y no habria motivo para tener la cola de propuestas.
     */
    @PostMapping
    @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
    public ResponseEntity<LugarResponse> crear(
            @Validated(LugarRequest.OnCreate.class) @RequestBody LugarRequest request) {
        LugarResponse creado = lugarService.crear(request);

        // Location permite al cliente pedir el recurso recien creado sin reconstruir la URL.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();

        return ResponseEntity.created(location).body(creado);
    }

    /**
     * {@code PUT /api/places/{id}}.
     *
     * <p>El grupo {@code OnUpdate} no exige {@code id} en el cuerpo porque lo impone
     * la ruta. Si el cuerpo lo trae, se ignora: mover la clave primaria de un lugar
     * borraria la fila y crearia otra.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
    public ResponseEntity<LugarResponse> actualizar(@PathVariable String id,
                                                    @Validated(LugarRequest.OnUpdate.class)
                                                    @RequestBody LugarRequest request) {
        return ResponseEntity.ok(lugarService.actualizar(id, request));
    }

    /**
     * {@code DELETE /api/places/{id}} -&gt; 204.
     *
     * <p>Sin cuerpo: el 204 no debe llevar contenido, y devolver el objeto borrado
     * haria que el cliente creyera que sigue existiendo.
     *
     * <p>Exige ADMIN, y es el unico umbral de escritura que no coincide con el de la
     * creacion. La razon es que el borrado no se puede deshacer: {@code lugares} no
     * tiene columna de baja logica y ninguna clave foranea apunta a
     * {@code lugares.id} —las series historicas cuelgan de {@code paises}—, de modo
     * que el DELETE no dispara cascada ni error y la fila desaparece sin aviso. Crear
     * y editar se corrigen; borrar, no.
     *
     * <p>Ademas cierra un recorrido de tres pasos que con el umbral anterior era
     * posible: un COLABORADOR podia proponer un sitio, moderarlo el mismo y borrarlo,
     * y se iba un lugar curado sin que nadie mas hubiera interveneido.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@jerarquia.puede(authentication, 'ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        lugarService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
