package com.hispania.persistence.repository;

import com.hispania.persistence.entity.CategoriaExpresion;
import com.hispania.persistence.entity.EstadoPropuesta;
import com.hispania.persistence.entity.PropuestaExpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code propuestas_expresion}.
 *
 * <p>Escalado de {@link PropuestaLugarRepository} sin las diferencias que
 * comento ahi: se proyecta con {@code JOIN FETCH} de autor y revisor porque las
 * listas siempre muestran ambos nombres, y con {@code open-in-view=false} leerlos
 * despues de cerrar la sesion daria {@code LazyInitializationException}.
 */
public interface PropuestaExpresionRepository extends JpaRepository<PropuestaExpresion, Long> {

    @Query("""
            SELECT p FROM PropuestaExpresion p
            JOIN FETCH p.propuestoPor
            LEFT JOIN FETCH p.revisadoPor
            WHERE p.estado = :estado
            ORDER BY p.createdAt ASC
            """)
    List<PropuestaExpresion> findByEstadoConAutores(@Param("estado") EstadoPropuesta estado);

    /** Historial de propuestas de un usuario concreto. */
    @Query("""
            SELECT p FROM PropuestaExpresion p
            JOIN FETCH p.propuestoPor
            LEFT JOIN FETCH p.revisadoPor
            WHERE p.propuestoPor.id = :autorId
            ORDER BY p.createdAt DESC
            """)
    List<PropuestaExpresion> findByAutorConAutores(@Param("autorId") Long autorId);

    @Query("""
            SELECT p FROM PropuestaExpresion p
            JOIN FETCH p.propuestoPor
            LEFT JOIN FETCH p.revisadoPor
            WHERE p.id = :id
            """)
    Optional<PropuestaExpresion> findByIdConAutores(@Param("id") Long id);

    /** Propuestas de un autor que aun siguen abiertas. */
    @Query("""
            SELECT COUNT(p) > 0 FROM PropuestaExpresion p
            WHERE p.propuestoPor.id = :autorId
              AND p.estado = :estado
              AND p.categoria = :categoria
              AND p.country = :country
            """)
    boolean existsPendienteDuplicada(@Param("autorId") Long autorId,
                                     @Param("estado") EstadoPropuesta estado,
                                     @Param("categoria") CategoriaExpresion categoria,
                                     @Param("country") String country);
}
