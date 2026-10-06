package com.hispania.services.interfaces;

import com.hispania.persistence.entity.CategoriaExpresion;
import com.hispania.presentation.dto.response.ExpresionCulturalResponse;

import java.util.List;

/**
 * Lectura de expresiones culturales.
 *
 * <p>Solo lectura a proposito: las expresiones se crean aprobando una propuesta,
 * no con un {@code POST} directo. La razon es la misma que en {@code lugares}, y
 * esta vez se ve mejor, porque cualquiera puede proponer una tradicion que no es
 * de ningun pais o que esta mal atribuida.
 */
public interface ExpresionCulturalService {

    /**
     * Todas las expresiones, ordenadas por codigo de pais y luego por id.
     *
     * <p>Devuelve una lista vacia si no hay ninguna: la seccion del frontend la
     * recorre y una tabla vacia es un estado valido, no un error.
     */
    List<ExpresionCulturalResponse> listarTodas();

    /**
     * Las expresiones de un pais, o lista vacia si no tiene ninguna.
     *
     * <p>No lanza 404 por un pais sin expresiones, porque 15 de los 19 paises
     * no tienen ninguna todavia y eso no es un fallo. El pais inexistente lo
     * detecta el servicio y devuelve {@code null} para que el controller
     * responda 404, que es un caso distinto.
     */
    List<ExpresionCulturalResponse> listarPorPais(String codigo);

    /**
     * Inserta una expresion. No hay endpoint publico que la llame: la escritura
     * pasa por aprobar una propuesta.
     *
     * <p>El metodo es publico en la interfaz pero no esta expuesto por HTTP, y esa
     * distincion es la que mantiene la seccion de solo lectura sin tener que
     * duplicar la validacion en el servicio de propuestas.
     *
     * @throws com.hispania.exception.ResourceNotFoundException si el pais no existe
     * @throws com.hispania.exception.DuplicateResourceException  si el id ya existe, o
     *         si el pais ya tiene una expresion de esa categoria
     */
    ExpresionCulturalResponse crear(String id, String titulo, CategoriaExpresion categoria,
                                    String paisCode, String descText, String imagen,
                                    String creditos);
}
