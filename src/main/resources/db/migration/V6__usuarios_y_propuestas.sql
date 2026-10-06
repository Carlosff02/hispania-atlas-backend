-- ============================================================================
-- V6 — Usuarios, roles y propuestas de nuevos lugares
-- ============================================================================

-- ----------------------------------------------------------------------------
-- usuarios
-- ----------------------------------------------------------------------------
-- `password_hash` guarda el hash BCrypt, nunca la contraseña en claro. La
-- columna se llama *_hash a propósito: es el nombre el que evita que alguien se
-- confunda y escriba la contraseña directamente.
--
-- NO se siembra ningún usuario en esta migración, y es deliberado: una cuenta
-- de arranque con contraseña fija (aunque fuera solo un hash) quedaría escrita
-- en el historial de git para siempre. El primer ADMIN_SISTEMA lo crea el
-- arranque de la aplicación a partir de variables de entorno
-- (app.bootstrap.admin-username / app.bootstrap.admin-password).
CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nombre VARCHAR(100),
    rol VARCHAR(20) NOT NULL DEFAULT 'USUARIO',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_usuarios_username UNIQUE (username),
    CONSTRAINT uq_usuarios_email UNIQUE (email),

    -- El rol se valida tambien en Java (enum Rol). Duplicar la lista aqui evita
    -- que un INSERT manual deje un rol que la aplicacion no sepa interpretar.
    CONSTRAINT ck_usuarios_rol CHECK (rol IN (
        'USUARIO', 'COLABORADOR', 'ADMIN', 'ADMIN_SISTEMA'
    )),

    -- El login se normaliza a minusculas en Java antes de guardar. Este CHECK es
    -- la red de seguridad: si alguien inserta 'Ana' a mano, el CHECK lo rechaza
    -- en vez de crear una cuenta que no coincide con la de 'ana'.
    CONSTRAINT ck_usuarios_username_formato CHECK (username ~ '^[a-z0-9_]{3,30}$')
);

-- El login es lo mas consultado de la tabla; la UNIQUE ya genera indice, pero
-- este cubre el filtro habitual "listar usuarios por rol" de la administracion.
CREATE INDEX idx_usuarios_rol ON usuarios (rol);
CREATE INDEX idx_usuarios_activo ON usuarios (activo);


-- ----------------------------------------------------------------------------
-- propuestas_lugar
-- ----------------------------------------------------------------------------
-- Un USUARIO propone un lugar; un COLABORADOR lo aprueba o lo rechaza. Al
-- aprobar se inserta una fila en `lugares`, asi que aqui NO hay columna `id`:
-- el identificador legible ("museo_larco") se genera en el momento de aprobar,
-- cuando se puede comprobar que no choque con ninguno existente. Pedirlo al
-- proponer solo generaria errores por duplicados que el usuario no puede ver.
CREATE TABLE propuestas_lugar (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    country VARCHAR(2) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    category VARCHAR(50) NOT NULL,
    icon VARCHAR(50),
    period VARCHAR(50),
    desc_text VARCHAR(1000),
    img VARCHAR(500),

    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    propuesto_por BIGINT NOT NULL,
    revisado_por BIGINT,
    revisado_at TIMESTAMPTZ,
    motivo_rechazo VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- No hay ON DELETE CASCADE ni SET NULL: la propuesta conserva siempre a su
    -- autor. Por eso los usuarios no se borran, se desactivan (`activo = false`),
    -- y asi el historial de "quien propuso esto" no se pierde. Un DELETE sobre un
    -- usuario con propuestas queda rechazado por la FK, que es lo correcto.
    CONSTRAINT fk_propuestas_propuesto_por FOREIGN KEY (propuesto_por)
        REFERENCES usuarios (id),
    CONSTRAINT fk_propuestas_revisado_por FOREIGN KEY (revisado_por)
        REFERENCES usuarios (id) ON DELETE SET NULL,

    -- El pais debe existir: es la misma FK que usa la tabla `lugares`.
    CONSTRAINT fk_propuestas_pais FOREIGN KEY (country)
        REFERENCES paises (code),

    CONSTRAINT ck_propuestas_estado CHECK (estado IN (
        'PENDIENTE', 'APROBADA', 'RECHAZADA'
    )),

    -- Mismas 9 categorias que la tabla `lugares` (V2).
    CONSTRAINT ck_propuestas_category CHECK (category IN (
        'ARTE',
        'DANZA',
        'ARQUEOLOGIA',
        'PATRIMONIO',
        'HISTORICO',
        'INFRAESTRUCTURA',
        'PAISAJE_NATURAL',
        'ACADEMICO',
        'GASTRONOMICO'
    )),

    -- Coordenadas dentro de los rangos del planeta.
    CONSTRAINT ck_propuestas_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_propuestas_lng CHECK (lng BETWEEN -180 AND 180),

    -- Integridad del flujo de moderacion. Rechazar sin explicar por que es la
    -- causa habitual de queja ("no me dicen nada"), asi que se exige motivo.
    -- Equivale a: "es RECHAZADA si y solo si tiene motivo".
    CONSTRAINT ck_propuestas_rechazo_coherente CHECK (
        (estado = 'RECHAZADA') = (motivo_rechazo IS NOT NULL)
    ),

    -- Si esta revisada, tiene revisor y fecha; si sigue pendiente, no.
    CONSTRAINT ck_propuestas_revision_coherente CHECK (
        (estado = 'PENDIENTE') = (revisado_at IS NULL)
    )
);

-- Cola de moderacion: se consulta por estado y orden de llegada.
CREATE INDEX idx_propuestas_estado ON propuestas_lugar (estado, created_at);
-- "Mis propuestas": se consulta por autor.
CREATE INDEX idx_propuestas_propuesto_por ON propuestas_lugar (propuesto_por);
