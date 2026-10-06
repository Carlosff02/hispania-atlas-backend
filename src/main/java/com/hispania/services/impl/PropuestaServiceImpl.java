package com.hispania.services.impl;

import com.hispania.exception.ForbiddenException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.EstadoPropuesta;
import com.hispania.persistence.entity.PropuestaLugar;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.persistence.repository.PropuestaLugarRepository;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.request.PropuestaRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.presentation.dto.response.PropuestaResponse;
import com.hispania.services.interfaces.LugarService;
import com.hispania.services.interfaces.PropuestaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Flujo de propuestas de lugares.
 *
 * <h2>Por que aprobar delega en LugarService</h2>
 * Al aprobar se inserta una fila en {@code lugares}, y esa inserta tiene reglas que
 * ya estan escritas y probadas en {@link LugarService#crear}: comprobar que el pais
 * existe, validar las coordenadas y evitar identificadores duplicados. Este
 * servicio construye el {@link LugarRequest} y delega, en vez de repetir esa
 * logica. Si se duplicara, un cambio futuro en las reglas del alta de lugares
 * dejaria de aplicarse a los que nacen de una propuesta, sin que nada fallara.
 */
@Service
public class PropuestaServiceImpl implements PropuestaService {

    private static final Logger log = LoggerFactory.getLogger(PropuestaServiceImpl.class);

    /** La columna de `lugares` admite 50 caracteres. */
    private static final int MAX_ID_LUGAR = 50;

    private final PropuestaLugarRepository propuestas;
    private final UsuarioRepository usuarios;
    private final PaisRepository paises;
    private final LugarService lugarService;

    public PropuestaServiceImpl(PropuestaLugarRepository propuestas,
                                UsuarioRepository usuarios,
                                PaisRepository paises,
                                LugarService lugarService) {
        this.propuestas = propuestas;
        this.usuarios = usuarios;
        this.paises = paises;
        this.lugarService = lugarService;
    }

    @Override
    @Transactional
    public PropuestaResponse proponer(PropuestaRequest request, Long autorId) {
        Usuario autor = usuarios.findById(autorId)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", autorId));

        // El pais se comprueba al proponer y no al aprobar, para que el usuario se
        // entere de que eligio un pais inexistente en el momento, en lugar de
        // descubrirlo semanas despues cuando alguien revise su propuesta.
        String country = request.country().toUpperCase(Locale.ROOT);
        if (!paises.existsByCode(country)) {
            throw ResourceNotFoundException.de("Pais", request.country());
        }

        PropuestaLugar propuesta = new PropuestaLugar(
                request.nombre().trim(),
                country,
                request.lat(),
                request.lng(),
                request.category(),
                request.icon(),
                request.period(),
                request.descText(),
                request.img(),
                autor
        );

        PropuestaLugar guardada = propuestas.save(propuesta);
        log.info("Propuesta {} registrada por {}", guardada.getId(), autor.getUsername());

        return aResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropuestaResponse> listarPendientes() {
        return propuestas.findByEstadoConAutores(EstadoPropuesta.PENDIENTE).stream()
                .map(PropuestaServiceImpl::aResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropuestaResponse> misPropuestas(Long autorId) {
        return propuestas.findByAutorConAutores(autorId).stream()
                .map(PropuestaServiceImpl::aResponse)
                .toList();
    }

    /**
     * Aprueba o rechaza una propuesta pendiente.
     *
     * <p>Al aprobar, el lugar se inserta <strong>antes</strong> de marcar la propuesta
     * como aprobada, dentro de la misma transaccion. Si la inserta falla, la
     * propuesta sigue pendiente y el moderador reintenta; con el orden inverso, la
     * propuesta quedaria marcada como aprobada sin que existiera el lugar.
     *
     * <p>Quien puede revisar puede revisar tambien lo suyo. La unica excepcion es el
     * USUARIO, que no puede crear lugares por la via directa y por tanto no puede
     * resolver la suya de ninguna otra manera. El ADMIN mantiene la moderacion
     * aunque no pueda proponer.
     */
    @Override
    @Transactional
    public PropuestaResponse revisar(Long id, RevisionRequest request, Long revisorId) {
        PropuestaLugar propuesta = propuestas.findByIdConAutores(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Propuesta", id));

        if (!propuesta.getEstado().estaAbierta()) {
            throw new ForbiddenException("La propuesta " + id + " ya fue revisada como "
                    + propuesta.getEstado() + " y no se puede volver a revisar");
        }

        Usuario revisor = usuarios.findById(revisorId)
                .orElseThrow(() -> ResourceNotFoundException.de("Usuario", revisorId));

        /*
         * La autorevision se bloquea solo a quien NO puede escribir en `lugares` por
         * la via directa, que es el USUARIO. Para el resto no se bloquea, y no es un
         * olvido: quien ya puede crear el lugar con POST /api/places no obtiene nada
         * aprobandolo por aqui, asi que exigirle que lo apruebe otro no protege de
         * nada, solo ralentiza. Por eso el caso de "alguien propuso como usuario, le
         * ascendieron y ahora su propuesta seguia pendiente sin que nadie pudiera
         * resolverla" tiene que poder resolverse solo.
         *
         * El rango se lee del usuario de la base y no del token a proposito: si le
         * acabaran de degradar sin que el token hubiera caducado, aqui ya no se le
         * reconoce el poder, que es el lado correcto en el que equivocarse.
         */
        boolean escribeDirecto = revisor.getRol().incluye(Rol.COLABORADOR);
        if (!escribeDirecto && propuesta.getPropuestoPor().getId().equals(revisorId)) {
            throw new ForbiddenException("No puedes revisar una propuesta tuya:"
                    + " hace falta poder crear lugares directamente");
        }

        switch (request.estado()) {
            case APROBADA -> aprobar(propuesta, revisor);
            case RECHAZADA -> rechazar(propuesta, revisor, request.motivo());
        }

        PropuestaLugar guardada = propuestas.save(propuesta);
        log.info("Propuesta {} revisada como {} por {}",
                id, guardada.getEstado(), revisor.getUsername());

        return aResponse(guardada);
    }

    private void aprobar(PropuestaLugar propuesta, Usuario revisor) {
        LugarRequest nuevoLugar = new LugarRequest(
                generarId(propuesta),
                propuesta.getNombre(),
                propuesta.getCountry(),
                propuesta.getLat(),
                propuesta.getLng(),
                propuesta.getCategory(),
                propuesta.getIcon(),
                propuesta.getPeriod(),
                propuesta.getDescText(),
                propuesta.getImg()
        );

        lugarService.crear(nuevoLugar);
        propuesta.aprobar(revisor);
    }

    private void rechazar(PropuestaLugar propuesta, Usuario revisor, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            // Se comprueba aqui y tambien por CHECK en la base. Si solo lo
            // comprobara PostgreSQL, el moderador recibiria un 409 que no menciona
            // el motivo, que es el campo que debe corregir.
            throw new IllegalArgumentException("Para rechazar una propuesta hay que indicar el motivo");
        }
        propuesta.rechazar(revisor, motivo.trim());
    }

    /**
     * Construye el identificador legible del lugar a partir de su nombre.
     *
     * <p>Se anaden las coordenadas porque dos lugares pueden llamarse igual: hay
     * varias "Plaza Mayor". Las coordenadas no se repiten en un mismo sitio, asi que
     * hacen la clave unica sin preguntar nada.
     *
     * <p>Las coordenadas se escriben con tres decimales y el punto se cambia por
     * guion bajo. Un punto sin convertir haria que el identificador violara el
     * CHECK de {@code lugares.id}, que solo admite minusculas, digitos y guion bajo:
     * el alta fallaria con un 409 que no explicaria nada.
     *
     * <p>Si el nombre se tradujera a una cadena vacia (solo tildes o solo signos), se
     * avisa con un error legible en vez de dejar que reviente el CHECK de PostgreSQL.
     */
    private String generarId(PropuestaLugar propuesta) {
        String base = slug(propuesta.getNombre());
        if (base.isBlank()) {
            throw new IllegalArgumentException("No se puede generar un identificador a partir de '"
                    + propuesta.getNombre() + "': el nombre necesita letras o numeros");
        }

        String id = base + "_" + coordenada(propuesta.getLat()) + "_" + coordenada(propuesta.getLng());
        if (id.length() >= MAX_ID_LUGAR) {
            // El id es la clave primaria y la columna tiene 50 caracteres. Se avisa
            // en vez de recortar en silencio: el moderador debe saber que el
            // identificador ya no se parece al nombre del lugar.
            log.warn("Identificador de propuesta {} recortado a {} caracteres: {}",
                    propuesta.getId(), MAX_ID_LUGAR, id);
            return id.substring(0, MAX_ID_LUGAR);
        }
        return id;
    }

    /**
     * Coordenada como fragmento de identificador: tres decimales, sin signo y sin
     * punto decimal, que no es un caracter valido en la clave.
     */
    private static String coordenada(double valor) {
        return String.format(Locale.ROOT, "%.3f", Math.abs(valor)).replace('.', '_');
    }

    /** Normaliza a minusculas sin tildes y une las palabras con guion bajo. */
    private static String slug(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return sinAcentos
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static PropuestaResponse aResponse(PropuestaLugar p) {
        return new PropuestaResponse(
                p.getId(),
                p.getNombre(),
                p.getCountry(),
                p.getLat(),
                p.getLng(),
                p.getCategory(),
                p.getIcon(),
                p.getPeriod(),
                p.getDescText(),
                p.getImg(),
                p.getEstado(),
                p.getPropuestoPor() != null ? p.getPropuestoPor().getId() : null,
                p.getPropuestoPor() != null ? p.getPropuestoPor().getUsername() : null,
                p.getRevisadoPor() != null ? p.getRevisadoPor().getUsername() : null,
                p.getRevisadoAt(),
                p.getMotivoRechazo(),
                p.getCreatedAt()
        );
    }
}
