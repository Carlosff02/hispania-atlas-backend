-- STREAMING_CHUNK:Creando tablas normalizadas, relaciones e inserciones iniciales para Mexico y Peru...

CREATE TABLE paises (
    code VARCHAR(2) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    capital VARCHAR(100) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    region VARCHAR(50) NOT NULL,
    desc_text VARCHAR(1000)
);

CREATE TABLE paises_series_historicas (
    id BIGSERIAL PRIMARY KEY,
    year INT NOT NULL,
    gdp_current DOUBLE PRECISION NOT NULL,
    gdp_pc DOUBLE PRECISION,
    pop DOUBLE PRECISION,
    growth DOUBLE PRECISION,
    inflation DOUBLE PRECISION,
    exports DOUBLE PRECISION,
    imports DOUBLE PRECISION,
    hdi DOUBLE PRECISION,
    debt DOUBLE PRECISION,
    trade DOUBLE PRECISION,
    pais_code VARCHAR(2) NOT NULL REFERENCES paises(code) ON DELETE CASCADE
);

-- Relacionamos la tabla lugares con paises si no estaba vinculada
ALTER TABLE lugares ADD COLUMN IF NOT EXISTS pais_code VARCHAR(2) REFERENCES paises(code);

-- Inserción inicial de México
INSERT INTO paises (code, name, capital, lat, lng, region, desc_text) 
VALUES ('MX', 'México', 'Ciudad de México', 23.6, -102.5, 'Norteamérica', 'Vasta república septentrional; cuna del muralismo y epicentro del barroco novohispano.');

INSERT INTO paises_series_historicas (year, gdp_current, gdp_pc, pop, growth, inflation, exports, imports, hdi, debt, trade, pais_code) 
VALUES (2026, 2121, 15779, 133.4, 2.4, 3.9, 610, 598, 0.781, 52, 1208, 'MX');

-- Inserción inicial de Perú
INSERT INTO paises (code, name, capital, lat, lng, region, desc_text) 
VALUES ('PE', 'Perú', 'Lima', -9.2, -75.0, 'Andina', 'Antiguo epicentro virreinal; hogar de la Escuela Cusqueña, el MALI y deslumbrantes danzas nacionales.');

INSERT INTO paises_series_historicas (year, gdp_current, gdp_pc, pop, growth, inflation, exports, imports, hdi, debt, trade, pais_code) 
VALUES (2026, 380, 10960, 34.4, 3.1, 2.6, 68.9, 52.3, 0.796, 34, 121.2, 'PE');

-- Actualizamos los lugares existentes de MX y PE para que apunten a su respectivo país
UPDATE lugares SET pais_code = 'MX' WHERE country = 'MX';
UPDATE lugares SET pais_code = 'PE' WHERE country = 'PE';