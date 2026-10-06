package com.hispania.services.impl;

import com.hispania.exception.ForbiddenException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.EstadoPropuesta;
import com.hispania.persistence.entity.PropuestaExpresion;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.persistence.repository.PropuestaExpresionRepository;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.request.PropuestaExpresionRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.presentation.dto.response.PropuestaExpresionResponse;
import com.hispania.services.interfaces.ExpresionCulturalService;
import com.hispania.services.interfaces.PropuestaExpresionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Cola de moderacion de expresiones culturales.
 *
 * <p>Escalado de {@code PropuestaServiceImpl} con los mismos criterios y las
 * mismas cuatro decisiones, que conviene no volver a pensar:
 *
 * <ol>
 *   <li>El slug se genera al aprobar, no al proponer, porque es cuando se puede
 *       comprobar que no choca con nada.</li>
 *   <li>La autorevision se bloquea solo a quien no puede escribir por la via
 *       directa. Para el resto no se bloquea: quien ya puede crearla con
 *       moderacion no obtiene nada exigiendole que la apruebe otro.</li>
 *   <li>El poder se lee del usuario de la base, no del token.</li>
 *   <li>Rechazar exige motivo; aprobar no lo exige.</li>
 * </ol>
 *
 * <p>Lo que cambia respecto a los lugares es el sufijo del identificador. Ahi se
 * anaden latitud y longitud para que dos lugares con el mismo nombre no choquen.
 * Una expresion no tiene coordenadas, asi que el sufijo es el codigo de pais: dos
 * "El Tango" de paises distintos son entradas legitimas y distintas.
 */
@Service
@Transactional(readOnly = true)
public class PropuestaExpresionServiceImpl implements PropuestaExpresionService {

    private static final Logger log = LoggerFactory.getLogger(PropuestaExpresionServiceImpl.class);

    /** La columna `id` de `expresiones_culturales` es VARCHAR(100). */
    private static final int MAX_ID_EXPRESION = 100;

    private final PropuestaExpresionRepository propuestas;
    private final UsuarioRepository usuarios;
    private final PaisRepository paises;
    private final ExpresionCulturalService expresionService;

    public PropuestaExpresionServiceImpl(PropuestaExpresionRepository propuestas,
                                         UsuarioRepository usuarios,
                                         PaisRepository paises,
                                         ExpresionCulturalService expresionService) {
        this.propuestas = propuestas;
        this.usuarios = usuarios;
        this.paises = paises;
        this.expresionService = expresionService;
    }

    @Override
    @Transactional
    public PropuestaExpresionResponse proponer(PropuestaExpresionRequest request, Long autorId) {
        Usuario autor = usuarios.findById(autorId)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", autorId));

        // Sin esta comprobacion, un codigo de pais inventado llegaria hasta la FK de
        // V8 y la propuesta se perderia con un 500. Un 404 dice que hay que elegir
        // un pais de los 19.
        if (!paises.existsByCode(request.country().toUpperCase(Locale.ROOT))) {
            throw ResourceNotFoundException.de("Pais", request.country());
        }

        if (propuestas.existsPendienteDuplicada(autorId, EstadoPropuesta.PENDIENTE,
                request.categoria(), request.country().toUpperCase(Locale.ROOT))) {
            throw new ForbiddenException("Ya tienes una propuesta pendiente de "
                    + request.country() + " en esa categoria: espera a que la revisen antes de enviar otra");
        }

        PropuestaExpresion guardada = propuestas.save(new PropuestaExpresion(
                request.titulo(), request.categoria(), request.country().toUpperCase(Locale.ROOT),
                request.descText(), vacioANulo(request.imagen()), vacioANulo(request.creditos()), autor));

        log.info("Propuesta de expresion {} pendiente de {} para {}",
                guardada.getId(), autor.getUsername(), request.country());
        return aResponse(guardada);
    }

    @Override
    public List<PropuestaExpresionResponse> listarPendientes() {
        return propuestas.findByEstadoConAutores(EstadoPropuesta.PENDIENTE).stream()
                .map(this::aResponse)
                .toList();
    }

    @Override
    public List<PropuestaExpresionResponse> misPropuestas(Long autorId) {
        return propuestas.findByAutorConAutores(autorId).stream()
                .map(this::aResponse)
                .toList();
    }

    @Override
    @Transactional
    public PropuestaExpresionResponse revisar(Long id, RevisionRequest request, Long revisorId) {
        PropuestaExpresion propuesta = propuestas.findByIdConAutores(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Propuesta de expresion", id));

        if (!propuesta.getEstado().estaAbierta()) {
            throw new ForbiddenException("La propuesta " + id + " ya fue revisada como "
                    + propuesta.getEstado() + " y no se puede volver a revisar");
        }

        Usuario revisor = usuarios.findById(revisorId)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", revisorId));

        // Mismo criterio que en la cola de lugares, y por el mismo motivo: el rango
        // se lee de la base y no del token, para que a quien le hayan degradado sin
        // que el token haya caducado no le quede el poder.
        boolean escribeDirecto = revisor.getRol().incluye(Rol.COLABORADOR);
        if (!escribeDirecto && propuesta.getPropuestoPor().getId().equals(revisorId)) {
            throw new ForbiddenException("No puedes revisar una propuesta tuya:"
                    + " hace falta poder crear expresiones directamente");
        }

        switch (request.estado()) {
            case APROBADA -> aprobar(propuesta, revisor);
            case RECHAZADA -> rechazar(propuesta, revisor, request.motivo());
            default -> throw new ForbiddenException(
                    "No se puede dejar una propuesta en estado " + request.estado());
        }

        PropuestaExpresion guardada = propuestas.save(propuesta);
        log.info("Propuesta de expresion {} revisada como {} por {}",
                id, guardada.getEstado(), revisor.getUsername());
        return aResponse(guardada);
    }

    /**
     * Inserta la expresion y marca la propuesta como aprobada.
     *
     * <p>El orden importa: primero se inserta y luego se marca. Si la insercion
     * falla (pais que ya tiene esa categoria, slug duplicado), la transaccion se
     * deshace entera y la propuesta sigue PENDIENTE, que es el estado correcto:
     * el moderador no ha decidido nada todavia.
     */
    private void aprobar(PropuestaExpresion propuesta, Usuario revisor) {
        expresionService.crear(
                generarId(propuesta),
                propuesta.getTitulo(),
                propuesta.getCategoria(),
                propuesta.getCountry(),
                propuesta.getDescText(),
                propuesta.getImagen(),
                propuesta.getCreditos());

        propuesta.revisar(revisor, EstadoPropuesta.APROBADA, null);
    }

    private void rechazar(PropuestaExpresion propuesta, Usuario revisor, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Rechazar una propuesta exige un motivo:"
                    + " es lo unico que quien la propuso puede aprender de la decision");
        }
        propuesta.revisar(revisor, EstadoPropuesta.RECHAZADA, motivo.trim());
    }

    /**
     * Identificador de la expresion: slug del titulo mas el codigo de pais.
     *
     * <p>El sufijo del pais no es decorativo. Sin el, dos "El Tango" propuestos
     * para paises distintos generarian el mismo id y el segundo chocaria con el
     * primero. En los lugares el sufijo son las coordenadas; aqui no hay
     * coordenadas, y el pais es lo que hace la expresion distinta de la otra.
     *
     * <p>Si aun asi choca (mismo titulo y mismo pais, que solo puede pasar si el
     * pais ya tiene una expresion de esa categoria), la restriccion UNIQUE de V8
     * lo rechaza antes y {@code crear} devuelve un 409 con el motivo.
     */
    private String generarId(PropuestaExpresion propuesta) {
        String base = slug(propuesta.getTitulo());
        if (base.isBlank()) {
            throw new IllegalArgumentException("No se puede generar un identificador a partir de '"
                    + propuesta.getTitulo() + "': el titulo necesita letras o numeros");
        }

        String id = base + "_" + propuesta.getCountry().toLowerCase(Locale.ROOT);
        if (id.length() >= MAX_ID_EXPRESION) {
            log.warn("Identificador de propuesta {} recortado a {} caracteres: {}",
                    propuesta.getId(), MAX_ID_EXPRESION, id);
            return id.substring(0, MAX_ID_EXPRESION);
        }
        return id;
    }

    /** Normaliza a minusculas sin tildes y une las palabras con guion bajo. */
    private static String slug(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return sinAcentos.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
    }

    /** Cadena vacia a {@code null}: un campo opcional en blanco se guarda como nulo. */
    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private PropuestaExpresionResponse aResponse(PropuestaExpresion p) {
        return new PropuestaExpresionResponse(
                p.getId(),
                p.getTitulo(),
                p.getCategoria().name(),
                p.getCountry(),
                p.getDescText(),
                p.getImagen(),
                p.getCreditos(),
                p.getEstado(),
                p.getPropuestoPor().getUsername(),
                p.getRevisadoPor() != null ? p.getRevisadoPor().getUsername() : null,
                p.getRevisadoAt(),
                p.getMotivoRechazo(),
                p.getCreatedAt());
    }
}
