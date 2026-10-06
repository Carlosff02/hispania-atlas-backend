package com.hispania.services.mapper;

import com.hispania.persistence.entity.ExpresionCultural;
import com.hispania.persistence.entity.Lugar;
import com.hispania.persistence.entity.Pais;
import com.hispania.persistence.entity.PaisSerieHistorica;
import com.hispania.presentation.dto.response.ExpresionCulturalResponse;
import com.hispania.presentation.dto.response.LugarResponse;
import com.hispania.presentation.dto.response.PaisResponse;
import com.hispania.presentation.dto.response.SerieHistoricaResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte entidades de persistencia en DTOs de respuesta.
 *
 * <p>Vive en la capa de servicios y no dentro de los DTOs a proposito: un DTO de
 * salida que recibe una entidad en un metodo {@code from} estaria importing
 * {@code persistence}, y la capa {@code presentation} dejaria de ser autonomous.
 * Asi el unico punto donde se conoce la forma de ambas capas es este.
 *
 * <p>Es un {@code @Component} sin estado, de modo que Spring lo reutiliza y las
 * conversiones se pueden pedir por inyeccion en vez de llamar a metodos estaticos.
 */
@Component
public class ResponseMapper {

    /**
     * Carpeta estatica donde se sirven las imagenes de las expresiones culturales.
     *
     * <p>Corresponde a {@code src/main/resources/static/expresiones/}, que Spring
     * Boot publica tal cual. Las imagenes de los lugares y los paises siguen siendo
     * URLs externas, y por eso esta constante solo aplica a expresiones.
     */
    private static final String RUTA_IMAGENES = "/expresiones/";

    /**
     * Proyecta una entidad a su DTO.
     *
     * <p>El coste de convertir 17 lugares y 19 paises es despreciable, asi que aqui
     * no hay ninguna cache. Si algun dia hiciera falta, el sitio natural seria un
     * servicio aparte, no un mapa estatico.
     */
    public LugarResponse toLugarResponse(Lugar entity) {
        return new LugarResponse(
                entity.getId(),
                entity.getName(),
                entity.getCountry(),
                entity.getLat(),
                entity.getLng(),
                entity.getCategory() != null ? entity.getCategory().name() : null,
                entity.getIcon(),
                entity.getPeriod(),
                entity.getDescText(),
                entity.getImg()
        );
    }

    public SerieHistoricaResponse toSerieResponse(PaisSerieHistorica entity) {
        return new SerieHistoricaResponse(
                entity.getYear(),
                entity.getGdp(),
                entity.getGdpPc(),
                entity.getPop(),
                entity.getGrowth(),
                entity.getInflation(),
                entity.getExports(),
                entity.getImports(),
                entity.getHdi(),
                entity.getDebt(),
                entity.getTrade()
        );
    }

    /**
     * Proyecta un pais junto con sus colecciones ya resueltas.
     *
     * @param lugares  lugares del pais; se espera el resultado de
     *                 {@code LugarRepository.findByPaisCode}, ya ordenados
     * @param series   serie del pais, ordenada por anio ascendente
     */
    public PaisResponse toPaisResponse(Pais entity,
                                       List<Lugar> lugares,
                                       List<PaisSerieHistorica> series) {
        return new PaisResponse(
                entity.getCode(),
                entity.getName(),
                entity.getCapital(),
                entity.getLat(),
                entity.getLng(),
                entity.getRegion() != null ? entity.getRegion().getValor() : null,
                entity.getDescText(),
                series.stream().map(this::toSerieResponse).toList(),
                lugares.stream().map(this::toLugarResponse).toList()
        );
    }

    /** Atajo para convertir una coleccion de lugares. */
    public List<LugarResponse> toLugarResponses(List<Lugar> entities) {
        return entities.stream().map(this::toLugarResponse).toList();
    }

    /**
     * Convierte una expresion cultural.
     *
     * <p>La ruta de la imagen se compone aqui y no en el frontend, porque es el
     * unico sitio donde se sabe donde vive el fichero. El JSON lleva la ruta
     * servida y la base se queda con el nombre, de modo que mover las imagenes a
     * otra carpeta no obliga a tocar el frontend.
     *
     * <p>La categoria va como {@code name()} del enum, en mayusculas, porque es lo
     * que el frontend usa para agrupar el filtro. Lo mismo que hace
     * {@link Lugar} con su categoria.
     */
    public ExpresionCulturalResponse toExpresionResponse(ExpresionCultural entity) {
        return new ExpresionCulturalResponse(
                entity.getId(),
                entity.getTitulo(),
                entity.getCategoria().name(),
                entity.getPais().getCode(),
                entity.getPais().getName(),
                entity.getDescText(),
                entity.getImagen() != null ? RUTA_IMAGENES + entity.getImagen() : null,
                entity.getCreditos()
        );
    }

    /** Atajo para convertir una coleccion de expresiones. */
    public List<ExpresionCulturalResponse> toExpresionResponses(List<ExpresionCultural> entities) {
        return entities.stream().map(this::toExpresionResponse).toList();
    }
}
