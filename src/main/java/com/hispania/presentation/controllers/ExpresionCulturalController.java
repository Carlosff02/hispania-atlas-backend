package com.hispania.presentation.controllers;

import com.hispania.presentation.dto.response.ExpresionCulturalResponse;
import com.hispania.services.interfaces.ExpresionCulturalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de {@code /api/expresiones}.
 *
 * <p>Sustituyen a la constante {@code art-data.ts} del frontend. Antes de esto
 * la seccion no tenia backend: cuatro entradas escritas a mano en el TypeScript,
 * con el pais como texto suelto y las imagenes enlazadas a un buscador.
 *
 * <p>Solo hay lectura. Crear una expresion pasa por aprobar una propuesta, igual
 * que un lugar, y ese endpoint vive en {@code PropuestaController}. No es
 * adorno: cualquiera puede proponer una tradicion mal atribuida a un pais, y un
 * {@code POST} abierto no tendria quien lo revise.
 */
@RestController
@RequestMapping("/api/expresiones")
public class ExpresionCulturalController {

    private final ExpresionCulturalService expresionService;

    public ExpresionCulturalController(ExpresionCulturalService expresionService) {
        this.expresionService = expresionService;
    }

    /**
     * {@code GET /api/expresiones}.
     *
     * <p>Lista vacia si no hay ninguna, no 404: el frontend la recorre y una
     * coleccion vacia es un estado valido de la seccion.
     *
     * <p>Sin {@code @PreAuthorize}, igual que {@code GET /api/countries}: es
     * contenido publico del atlas.
     */
    @GetMapping
    public List<ExpresionCulturalResponse> listar() {
        return expresionService.listarTodas();
    }

    /**
     * {@code GET /api/expresiones/{code}} -&gt; 200 con la lista, o 404 si el pais
     * no existe.
     *
     * <p>Un pais que existe pero no tiene expresiones devuelve 200 con lista
     * vacia. Ahora mismo son 15 de los 19, asi que el 404 tiene que quedar
     * reservado al pais inexistente o la ficha del mapa dejaria de funcionar
     * para casi todos.
     */
    @GetMapping("/{code}")
    public ResponseEntity<List<ExpresionCulturalResponse>> listarPorPais(@PathVariable String code) {
        List<ExpresionCulturalResponse> expresiones = expresionService.listarPorPais(code);
        return expresiones == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(expresiones);
    }
}
