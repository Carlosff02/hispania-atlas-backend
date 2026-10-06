-- ============================================================================
--  V9: Los CHECK de coherencia que le faltaban a propuestas_expresion
-- ============================================================================
--
--  Por que una migracion nueva y no editar V8:
--
--  V8 ya esta aplicada en las bases de desarrollo, y Flyway valida el checksum
--  de cada migracion guardada. Editarla haria que `hispania_db` dejara de
--  arrancar con un "Validate failed: checksum mismatch" hasta que alguien
--  borrase el esquema. Por eso V8 se queda como esta y lo que falta llega aqui.
--
--  Que falta:
--
--  V6 creo la cola de lugares con dos CHECK de coherencia que en V8 no se
--  replicaron. Son la red que hay debajo del servicio, y su ausencia la
--  detectaron las pruebas de integracion: la base aceptaba un
--  rechazo sin motivo y una aprobacion sin revisor, justo los dos estados que
--  el servicio evita a proposito.
--
--  Sigue siendo el servicio quien protege el dato en el camino normal, porque
--  un 409 con el motivo del choque le dice algo a quien lo provoca y un CHECK
--  solo revienta. Esto es para lo que no pasa por el servicio: un script de
--  correccion, un UPDATE manual, una futura tarea de arranque.
--

ALTER TABLE propuestas_expresion
    -- Equivale a: "es RECHAZADA si y solo si tiene motivo".
    ADD CONSTRAINT ck_propuestas_expresion_rechazo_coherente CHECK (
        (estado = 'RECHAZADA') = (motivo_rechazo IS NOT NULL)
    ),

    -- Si esta revisada, tiene revisor y fecha; si sigue pendiente, no. Es el
    -- mismo invariante de V6, con el par revisor/fecha en vez de solo la fecha.
    ADD CONSTRAINT ck_propuestas_expresion_revision_coherente CHECK (
        (estado = 'PENDIENTE') = (revisado_at IS NULL AND revisado_por IS NULL)
    );
