package com.hispania.services.impl;

import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.Lugar;
import com.hispania.persistence.entity.Pais;
import com.hispania.persistence.entity.PaisSerieHistorica;
import com.hispania.persistence.repository.LugarRepository;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.persistence.repository.PaisSerieHistoricaRepository;
import com.hispania.presentation.dto.request.PaisRequest;
import com.hispania.presentation.dto.response.PaisResponse;
import com.hispania.services.interfaces.PaisService;
import com.hispania.services.mapper.ResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementacion de {@link PaisService}.
 *
 * <p>La logica que aporta sobre el CRUD son tres decisiones:
 *
 * <ul>
 *   <li><strong>Tres consultas en total</strong>, no una por pais. Los lugares y las
 *       series se piden una vez cada uno y se agrupan en memoria por codigo, en
 *       lugar de dejar que Hibernate dispare un acceso perezoso por cada
 *       coleccion.</li>
 *   <li><strong>Las actualizaciones son parciales.</strong> Un campo ausente en el
 *       cuerpo se conserva; es lo que espera un {@code PUT} de edicion sobre un
 *       formulario que solo envia lo que cambio.</li>
 *   <li><strong>Se borran las filas hijas a mano.</strong> El esquema no declara
 *       cascadas en JPA, asi que sin este borrado explicito, con
 *       {@code open-in-view=false} y entidades planas, PostgreSQL rechazaria el
 *       {@code DELETE} con una violacion de clave foranea.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PaisServiceImpl implements PaisService {

    private static final Logger log = LoggerFactory.getLogger(PaisServiceImpl.class);

    private final PaisRepository paisRepository;
    private final LugarRepository lugarRepository;
    private final PaisSerieHistoricaRepository serieRepository;
    private final ResponseMapper mapper;

    public PaisServiceImpl(PaisRepository paisRepository,
                           LugarRepository lugarRepository,
                           PaisSerieHistoricaRepository serieRepository,
                           ResponseMapper mapper) {
        this.paisRepository = paisRepository;
        this.lugarRepository = lugarRepository;
        this.serieRepository = serieRepository;
        this.mapper = mapper;
    }

    @Override
    public List<PaisResponse> listarTodos() {
        List<Pais> paises = paisRepository.findAllByOrderByCodeAsc();
        if (paises.isEmpty()) {
            return List.of();
        }

        // groupingBy conserva el orden de aparicion de las claves, y ambos repositorios
        // ya devuelven las filas ordenadas, asi que no hace falta un segundo sort.
        Map<String, List<Lugar>> lugaresPorPais = lugarRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(lugar -> lugar.getCountry()));

        Map<String, List<PaisSerieHistorica>> seriesPorPais =
                serieRepository.findAllByOrderByPaisCodeAscYearAsc().stream()
                        .collect(Collectors.groupingBy(PaisSerieHistorica::getPaisCode));

        return paises.stream()
                .map(pais -> mapper.toPaisResponse(
                        pais,
                        lugaresPorPais.getOrDefault(pais.getCode(), List.of()),
                        seriesPorPais.getOrDefault(pais.getCode(), List.of())))
                .toList();
    }

    @Override
    public PaisResponse buscarPorCodigo(String code) {
        return paisRepository.findById(code)
                .map(pais -> mapper.toPaisResponse(
                        pais,
                        lugarRepository.findByPaisCode(code),
                        serieRepository.findByPaisCodeOrderByYearAsc(code)))
                .orElse(null);
    }

    @Override
    @Transactional
    public PaisResponse crear(PaisRequest request) {
        if (paisRepository.existsById(request.code())) {
            throw DuplicateResourceException.de("Pais", request.code());
        }

        Pais pais = new Pais(
                request.code(),
                request.name(),
                request.capital(),
                request.lat(),
                request.lng(),
                request.region(),
                request.descText()
        );

        Pais guardado = paisRepository.save(pais);
        log.info("Pais creado: {}", guardado.getCode());

        // Se devuelve con las colecciones vacias: acaban de crearse, no hay nada que
        // anadir y evita dos consultas extra solo para responder a un alta.
        return mapper.toPaisResponse(guardado, List.of(), List.of());
    }

    @Override
    @Transactional
    public PaisResponse actualizar(String code, PaisRequest request) {
        Pais pais = paisRepository.findById(code)
                .orElseThrow(() -> ResourceNotFoundException.de("Pais", code));

        // El identificador no se toca: lo impone la ruta.
        pais.setName(request.name());
        pais.setCapital(request.capital());
        pais.setLat(request.lat());
        pais.setLng(request.lng());
        pais.setRegion(request.region());
        pais.setDescText(request.descText());

        Pais guardado = paisRepository.save(pais);
        log.info("Pais actualizado: {}", code);

        return mapper.toPaisResponse(
                guardado,
                lugarRepository.findByPaisCode(code),
                serieRepository.findByPaisCodeOrderByYearAsc(code));
    }

    @Override
    @Transactional
    public void eliminar(String code) {
        if (!paisRepository.existsById(code)) {
            throw ResourceNotFoundException.de("Pais", code);
        }

        // Primero las hijas, luego el padre. Al reves, la clave foranea de
        // lugares.pais_code y paises_series_historicas.pais_code lo impediria.
        lugarRepository.deleteAll(lugarRepository.findByPaisCode(code));
        serieRepository.deleteAll(serieRepository.findByPaisCodeOrderByYearAsc(code));
        paisRepository.deleteById(code);

        log.info("Pais eliminado con sus lugares y series: {}", code);
    }
}
