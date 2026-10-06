-- STREAMING_CHUNK:Creando la migración V2 para añadir restricciones de categorías en la base de datos...
-- Si ya tienes la tabla creada en V1, alteramos la tabla para agregar el CHECK constraint de las nuevas categorías

ALTER TABLE lugares DROP CONSTRAINT IF EXISTS lugares_category_check;

ALTER TABLE lugares ADD CONSTRAINT lugares_category_check CHECK (category IN (
    'ARTE', 
    'DANZA', 
    'ARQUEOLOGIA', 
    'PATRIMONIO', 
    'HISTORICO', 
    'INFRAESTRUCTURA', 
    'PAISAJE_NATURAL', 
    'ACADEMICO', 
    'GASTRONOMICO'
));