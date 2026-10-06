package com.hispania.services.impl;

import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.Lugar;
import com.hispania.persistence.entity.Pais;
import com.hispania.persistence.repository.LugarRepository;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.response.LugarResponse;
import com.hispania.services.interfaces.LugarService;
import com.hispania.services.mapper.ResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementacion de {@link LugarService}.
 *
 * <p>La logica que aporta es sobre todo la resolucion del pais y la coherencia de las
 * dos columnas que lo guardan.
 */
@Service
@Transactional(readOnly = true)
public class LugarServiceImpl implements LugarService {

    private static final Logger log = LoggerFactory.getLogger(LugarServiceImpl.class);

    private final LugarRepository lugarRepository;
    private final PaisRepository paisRepository;
    private final ResponseMapper mapper;

    public LugarServiceImpl(LugarRepository lugarRepository,
                            PaisRepository paisRepository,
                            ResponseMapper mapper) {
        this.lugarRepository = lugarRepository;
        this.paisRepository = paisRepository;
        this.mapper = mapper;
    }

    @Override
    public List<LugarResponse> listarTodos() {
        return mapper.toLugarResponses(lugarRepository.findAllByOrderByNameAsc());
    }

    @Override
    public LugarResponse buscarPorId(String id) {
        return lugarRepository.findByIdWithPais(id).map(mapper::toLugarResponse).orElse(null);
    }

    @Override
    public List<LugarResponse> listarPorPais(String code) {
        // Se distingue "pais inexistente" de "pais sin lugares" para que el controller
        // pueda responder 404 o una lista vacia segun corresponda.
        if (!paisRepository.existsById(code)) {
            return null;
        }
        return mapper.toLugarResponses(lugarRepository.findByPaisCode(code));
    }

    @Override
    public List<LugarResponse> listarPorCategoria(CategoriaLugar categoria) {
        // El repositorio resuelve el filtro por el campo `category` de la entidad, con
        // el valor ya convertido al enumerado: una categoria desconocida no llega a SQL.
        return mapper.toLugarResponses(
                lugarRepository.findAllByCategoryOrderByNameAsc(categoria));
    }

    @Override
    @Transactional
    public LugarResponse crear(LugarRequest request) {
        if (lugarRepository.existsById(request.id())) {
            throw DuplicateResourceException.de("Lugar", request.id());
        }

        Pais pais = paisRepository.findById(request.country())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el pais con codigo '" + request.country() + "'"));

        // El constructor de Lugar escribe `country` a partir del pais, de modo que las
        // dos columnas quedan iguales y se cumple el CHECK de la migracion V5.
        Lugar lugar = new Lugar(
                request.id(),
                request.name(),
                pais,
                request.lat(),
                request.lng(),
                request.category(),
                request.icon(),
                request.period(),
                request.descText(),
                request.img()
        );

        Lugar guardado = lugarRepository.save(lugar);
        log.info("Lugar creado: {} ({})", guardado.getId(), guardado.getCountry());

        return mapper.toLugarResponse(guardado);
    }

    @Override
    @Transactional
    public LugarResponse actualizar(String id, LugarRequest request) {
        Lugar lugar = lugarRepository.findByIdWithPais(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Lugar", id));

        // El identificador lo impone la ruta y no se toca. El cuerpo podria traer otro,
        // y si se usara el del cuerpo se moveria la fila a otra clave primaria.
        lugar.setName(request.name());

        // `country` se resuelve a un Pais para que setPais mantenga `country` y
        // `pais_code` sincronizadas. Si el cuerpo omite el pais, se conserva el actual.
        if (request.country() != null && !request.country().isBlank()) {
            Pais pais = paisRepository.findById(request.country())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No existe el pais con codigo '" + request.country() + "'"));
            lugar.setPais(pais);
        }

        lugar.setLat(request.lat());
        lugar.setLng(request.lng());

        if (request.category() != null) {
            lugar.setCategory(request.category());
        }
        lugar.setIcon(request.icon());
        lugar.setPeriod(request.period());
        lugar.setDescText(request.descText());
        lugar.setImg(request.img());

        Lugar guardado = lugarRepository.save(lugar);
        log.info("Lugar actualizado: {}", id);

        return mapper.toLugarResponse(guardado);
    }

    @Override
    @Transactional
    public void eliminar(String id) {
        if (!lugarRepository.existsById(id)) {
            throw ResourceNotFoundException.de("Lugar", id);
        }
        lugarRepository.deleteById(id);
        log.info("Lugar eliminado: {}", id);
    }
}
