package com.hispania.persistence.entity;

/**
 * Categoria cultural de un lugar.
 *
 * <p>La lista coincide con la restriccion {@code lugares_category_check} de la
 * migracion V2. Si se anade un valor aqui, hay que anadirlo tambien alla; el
 * {@code ddl-auto=validate} no detecta esta discrepancia, solo el CHECK de Postgres.
 */
public enum CategoriaLugar {
    ARTE,
    DANZA,
    ARQUEOLOGIA,
    PATRIMONIO,
    HISTORICO,
    INFRAESTRUCTURA,
    PAISAJE_NATURAL,
    ACADEMICO,
    GASTRONOMICO
}
