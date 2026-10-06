package com.hispania.services.interfaces;

import com.hispania.presentation.dto.request.PaisRequest;
import com.hispania.presentation.dto.response.PaisResponse;

import java.util.List;

/**
 * Contrato de negocio de los paises.
 *
 * <p>Es una interfaz y no una clase para dos cosas: el controller depende de esta
 * abstraccion y no de la implementacion, y las pruebas pueden sustituirla por un
 * doble sin levantar Spring.
 *
 * <p>Los metacos devuelven DTOs, no entidades. La capa de negocio no debe filtrar
 * objetos de persistencia hacia arriba: si lo hiciera, cualquier campo nuevo de la
 * entidad acabaria expuesto en la API sin revision.
 */
public interface PaisService {

    /**
     * Todos los paises con su serie historica y sus lugares ya resueltos.
     *
     * <p>Devuelve una lista vacia, no un error, si la tabla esta vacia: que no haya
     * datos es un estado valido de la base, no una falta.
     */
    List<PaisResponse> listarTodos();

    /**
     * Un pais por codigo.
     *
     * @param code codigo ISO de dos letras
     * @return el pais, o {@code null} si no existe. Se devuelve {@code null} en
     *         lugar de lanzar excepcion porque el controller decide el codigo HTTP y
     *         el servicio no debería imponerlo.
     */
    PaisResponse buscarPorCodigo(String code);

    /**
     * Crea un pais.
     *
     * @throws com.hispania.exception.DuplicateResourceException
     *         si el codigo ya existe
     */
    PaisResponse crear(PaisRequest request);

    /**
     * Actualiza un pais. Los campos ausentes del cuerpo se conservan.
     *
     * @throws com.hispania.exception.ResourceNotFoundException
     *         si el pais no existe
     */
    PaisResponse actualizar(String code, PaisRequest request);

    /**
     * Elimina un pais y, en cascada, su serie historica y sus lugares.
     *
     * @throws com.hispania.exception.ResourceNotFoundException
     *         si el pais no existe
     */
    void eliminar(String code);
}
