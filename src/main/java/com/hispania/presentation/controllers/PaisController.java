package com.hispania.presentation.controllers;

import com.hispania.presentation.dto.request.PaisRequest;
import com.hispania.presentation.dto.response.PaisResponse;
import com.hispania.services.interfaces.PaisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de {@code /api/countries}.
 *
 * <p>El frontend consume sobre todo el listado: al pintar el mapa necesita todos los
 * paises con su serie y sus lugares en una sola peticion, y no uno por ficha.
 * {@code GET /api/countries/{code}} se anadio ademas porque
 * {@code CountriesService.fetchCountryByCode} lo llama y el backend de Quarkus no lo
 * tenia implementado, asi que siempre caia en el respaldo local.
 */
@RestController
@RequestMapping("/api/countries")
public class PaisController {

    private final PaisService paisService;

    public PaisController(PaisService paisService) {
        this.paisService = paisService;
    }

    /**
     * {@code GET /api/countries}.
     *
     * <p>Devuelve una lista vacia si no hay datos, no un 404: el frontend la recorre
     * y una tabla vacia es un estado valido.
     */
    @GetMapping
    public List<PaisResponse> listar() {
        return paisService.listarTodos();
    }

    /** {@code GET /api/countries/{code}} -&gt; 200 o 404. */
    @GetMapping("/{code}")
    public ResponseEntity<PaisResponse> buscarPorCodigo(@PathVariable String code) {
        PaisResponse pais = paisService.buscarPorCodigo(code);
        return pais == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(pais);
    }

    /**
     * {@code POST /api/countries} -&gt; 201 con la cabecera {@code Location}.
     *
     * <p>Si el codigo ya existe, el servicio lanza {@code DuplicateResourceException} y
     * el manejador global lo traduce a 409, no a 500.
     *
     * <p>Exige el rango de ADMIN, un nivel por encima del de los lugares. Anadir un
     * pais es una decision estructural del proyecto y arrastra su serie historica;
     * no le corresponde a un colaborador.
     */
    @PostMapping
    @PreAuthorize("@jerarquia.puede(authentication, 'ADMIN')")
    public ResponseEntity<PaisResponse> crear(
            @Validated(PaisRequest.OnCreate.class) @RequestBody PaisRequest request) {
        PaisResponse creado = paisService.crear(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(creado.code())
                .toUri();

        return ResponseEntity.created(location).body(creado);
    }

    /** {@code PUT /api/countries/{code}}. El {@code code} lo impone la ruta. */
    @PutMapping("/{code}")
    @PreAuthorize("@jerarquia.puede(authentication, 'ADMIN')")
    public ResponseEntity<PaisResponse> actualizar(@PathVariable String code,
                                                   @Validated(PaisRequest.OnUpdate.class)
                                                   @RequestBody PaisRequest request) {
        return ResponseEntity.ok(paisService.actualizar(code, request));
    }

    /**
     * {@code DELETE /api/countries/{code}} -&gt; 204.
     *
     * <p>Elimina tambien sus lugares y su serie historica. Es una operacion
     * destructiva en cascada, pero el esquema lo define asi: los lugares no tienen
     * sentido sin su pais.
     */
    @DeleteMapping("/{code}")
    @PreAuthorize("@jerarquia.puede(authentication, 'ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable String code) {
        paisService.eliminar(code);
        return ResponseEntity.noContent().build();
    }
}
