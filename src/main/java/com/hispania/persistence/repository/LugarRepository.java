package com.hispania.persistence.repository;

import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.Lugar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code lugares}.
 */
public interface LugarRepository extends JpaRepository<Lugar, String> {

    List<Lugar> findAllByOrderByNameAsc();

    /**
     * Lugares de un pais.
     *
     * <p>La consulta llega al {@code JOIN} por la clave foranea {@code pais_code},
     * que la migracion V5 dejo indexada. Se proyecta la entidad completa en vez de
     * usar un {@code JOIN FETCH} porque aqui solo hay una relacion y no hay riesgo
     * de duplicar filas.
     */
    @Query("SELECT l FROM Lugar l WHERE l.pais.code = :code ORDER BY l.name ASC")
    List<Lugar> findByPaisCode(@Param("code") String code);

    /**
     * Un lugar con su pais resuelto en la misma consulta.
     *
     * <p>Sin el {@code JOIN FETCH}, el servicio tendria que hacer una segunda consulta
     * por el pais solo para leer su codigo, y con {@code open-in-view=false} esa
     * consulta ya no seria posible: la sesion se cierra al devolver la entidad.
     */
    @Query("SELECT l FROM Lugar l JOIN FETCH l.pais WHERE l.id = :id")
    Optional<Lugar> findByIdWithPais(@Param("id") String id);

    /**
     * Filtra por categoria.
     *
     * <p>Es una consulta derivada ({@code findByCategoria...}) sobre el campo
     * {@code category} de la entidad, que Hibernate persiste con
     * {@code @Enumerated(STRING)}. Spring Data convierte el parametro al
     * {@code EnumType.STRING} correspondiente, asi que la categoria viaja como
     * {@code 'DANZA'} y no como un ordinal: renumerar el enum no corromperia los
     * datos guardados.
     */
    List<Lugar> findAllByCategoryOrderByNameAsc(CategoriaLugar categoria);
}
