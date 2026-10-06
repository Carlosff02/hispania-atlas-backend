package com.hispania.services.impl;

import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.CategoriaExpresion;
import com.hispania.persistence.entity.ExpresionCultural;
import com.hispania.persistence.entity.Pais;
import com.hispania.persistence.repository.ExpresionCulturalRepository;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.presentation.dto.response.ExpresionCulturalResponse;
import com.hispania.services.interfaces.ExpresionCulturalService;
import com.hispania.services.mapper.ResponseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementacion de la lectura de expresiones culturales.
 *
 * <p>No cachea nada ni junta repartos: son cuatro filas hoy y la consulta ya
 * trae el pais con {@code JOIN FETCH}. El dia que haya una expresion por pais y
 * categoria serian como mucho 171, y sigue siendo una tabla que entra entera en
 * memoria sin problema.
 */
@Service
@Transactional(readOnly = true)
public class ExpresionCulturalServiceImpl implements ExpresionCulturalService {

    private final ExpresionCulturalRepository expresionRepository;
    private final PaisRepository paisRepository;
    private final ResponseMapper mapper;

    public ExpresionCulturalServiceImpl(ExpresionCulturalRepository expresionRepository,
                                        PaisRepository paisRepository,
                                        ResponseMapper mapper) {
        this.expresionRepository = expresionRepository;
        this.paisRepository = paisRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ExpresionCulturalResponse> listarTodas() {
        return mapper.toExpresionResponses(expresionRepository.findAllConPais());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Devuelve {@code null} cuando el pais no existe, para que el controller
     * lo traduzca a 404. Se comprueba contra {@code paises} y no contra el
     * resultado de la consulta de expresiones, porque un pais sin expresiones es
     * un estado valido y no merece un 404: lo que no existe es el pais.
     */
    @Override
    public List<ExpresionCulturalResponse> listarPorPais(String codigo) {
        if (!paisRepository.existsByCode(codigo)) {
            return null;
        }
        List<ExpresionCultural> expresiones = expresionRepository.findByPaisCode(codigo);
        return mapper.toExpresionResponses(expresiones);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Los tres casos que se comprueban aqui estan tambien cubiertos por
     * constraints en V8, pero comprobarlos en Java cambia lo que ve el cliente: una
     * FK violada o un {@code UNIQUE} reventado llegan como 500, y un 409 con el
     * nombre del choque dice que paso. El CHECK de la base sigue siendo la red,
     * porque el INSERT tambien puede venir de un script.
     */
    @Override
    @Transactional
    public ExpresionCulturalResponse crear(String id, String titulo, CategoriaExpresion categoria,
                                           String paisCode, String descText, String imagen,
                                           String creditos) {
        Pais pais = paisRepository.findById(paisCode)
                .orElseThrow(() -> ResourceNotFoundException.de("Pais", paisCode));

        if (expresionRepository.existsById(id)) {
            throw new DuplicateResourceException(
                    "Ya existe una expresion cultural con el identificador '" + id + "'");
        }

        if (expresionRepository.existsByPaisCodeAndCategoria(paisCode, categoria)) {
            throw new DuplicateResourceException(pais.getName()
                    + " ya tiene una expresion de categoria " + categoria);
        }

        if (imagen != null && !imagen.isBlank() && (creditos == null || creditos.isBlank())) {
            throw new IllegalArgumentException(
                    "Una expresion con imagen necesita creditos: es material de autor y hay que identificarlo");
        }

        ExpresionCultural guardada = expresionRepository.save(new ExpresionCultural(
                id, titulo, categoria, pais, descText,
                imagen == null || imagen.isBlank() ? null : imagen,
                creditos == null || creditos.isBlank() ? null : creditos,
                Instant.now()));

        return mapper.toExpresionResponse(guardada);
    }
}
