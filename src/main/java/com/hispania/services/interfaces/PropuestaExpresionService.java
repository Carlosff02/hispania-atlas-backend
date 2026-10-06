package com.hispania.services.interfaces;

import com.hispania.presentation.dto.request.PropuestaExpresionRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.presentation.dto.response.PropuestaExpresionResponse;

import java.util.List;

/**
 * Cola de moderacion de expresiones culturales.
 *
 * <p>Escalado de {@link PropuestaService} sin las diferencias que comento ahi.
 * El flujo es el mismo: un USUARIO propone, un COLABORADOR aprueba o rechaza, y
 * al aprobar se inserta en {@code expresiones_culturales}.
 */
public interface PropuestaExpresionService {

    /** Registra una propuesta en estado PENDIENTE. */
    PropuestaExpresionResponse proponer(PropuestaExpresionRequest request, Long autorId);

    /** Cola pendiente, para el moderador. */
    List<PropuestaExpresionResponse> listarPendientes();

    /** Historial del usuario, para que vea que paso con lo que propuso. */
    List<PropuestaExpresionResponse> misPropuestas(Long autorId);

    /**
     * Aprueba o rechaza.
     *
     * <p>Al aprobar se inserta la expresion; al rechazar se exige un motivo, que
     * es lo unico que el proponente puede aprender de la decision.
     */
    PropuestaExpresionResponse revisar(Long id, RevisionRequest request, Long revisorId);
}
