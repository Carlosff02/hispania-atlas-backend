package com.hispania.persistence.entity;

/**
 * Disciplina de una expresion cultural.
 *
 * <p>La categoria dice QUE es, no DONDE esta ni de que epoca es. Por eso no
 * coincide con {@link CategoriaLugar}: aquellos nueve valores describen un tipo
 * de sitio (un museo es ARTE, un templo es PATRIMONIO) y estos describen una
 * disciplina practicada. Un museo y "El Tango" no compiten por el mismo valor.
 *
 * <p>Las cuatro expresiones que vivian en el frontend tenian categorias que no
 * servian ('Pintura Virreinal', 'Pintura Moderna', 'Danza Tradicional', 'Danza y
 * Musica'): eran a la vez epocas y disciplinas, y el filtro de la seccion las
 * agrupaba como texto suelto. Al sembrarlas se pierde el matiz de que el tango es
 * tambien musica, y es una perdida consciente: la alternativa era no tener
 * ninguna garantia sobre el dato.
 *
 * <p>Se replica la lista en el CHECK de V8. Anadir un valor aqui sin migrar
 * deja al CHECK rechazando filas que la aplicacion cree validas, asi que los dos
 * lados se cambian juntos.
 */
public enum CategoriaExpresion {

    PINTURA,
    ESCULTURA,
    DANZA,
    MUSICA,
    TEATRO,
    LITERATURA,
    ARTESANIA,
    TRADICION_ORAL,
    /** Cocina como tradicion cultural, no como dato nutricional. */
    GASTRONOMIA
}
