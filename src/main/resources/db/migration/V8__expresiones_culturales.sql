-- ============================================================================
--  V8: expresiones culturales por pais, con su cola de moderacion.
-- ============================================================================
--
--  QUE ES Y QUE NO ES ESTA TABLA
--
--  El frontend la daba por construida con una constante, art-data.ts, y el
--  comentario de ahi decia que era "contenido editorial estatico". Para moverla
--  hubo que decidir antes que cosa se esta guardando, y la respuesta cambia la
--  forma de la tabla.
--
--  No son OBRAS DE ARTE. La seccion se titula "Bellas Artes y Expresion", y las
--  cuatro entradas que ya existian lo confirman:
--
--      La Escuela Cusquena   | Pintura Virreinal | Peru
--      El Muralismo           | Pintura Moderna   | Mexico
--      La Marinera            | Danza Tradicional  | Peru
--      El Tango               | Danza y Musica     | Argentina
--
--  "La Marinera" y "El Tango" no son cosas que se visiten: son danzas. Y una danza no
--  tiene latitud ni longitud, ni se puede visitar, ni tiene un periodo de
--  fundacion. Meterla en `lugares` obligaria a falsificar esas tres columnas, y
--  `lugares` las exige como NOT NULL. Por eso esta tabla es aparte y no un
--  `lugares` mas.
--
--  Lo que se guarda es una EXPRESION CULTURAL de un pais: una tradicion, un
--  movimiento artistico, un genero musical, una tecnica. Es patrimonio
--  inmaterial. El nombre `expresiones_culturales` y no `obras_arte` por eso.
--
--  LA DIFFERENCIA CON `lugares`, EN UNA TABLA
--
--    lugares          | expresiones_culturales
--    -----------------+--------------------------
--    se visita        | se practica / se escucha
--    lat, lng NOT NULL| no tiene coordenadas
--    id = slug        | id = slug
--    country VARCHAR(2)| pais_code VARCHAR(2) FK
--
--  `pais_code` es FK a `paises(code)` y NO texto libre. En el frontend
--  `ObraArte.country` era un string suelto ('Peru', 'Mexico') que se mostraba en
--  la tarjeta y no se usaba para nada mas: no se filtraba, no se cruzaba, no se
--  relacionaba. Ese patron es exactamente el que produjo el bug de
--  `paises.region` ('CONO_SUR', 'NORTEAMERICA'), y que nadie lo notara durante
--  meses es precisamente porque el dato no se usaba en ninguna consulta. Ahi el
--  dato no puede quedar suelto: o es codigo de pais o no sirve.
--
--  LA CATEGORIA ES UN ENUM, Y POR QUE NO TEXTO LIBRE
--
--  Las cuatro categorias originales eran 'Pintura Virreinal', 'Pintura Moderna',
--  'Danza Tradicional' y 'Danza y Musica'. Fallan como taxonomia por dos motivos:
--
--    - No son categorias de LUGAR. `lugares.category` tiene su propio CHECK de
--      nueve valores, y esos nueve describen un tipo de sitio (ARTE, DANZA,
--      ARQUEOLOGIA...), no una disciplina ni una epoca.
--    - Tampoco servian como taxonomia cerrada. El filtro del frontend era
--      dinamico (`new Set(artData.map(a => a.cat))`), o sea que el filtro se
--      armaba con lo que hubiera en la constante. Bastaba un 'Pintura moderna'
--      con minuscula para que la tarjeta apareciera en un grupo y su gemela en
--      otro.
--
--  Se elige un enum con CHECK por coherencia con `lugares.category` y con
--  `paises.region`: la garantia esta en la base, no en que nadie escriba mal.
--  El precio es que anadir 'AFROAMERICANA' exige una migracion, y conviene saber
--  que ese es el coste en vez de descubrirlo cuando el listado devuelva un filtro
--  con una categoria mas de la que el enum conoce.
--
--  (La categoria sigue siendo la disciplina, no la epoca: 'Pintura Moderna' es una
--  corriente dentro de PINTURA, no una categoria mas.)
--
-- ============================================================================


CREATE TABLE expresiones_culturales (
    id VARCHAR(100) PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    categoria VARCHAR(50) NOT NULL,
    pais_code VARCHAR(2) NOT NULL REFERENCES paises(code) ON DELETE CASCADE,
    desc_text VARCHAR(1000) NOT NULL,
    -- Nombre de fichero dentro de static/expresiones/, NO una URL. Las cuatro
    -- imagenes que tenia el frontend eran enlaces a Bing y Pinterest: se rompen,
    -- hacen hotlink a servidores que lo bloquean, y su licencia se desconoce.
    -- Se sirven desde este proyecto.
    imagen VARCHAR(100),
    -- Fuente y licencia de la imagen. NO es opcional en cuanto haya una imagen:
    -- las imagenes de Wikimedia Commons lo exigen, y sin esta columna el
--  proyecto tiene material de terceros sin informar a nadie.
    creditos VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_expresiones_categoria CHECK (categoria IN (
        'PINTURA',
        'ESCULTURA',
        'DANZA',
        'MUSICA',
        'TEATRO',
        'LITERATURA',
        'ARTESANIA',
        'TRADICION_ORAL',
        'GASTRONOMIA'
    )),

    -- Una expresion por pais y categoria: no tiene sentido la Escuela Cusquena y
    -- otra pintura virreinal como entradas separadas, pero tampoco dos danzas
    -- tradicionales del mismo pais sin distinguir.
    CONSTRAINT uq_expresiones_pais_categoria UNIQUE (pais_code, categoria),

    -- Una imagen sin creditos es material de origen desconocido. Si `imagen` esta
    -- puesta, `creditos` tiene que estarlo tambien.
    CONSTRAINT ck_expresiones_imagen_creditos CHECK (
        imagen IS NULL OR creditos IS NOT NULL
    )
);


CREATE INDEX idx_expresiones_pais ON expresiones_culturales(pais_code);
CREATE INDEX idx_expresiones_categoria ON expresiones_culturales(categoria);


-- ============================================================================
--  Cola de moderacion
-- ============================================================================
--
--  Misma logica que `propuestas_lugar`: un USUARIO propone, un COLABORADOR
--  aprueba o rechaza, y al aprobar se inserta en `expresiones_culturales`.
--
--  No hay columna `id` aqui, y es deliberado: el slug legible
--  ('el_tango', 'la_marinera') se genera al aprobar, que es cuando se puede
--  comprobar que no choca con ninguna existente. Pedirlo al proponer solo genera
--  errores por duplicados que el usuario no puede ver. Es lo mismo que hace
--  `propuestas_lugar`, no una variante.
--
--  La diferencia con la cola de lugares es que aqui NO se propone un icono ni un
--  periodo: una expresion cultural no se visita ni tiene fundacion. Los campos
--  que no aplican se omiten en vez de rellenarse con cadenas vacias.
--
-- ============================================================================

CREATE TABLE propuestas_expresion (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    categoria VARCHAR(50) NOT NULL,
    country VARCHAR(2) NOT NULL,
    desc_text VARCHAR(1000) NOT NULL,
    imagen VARCHAR(100),
    creditos VARCHAR(300),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    propuesto_por BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    revisado_por BIGINT REFERENCES usuarios(id),
    revisado_at TIMESTAMPTZ,
    motivo_rechazo VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_propuestas_expresion_estado CHECK (estado IN (
        'PENDIENTE', 'APROBADA', 'RECHAZADA'
    )),

    -- Misma lista que `expresiones_culturales`. Si las dos se separan, la cola
    -- acepta una categoria que al aprobar va a fallar contra el CHECK de destino,
    -- y el usuario ve un error en vez de un rechazo.
    CONSTRAINT ck_propuestas_expresion_categoria CHECK (categoria IN (
        'PINTURA',
        'ESCULTURA',
        'DANZA',
        'MUSICA',
        'TEATRO',
        'LITERATURA',
        'ARTESANIA',
        'TRADICION_ORAL',
        'GASTRONOMIA'
    )),

    -- FK, no CHECK con subconsulta: PostgreSQL no admite `IN (SELECT ...)` dentro
    -- de un CHECK, y ademas la FK es lo que de verdad protege. El CHECK solo se
    -- puede usar con valores literales.
    CONSTRAINT fk_propuestas_expresion_pais FOREIGN KEY (country)
        REFERENCES paises(code) ON DELETE CASCADE
);

CREATE INDEX idx_propuestas_expresion_estado ON propuestas_expresion(estado);


-- ============================================================================
--  Las cuatro expresiones que ya existian en el frontend
-- ============================================================================
--
--  Son las cuatro de `art-data.ts`, y se siembran tal cual para que al cambiar el
--  frontend a la API la seccion no se vacie. Sus categorias SI cambian, porque las
--  cuatro no son validas en el enum:
--
--      'Pintura Virreinal'  -> PINTURA  (es una disciplina, no una epoca)
--      'Pintura Moderna'    -> PINTURA
--      'Danza Tradicional'   -> DANZA
--      'Danza y Musica'      -> DANZA
--
--  Este es el punto que obliga a decidir la taxonomia antes de sembrar: con el
--  enum, 'Danza y Musica' tiene que ir a DANZA, y se pierde el matiz de que el
--  tango es tambien musica. Es una perdida consciente; la alternativa (dejar
--  texto libre) es justo lo que hacia que el filtro del frontend mostrara
--  'Pintura moderna' y 'Pintura Moderna' como grupos distintos.
--
--  `id` es un slug, como el de `lugares`, porque va en la URL y en el `alt` de la
--  tarjeta: un id numerico no dice nada.
--
--  LAS IMAGENES
--
--  Las cuatro imagenes originales eran enlaces a Bing y Pinterest. Se sustituyen
--  por ficheros en `static/expresiones/`, con su licencia y su autor en `creditos`.
--  De las cuatro, ninguna permitia conocer su licencia: eran miniaturas de un
--  buscador, y hacer hotlink a un buscador es usar material de terceros sin
--  permiso.
--
--  Licencias distintas, y por eso la columna existe: una es dominio publico y las
--  otras dos son Creative BY con clausula share-alike. Compartir la imagen no obliga a
--  publicar el codigo, pero la atribucion si es obligatoria, asi que va en la base
--  y no en un comentario del HTML.
--
-- ============================================================================

INSERT INTO expresiones_culturales
    (id, titulo, categoria, pais_code, desc_text, imagen, creditos)
VALUES
    ('la_escuela_cusquena',
     'La Escuela Cusqueña',
     'PINTURA',
     'PE',
     'Pintura colonial desarrollada en Cusco entre los siglos XVII y XVIII. Fuso las tecnicas de la pintura europea con el mundo simbolico andino, con un uso intensivo del brocateado en oro y una iconografia religiosa de raiz local.',
     'escuela-cusquena.jpg',
     'Angelino Medoro, "Coronation of the Virgin of the Rosary" (c. 1585). Dominio publico. Wikimedia Commons.'),

    ('el_muralismo',
     'El Muralismo',
     'PINTURA',
     'MX',
     'Movimiento pictorico mexicano de caracter indigenista, social y monumental, consolidado con los muralistas de las dos decadas de 1920 y 1930. Busca educar a las masas a traves de grandes frescos en espacios publicos.',
     'muralismo-palacio-nacional.jpg',
     'Luis Alvaz, "Tableros murales de Diego Rivera en el Palacio Nacional". CC BY-SA 4.0. Wikimedia Commons.'),

    ('la_marinera',
     'La Marinera',
     'DANZA',
     'PE',
     'Danza popular peruana de pareja suelta, declarada patrimonio cultural de la nacion. Representa el galanteo con el uso del panuelo, con raiz mestiza, y suele acompanarse de un caballo de paso.',
     'marinera-paso.jpg',
     'Tomas Sobek, "Marinera dance with Peruvian Paso horse". CC BY 2.0. Wikimedia Commons.'),

    ('el_tango',
     'El Tango',
     'DANZA',
     'AR',
     'Genero musical y baile nacido en los arrabales del Rio de la Plata a finales del siglo XIX. La forma de ensayo arrabalera dio paso al baileenario, con el bandoneon como instrumento distintivo.',
     'el-tango.jpg',
     'Jenny Mealing, "Tango-Show-Buenos-Aires-01". CC BY 2.0. Wikimedia Commons.');

