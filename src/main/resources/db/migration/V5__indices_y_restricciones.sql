-- V5:_indices_y_restricciones
--
-- El esquema creado en V1..V4 funciona, pero deja tres problemas que solo aparecen
-- cuando ya hay datos suficientes:
--
--  1. Ninguna columna de clave foranea tiene indice. `lugares.pais_code` y
--     `paises_series_historicas.pais_code` se resolven con un seq scan cada vez que
--     Hibernate carga los `@OneToMany` de un pais. Con 17 lugares no se nota, pero
--     GET /api/countries dispara un scan por pais (patron N+1) y la migracion de
--     V4 --que inserta 8 filas de una-- empeora con cada lote nuevo.
--
--  2. `paises_series_historicas` no impide dos filas para el mismo (pais, anio).
--     El frontend asume que el ULTIMO elemento de `seriesHistoricas` es el mas
--     reciente (`dto.seriesHistoricas[length - 1]`); con duplicados esa suposicion
--     es falsa y las tarjetas macroeconomicas muestran un valor arbitrario.
--
--  3. `lugares.country` duplica `lugares.pais_code`. Las dos columnas pueden quedar
--     desincronizadas sin que nada lo impida, y el mapa entiende el pais por
--     `country` mientras que la relacion JPA usa `pais_code`.
--
-- Se verificó antes de escribir esta migración que los datos actuales cumplen las
-- tres condiciones, así que las restricciones se pueden añadir sin riesgo.

-- 1. Indices para las claves foraneas
CREATE INDEX IF NOT EXISTS idx_lugares_pais_code
    ON lugares (pais_code);

CREATE INDEX IF NOT EXISTS idx_series_historicas_pais_code
    ON paises_series_historicas (pais_code);

-- 2. Un unico registro por pais y anio
ALTER TABLE paises_series_historicas
    ADD CONSTRAINT uq_series_pais_year UNIQUE (pais_code, year);

-- 3. `country` no puede contradecir a la clave foranea, y un lugar siempre
--    pertenece a un pais (antes la columna admitia NULL sin restriccion).
ALTER TABLE lugares
    ADD CONSTRAINT ck_lugares_country_coincide CHECK (country = pais_code);

ALTER TABLE lugares
    ALTER COLUMN pais_code SET NOT NULL;
