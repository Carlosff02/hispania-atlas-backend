package com.hispania.persistence.repository;

import com.hispania.persistence.entity.CategoriaExpresion;
import com.hispania.persistence.entity.ExpresionCultural;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Acceso a la tabla {@code expresiones_culturales}.
 *
 * <p>Aqui si se usa {@code JOIN FETCH} sobre la relacion {@code pais}, y la
 * diferencia con {@link PaisRepository} es que alli el aviso era sobre dos
 * colecciones a la vez (el producto cartesiano de un {@code JOIN FETCH} doble).
 * {@code ExpresionCultural} no declara ninguna coleccion: solo tiene un
 * {@code @ManyToOne}, asi que el fetch no multiplica filas y evita el N+1 de
 * {@code pais} al serializar el listado.
 */
public interface ExpresionCulturalRepository extends JpaRepository<ExpresionCultural, String> {

    /**
     * Listado para {@code GET /api/expresiones}, con el pais ya cargado.
     *
     * <p>El orden es por codigo de pais y luego por id, para que las tarjetas
     * mantengan la misma disposicion entre peticiones. Ordenar solo por titulo
     * haria que la seccion cambiara de orden al anadir una entrada.
     */
    @Query("""
            SELECT e FROM ExpresionCultural e
            JOIN FETCH e.pais
            ORDER BY e.pais.code ASC, e.id ASC
            """)
    List<ExpresionCultural> findAllConPais();

    /**
     * Las expresiones de un pais, para su ficha. Devuelve una lista y no un
     * {@code Optional}: un pais sin expresiones es un estado valido (casi todos
     * los 19 las tienen todavia) y no un 404. El 404 lo decide el servicio, que
     * es quien sabe si el pais existe.
     */
    @Query("SELECT e FROM ExpresionCultural e JOIN FETCH e.pais WHERE e.pais.code = :code ORDER BY e.id ASC")
    List<ExpresionCultural> findByPaisCode(String code);

    /**
     * Existe ya una expresion de ese pais en esa categoria. Lo usa el servicio de
     * propuestas al aprobar, para devolver un 409 con sentido en vez de dejar que
     * reviente el {@code UNIQUE (pais_code, categoria)} de V8 como un 500.
     */
    boolean existsByPaisCodeAndCategoria(String paisCode, CategoriaExpresion categoria);
}
