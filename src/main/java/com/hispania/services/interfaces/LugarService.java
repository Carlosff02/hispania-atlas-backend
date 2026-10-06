package com.hispania.services.interfaces;

import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.response.LugarResponse;

import java.util.List;

/**
 * Contrato de negocio de los lugares.
 *
 * <p>Los metacos devuelven DTOs y no entidades, por el mismo motivo que en
 * {@link PaisService}: las entidades no deben salir de la capa de negocio.
 */
public interface LugarService {

    /** Todos los lugares, ordenados por nombre. */
    List<LugarResponse> listarTodos();

    /**
     * Un lugar por identificador.
     *
     * @param id identificador legible
     * @return el lugar, o {@code null} si no existe
     */
    LugarResponse buscarPorId(String id);

    /**
     * Lugares de un pais, para las consultas del mapa.
     *
     * @param code codigo ISO de dos letras
     * @return lista vacia si el pais existe pero no tiene lugares; {@code null} si el
     *         pais no existe, para que el controller lo distinga de "no hay lugares"
     */
    List<LugarResponse> listarPorPais(String code);

    /**
     * Filtra por categoria.
     *
     * <p>Existe porque el CHECK de la base de datos y el enumerado
     * {@link CategoriaLugar} pueden desincronizarse si alguien anade un valor a uno y
     * no al otro. Al filtrar aqui se comparan enumerados, no cadenas, asi que un
     * valor inesperado no llega ni a la consulta.
     */
    List<LugarResponse> listarPorCategoria(CategoriaLugar categoria);

    /**
     * Crea un lugar.
     *
     * @throws com.hispania.exception.DuplicateResourceException
     *         si el id ya existe
     * @throws com.hispania.exception.ResourceNotFoundException
     *         si el {@code country} del cuerpo no corresponde a ningun pais
     */
    LugarResponse crear(LugarRequest request);

    /**
     * Actualiza un lugar. Los campos ausentes del cuerpo se conservan.
     *
     * @throws com.hispania.exception.ResourceNotFoundException
     *         si el lugar no existe
     */
    LugarResponse actualizar(String id, LugarRequest request);

    /**
     * @throws com.hispania.exception.ResourceNotFoundException
     *         si el lugar no existe
     */
    void eliminar(String id);
}
