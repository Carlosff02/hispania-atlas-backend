package com.hispania.services.interfaces;

import com.hispania.presentation.dto.request.PropuestaRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.presentation.dto.response.PropuestaResponse;

import java.util.List;

/**
 * Flujo de propuestas de lugares: proponer, listar y revisar.
 */
public interface PropuestaService {

    /** Crea una propuesta en estado PENDIENTE a nombre del usuario indicado. */
    PropuestaResponse proponer(PropuestaRequest request, Long autorId);

    /**
     * Cola de moderacion: propuestas pendientes, de la mas antigua a la mas nueva.
     * Solo para colaboradores.
     */
    List<PropuestaResponse> listarPendientes();

    /** Propuestas del usuario indicado, en cualquier estado. */
    List<PropuestaResponse> misPropuestas(Long autorId);

    /**
     * Aprueba o rechaza una propuesta pendiente.
     *
     * @param revisorId usuario que decide
     * @param id        identificador de la propuesta
     * @param request   estado y motivo
     */
    PropuestaResponse revisar(Long id, RevisionRequest request, Long revisorId);
}
