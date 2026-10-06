package com.hispania.persistence.repository;

import com.hispania.persistence.entity.Pais;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla {@code paises}.
 *
 * <p>Spring Data genera la implementacion en tiempo de ejecucion, asi que no hay
 * ninguna clase concreta que escribir.
 *
 * <p>No hay metodos con {@code JOIN FETCH} a proposito. {@code Pais} no declara
 * colecciones {@code @OneToMany}: el mapeo es plano (una entidad por tabla) y es
 * {@code PaisServiceImpl} quien reparte las consultas entre los tres repositorios
 * y las une en memoria. Asi {@code GET /api/countries} son <strong>tres</strong>
 * consultas en total, en lugar de una por pais y por cada coleccion (N+1), y sin
 * el producto cartesiano que genera un {@code JOIN FETCH} de dos colecciones
 * simultaneas.
 */
public interface PaisRepository extends JpaRepository<Pais, String> {

    /**
     * El frontend pinta el mapa de izquierda a derecha, asi que el orden por codigo
     * mantiene la disposicion estable entre peticiones.
     */
    List<Pais> findAllByOrderByCodeAsc();

    /**
     * El servicio de lugares lo usa para devolver un 400 coherente cuando se crea
     * un lugar con un pais inexistente, en vez de dejar que reviente la restriccion
     * de clave foranea de Postgres (que el cliente veria como un 500).
     */
    boolean existsByCode(String code);
}
