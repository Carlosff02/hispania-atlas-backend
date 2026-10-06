-- ============================================================================
-- V7 — Normaliza las regiones heredadas y siembra los 19 países iberoamericanos
-- ============================================================================
--
-- Esta migración hace dos cosas y conviene separarlas, porque surgieron por
-- motivos distintos:
--
--   1. corrige los valores de `paises.region` que dejó el código viejo, y
--   2. inserta los países que faltaban.
--
--
-- 1) POR QUÉ HAY QUE NORMALIZAR `region`
-- ---------------------------------------------------------------------------
-- `Region` se guardaba antes con `@Enumerated(EnumType.STRING)`, que escribe el
-- NOMBRE de la constante. La columna se llenó de 'NORTEAMERICA', 'ANDINA' y
-- 'CONO_SUR' en vez de 'Norteamérica', 'Andina' y 'Cono Sur'.
--
-- El nombre de la constante no puede ser el dato: 'Cono Sur' lleva espacio y no
-- es un identificador de Java válido. Por eso `Region` lleva ahora su valor
-- textual aparte y lo convierte `RegionConvertidor`.
--
-- El síntoma era un 500 en GET /api/countries: Hibernate no encontraba el enum
-- para un valor que él mismo había escrito. Antes de este arreglo el endpoint
-- devolvía 200 con 'CONO_SUR' en el JSON y el frontend lo descartaba en silencio
-- (su `Region` no lo reconoce y cae en 'Andina'), así que el país se dibujaba
-- en la región equivocada sin que nada fallara.
--
--
-- 2) POR QUÉ FALTABAN PAÍSES
-- ---------------------------------------------------------------------------
-- V3 solo siembra México y Perú. Los otros 17 no estaban, y el frontend
-- (`hispania-atlas-ng/src/app/core/data/countries.ts`) ya define 19: los de
-- Norteamérica, Centroamérica, Caribe, Andina y Cono Sur.
--
-- Los datos de abajo están copiados de ese archivo, que es la fuente de verdad
-- del cliente. Si se cambia `countries.ts`, hay que cambiar esta migración.
--
--
-- CONTRATO DE IDEMPOTENCIA (importante)
-- ---------------------------------------------------------------------------
-- Cada país se inserta SOLO si su código no existe. No se decide "insertar
-- estos 15" ni "saltarse los 4 que ya hay", sino fila por fila:
--
--     WHERE NOT EXISTS (SELECT 1 FROM paises p WHERE p.code = v.code)
--
-- Así la misma migración sirve para las tres situaciones:
--
--   - base recién creada (V3 siembra MX y PE) → inserta los 17 que faltan;
--   - base con los 4 que había (MX, PE, AR, CL) → inserta los 15 que faltan;
--   - base ya completa → no inserta nada.
--
-- Si en vez se escribiera `WHERE code NOT IN ('MX','PE','AR','CL')`, en una base
-- nueva se saltaría los países que sí hay que sembrar.
--
--
-- POR QUÉ NO SE TOCAN LOS PAÍSES QUE YA EXISTEN
-- ---------------------------------------------------------------------------
-- Solo se les corrige `region`. No se sobrescriben name, capital, lat ni lng,
-- porque en una base real esos datos pueden estar corregidos a mano y esta
-- migración no tiene por qué ganar esa discusión.
--
--
-- `paises_series_historicas` NO se siembra aquí: los 19 países quedan sin
-- series, y `PaisServiceImpl` responde con una lista vacía en vez de fallar
-- (`seriesPorPais.getOrDefault(codigo, List.of())`). Los gráficos de los países
-- nuevos saldrán vacíos hasta que haya una migración de datos aparte.
--
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) Normalizar las regiones que el código viejo escribió.
-- ----------------------------------------------------------------------------
-- Los CINCO nombres de constante difieren del texto canónico, así que hacen
-- falta los cinco UPDATE. Cuatro se diferencian solo en las mayúsculas
-- ('CARIBE' -> 'Caribe', 'ANDINA' -> 'Andina'), y CONO_SUR además cambia el
-- guion bajo por un espacio y NORTEAMERICA y CENTROAMERICA llevan una 'é' que
-- el nombre de la constante no puede tener.
--
-- El mapeo es explícito y no un UPPER() genérico, por la misma razón que en
-- V1_1: un UPPER() silenciaría cualquier valor desconocido y dejaría datos
-- corruptos sin avisar. El guardia de más abajo es quien se encarga de
-- quejarse.
UPDATE paises SET region = 'Norteamérica' WHERE region = 'NORTEAMERICA';
UPDATE paises SET region = 'Centroamérica' WHERE region = 'CENTROAMERICA';
UPDATE paises SET region = 'Caribe'        WHERE region = 'CARIBE';
UPDATE paises SET region = 'Andina'        WHERE region = 'ANDINA';
UPDATE paises SET region = 'Cono Sur'      WHERE region = 'CONO_SUR';

-- ----------------------------------------------------------------------------
-- 2) Guardia: si queda algún valor que no es canónico, esto para aquí.
-- ----------------------------------------------------------------------------
-- El CHECK del punto 3 fallaría con un error genérico ("violated by some row")
-- que no dice qué valor es el culpable. Este bloque lo dice.
--
-- Flyway envuelve cada migración en una transacción, así que el RAISE deshace
-- también los UPDATE del punto 1: no queda la tabla a medio normalizar.
DO
$$
    DECLARE
        sobrantes TEXT;
    BEGIN
        SELECT string_agg(DISTINCT region, ' | ')
        INTO sobrantes
        FROM paises
        WHERE region NOT IN ('Norteamérica', 'Centroamérica', 'Caribe', 'Andina', 'Cono Sur');

        IF sobrantes IS NOT NULL THEN
            RAISE EXCEPTION
                'paises.region tiene valores que no son canonicos: %. '
                'Corregilos a mano o anadilos a la lista de esta migracion; '
                'la migracion se ha deshecho entera.', sobrantes;
        END IF;
    END
$$;

-- ----------------------------------------------------------------------------
-- 3) CHECK sobre `region`.
-- ----------------------------------------------------------------------------
-- `lugares.category` ya tiene uno, y fue precisamente su CHECK el que
-- destapó el fallo de V1/V2. `paises.region` no tenía ninguno, que es
-- justo lo que dejó que el código viejo escribiera 'CONO_SUR' sin que nadie se
-- enterara hasta que la lectura falló.
--
-- A partir de aqui un valor inventado lo rechaza Postgres en el INSERT, con un
-- error que nombra la fila, en vez de aparecer como un 500 en
-- GET /api/countries tres capas mas abajo.
--
-- El DROP IF EXISTS no es necesario para Flyway (cada migración corre una sola
-- vez), pero permite reejecutar este archivo a mano durante una depuración sin
-- tener que acordarse de cuál es el nombre de la restricción.
ALTER TABLE paises DROP CONSTRAINT IF EXISTS paises_region_check;

ALTER TABLE paises
    ADD CONSTRAINT paises_region_check
    CHECK (region IN ('Norteamérica', 'Centroamérica', 'Caribe', 'Andina', 'Cono Sur'));

-- ----------------------------------------------------------------------------
-- 4) Los 19 países de Iberoamérica, cada uno si y solo si no existe.
-- ----------------------------------------------------------------------------
-- Ordenados por región y, dentro de la región, como los tiene `countries.ts`.
INSERT INTO paises (code, name, capital, lat, lng, region, desc_text)
SELECT v.code, v.name, v.capital, v.lat, v.lng, v.region, v.desc_text
FROM (VALUES
          -- Norteamérica
          ('MX', 'México', 'Ciudad de México', 23.6, -102.5, 'Norteamérica',
           'Vasta república septentrional; cuna del muralismo y epicentro del barroco novohispano.'),

          -- Centroamérica
          ('GT', 'Guatemala', 'Ciudad de Guatemala', 15.7, -90.2, 'Centroamérica',
           'Corazón del mundo maya, país de montañas, volcanes y bosques profundos.'),
          ('SV', 'El Salvador', 'San Salvador', 13.7, -88.9, 'Centroamérica',
           'El Pulgarcito de América, pionero en adopción de criptomonedas.'),
          ('HN', 'Honduras', 'Tegucigalpa', 15.2, -86.2, 'Centroamérica',
           'Tierra montañosa con rica biodiversidad y barreras de coral caribeñas.'),
          ('NI', 'Nicaragua', 'Managua', 12.9, -85.2, 'Centroamérica',
           'Tierra de lagos y volcanes, con vasta reserva de biósfera.'),
          ('CR', 'Costa Rica', 'San José', 9.7, -83.7, 'Centroamérica',
           'Pionero mundial en ecoturismo, conservación y energía renovable.'),
          ('PA', 'Panamá', 'Ciudad de Panamá', 8.5, -80.0, 'Centroamérica',
           'Hub logístico global; puente del mundo que une dos océanos.'),

          -- Caribe
          ('CU', 'Cuba', 'La Habana', 21.5, -77.8, 'Caribe',
           'Famosa por su música, tabaco y arquitectura colonial.'),
          ('DO', 'Rep. Dominicana', 'Santo Domingo', 18.7, -70.1, 'Caribe',
           'Líder turístico del Caribe, hogar de hermosas playas y merengue.'),
          ('PR', 'Puerto Rico', 'San Juan', 18.2, -66.5, 'Caribe',
           'La Isla del Encanto, mezcla de herencia taína, española y africana.'),

          -- Andina
          ('VE', 'Venezuela', 'Caracas', 6.4, -66.5, 'Andina',
           'País de maravillas naturales, desde los Andes hasta el Salto Ángel.'),
          ('CO', 'Colombia', 'Bogotá', 4.5, -74.0, 'Andina',
           'Puerta de entrada a Sudamérica, rica en café y esmeraldas.'),
          ('EC', 'Ecuador', 'Quito', -1.8, -78.1, 'Andina',
           'El país de los cuatro mundos, guardián de las Islas Galápagos.'),
          ('PE', 'Perú', 'Lima', -9.2, -75.0, 'Andina',
           'Antiguo epicentro virreinal; capital gastronómica de América.'),
          ('BO', 'Bolivia', 'Sucre / La Paz', -16.2, -68.1, 'Andina',
           'Corazón de Sudamérica, hogar del místico Salar de Uyuni.'),

          -- Cono Sur
          ('PY', 'Paraguay', 'Asunción', -23.4, -58.3, 'Cono Sur',
           'Tierra bilingüe impulsada por inmensas represas hidroeléctricas.'),
          ('UY', 'Uruguay', 'Montevideo', -32.5, -55.7, 'Cono Sur',
           'Nación austral de amplias llanuras y altos estándares sociales.'),
          ('AR', 'Argentina', 'Buenos Aires', -35.5, -64.5, 'Cono Sur',
           'Polo irradiador de cultura, sede de la sensualidad del tango.'),
          ('CL', 'Chile', 'Santiago', -35.6, -71.5, 'Cono Sur',
           'Larga y angosta faja de tierra de contrastes extremos.')
     ) AS v (code, name, capital, lat, lng, region, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM paises p WHERE p.code = v.code);
