-- STREAMING_CHUNK:Creando script DDL inicial de Flyway para la tabla lugares...
CREATE TABLE lugares (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    country VARCHAR(2) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    category VARCHAR(50),
    icon VARCHAR(50),
    period VARCHAR(50),
    desc_text VARCHAR(1000),
    img VARCHAR(500)
);

-- Inserción de prueba inicial (ej. MALI y Machu Picchu)
INSERT INTO lugares (id, name, country, lat, lng, category, icon, period, desc_text, img) 
VALUES 
('mali', 'Museo de Arte de Lima', 'PE', -12.06, -77.037, 'Arte', 'palette', '1961', 'El MALI, ubicado en el histórico Palacio de la Exposición, alberga 3000 años de arte peruano.', 'https://exploortrip.com/wp-content/uploads/2025/11/Museo-de-Arte-de-Lima-Maliaaaa-900x450.jpg'),
('machu', 'Santuario de Machu Picchu', 'PE', -13.163, -72.545, 'Arqueología', 'landmark', 'Siglo XV', 'Magistral obra de arquitectura e ingeniería paisajística Inca.', 'https://www.peru-explorer.com/wp-content/uploads/machu-picchu-5.jpg');