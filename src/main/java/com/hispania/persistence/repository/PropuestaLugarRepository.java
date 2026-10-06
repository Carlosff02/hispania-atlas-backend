package com.hispania.persistence.repository;

import com.hispania.persistence.entity.EstadoPropuesta;
import com.hispania.persistence.entity.PropuestaLugar;
import com.hispania.persistence.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code propuestas_lugar}.
 */
public interface PropuestaLugarRepository extends JpaRepository<PropuestaLugar, Long> {

    /**
     * Cola de moderacion. Se proyecta la entidad con {@code JOIN FETCH} de autor
     * y revisor porque la lista siempre muestra ambos nombres y, con
     * {@code open-in-view=false}, leerlos despues de cerrar la sesion provocaria
     * {@code LazyInitializationException}.
     */
    @Query("""
            SELECT p FROM PropuestaLugar p
            JOIN FETCH p.propuestoPor
            LEFT JOIN FETCH p.revisadoPor
            WHERE p.estado = :estado
            ORDER BY p.createdAt ASC
            """)
    List<PropuestaLugar> findByEstadoConAutores(@Param("estado") EstadoPropuesta estado);

    /** Historial de propuestas de un usuario concreto. */
    @Query("""
            SELECT p FROM PropuestaLugar p
            JOIN FETCH p.propuestoPor
            LEFT JOIN FETCH p.revisadoPor
            WHERE p.propuestoPor.id = :autorId
            ORDER BY p.createdAt DESC
            """)
    List<PropuestaLugar> findByAutorConAutores(@Param("autorId") Long autorId);

    @Query("""
            SELECT p FROM PropuestaLugar p
            JOIN FETCH p.propuestoPor
            LEFT JOIN FETCH p.revisadoPor
            WHERE p.id = :id
            """)
    Optional<PropuestaLugar> findByIdConAutores(@Param("id") Long id);
}
