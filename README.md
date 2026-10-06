# Hispania Atlas — API REST (Spring Boot)

Migración del backend de **Quarkus 3.39 + Panache** (que sigue en
`hispania-backend/patrimonio-backend`) a **Spring Boot 4.0.8**, reorganizado en capas
y manteniendo el contrato `/api/countries` y `/api/places** que consume el frontend.

## Arquitectura

Las tres capas se conocen solo hacia abajo. `presentation` no importa `persistence`,
`services` no importa nada de `presentation`, y el mapeo entidad ↔ DTO es el único
punto donde se cruzan.

```
com.hispania
├── PatrimonioApplication
│
├── presentation/                    <- capa HTTP
│   ├── controllers/                 @RestController, translating HTTP <-> service
│   │   ├── PaisController
│   │   └── LugarController
│   └── dto/
│       ├── request/                 records de entrada + Bean Validation
│       │   ├── PaisRequest          con grupos OnCreate / OnUpdate
│       │   └── LugarRequest         el id solo se exige al crear
│       └── response/                records de salida (inmutables)
│           ├── ApiError             cuerpo unico de error
│           ├── PaisResponse
│           ├── LugarResponse
│           └── SerieHistoricaResponse
│
├── services/                        <- capa de negocio
│   ├── interfaces/                  el contrato (implementado por test doubles)
│   │   ├── PaisService
│   │   └── LugarService
│   ├── impl/                        la logica y las transacciones
│   │   ├── PaisServiceImpl
│   │   └── LugarServiceImpl
│   └── mapper/
│       └── ResponseMapper           entidad <-> DTO
│
├── persistence/                     <- capa de datos
│   ├── entity/                      entidades JPA planas
│   │   ├── Pais                     + enums Region, CategoriaLugar
│   │   ├── Lugar
│   │   └── PaisSerieHistorica
│   └── repository/                  Spring Data, sin implementacion escrita
│       ├── PaisRepository
│       ├── LugarRepository
│       └── PaisSerieHistoricaRepository
│
├── config/                          CORS y propiedades tipadas
└── exception/                       excepciones de negocio + manejador global
```

> `services/interface/` no es una opción: `interface` es palabra reservada en Java y
> `package com.hispania.services.interface;` no compila. Por eso la carpeta se llama
> `interfaces`.

### Por qué los DTO son `record` y las entidades no

Un `record` de Java es inmutable y el compilador genera los accesores, el constructor
canónico y la deserialización por nombre de componente. Para un DTO de entrada o
salida, eso es exactamente lo que se quiere y evita 60 líneas de getters y setters.

Las entidades JPA, en cambio, **no** pueden ser `record`: Hibernate necesita un
constructor sin argumentos y mutar los campos con setters para cargar el estado.
Por eso llevan constructor protegido vacío y setters.

### Por qué el mapeo vive en `services` y no en los DTO

La versión de Quarkus tenía `static fromEntity(...)` dentro de los DTO, lo que hace que
`presentation` dependa de `persistence`. Aquí el mapeo está en `ResponseMapper`, y los
DTO son records sin ningún método que reciba entidades. Así se puede cambiar el modelo
de datos sin que se rompa la capa HTTP, y al revés.

### Por qué `Region` no puede usar su nombre como valor

`Region` es el único enum del proyecto cuyo valor persistido **no** es el nombre de la
constante, y el motivo es concreto: el texto no puede ser un identificador de Java.

| | Constante | Valor en base y JSON |
| --- | --- | --- |
| 1 | `NORTEAMERICA` | `Norteamérica` |
| 2 | `CENTROAMERICA` | `Centroamérica` |
| 3 | `CARIBE` | `Caribe` |
| 4 | `ANDINA` | `Andina` |
| 5 | `CONO_SUR` | `Cono Sur` ← lleva espacio |

Esos textos son los que escribieron las migraciones (`V3`) y los que consume el
frontend, que valida contra una lista cerrada en `countries.service.ts` y **avisa en
consola y cae en `Andina`** si no coincide. Renombrar las constantes a `Norteamérica`
habría dejado `Cono Sur` sin poder existir, y `NORTEAMERICA` en el JSON habría pintado
México como "Andina" sin ningún error visible.

De ahí el par `Region` + `RegionConvertidor`:

- `Region.getValor()` lleva el texto, con `@JsonValue` para la salida y `@JsonCreator`
  para la entrada.
- `RegionConvertidor` es un `AttributeConverter` para JPA, de modo que la columna
  `VARCHAR(50)` guarda y lee el texto, no el ordinal ni el nombre de la constante.
- `ResponseMapper` llama a `getValor()` y no a `.name()`.

Sin el converter, `@Enumerated(EnumType.STRING)` leía `'Norteamérica'` buscando una
constante llamada así y fallaba: **`GET /api/countries` devolvía 500** y, con solo
arreglar eso, el JSON habría seguido mandando `NORTEAMERICA` y el frontend habría
seguido dibujando México en la región equivocada sin que nada fallara. Los dos fallos
estaban en el mismo sitio.

`CategoriaLugar` **sí** usa su nombre como valor (`ARTE`, `DANZA`, …) y no necesita
converter: es la razón por la que las dos mitades del enum parecen inconsistentes, y
por lo que `RegionConvertidorTest` fija el contrato de texto en las dos direcciones.

## Requisitos

- **JDK 25** (compila con `--release 25`; el proyecto funciona con 17+)
- **PostgreSQL 17** con la base `hispania_db` ya creada
- Maven no hace falta instalado: se usa el wrapper (`./mvnw`)

## Puesta en marcha

```bash
# 1. Base de datos (una sola vez)
#    La base hispania_db debe existir y tener el usuario postgres con contraseña.
psql -U postgres -c "CREATE DATABASE hispania_db;"

# 2. Credenciales — OBLIGATORIO
#    Ni la contraseña de la base ni la clave del JWT tienen valor por defecto en
#    application.properties, así que sin ellas la aplicación aborta al arrancar.
#    Es deliberado: un default acabaría escrito en el historial de git.
copy .env.example .env
#    En PowerShell:
#      $env:DB_URL       = "jdbc:postgresql://localhost:5432/hispania_db"
#      $env:DB_USER      = "postgres"
#      $env:DB_PASSWORD  = "tu-contrasena-real"
#      $env:JWT_SECRET   = "clave-aleatoria-de-openssl-rand-base64-48"

# 3. Primer administrador del sistema (opcional pero recomendado)
#    La migración NO siembra ninguna cuenta. Si defines estas tres variables y no
#    hay ningún ADMIN_SISTEMA, el arranque crea la cuenta inicial. Luego puedes
#    vaciarlas: la cuenta ya existe y no se vuelve a crear.
#      $env:ADMIN_USERNAME = "root"
#      $env:ADMIN_EMAIL    = "root@localhost"
#      $env:ADMIN_PASSWORD = "tu-contrasena-fuerte"

# 4. Arrancar
./mvnw spring-boot:run          # http://localhost:8080
```

`DB_URL` y `DB_USER` sí tienen default (`localhost:5432/hispania_db` y `postgres`),
porque no son secretos. `DB_PASSWORD` y `JWT_SECRET` son obligatorias, y si faltan
la aplicación aborta al arrancar con un mensaje que dice cuál falta, en lugar de
dejar que fallo más tarde y en un sitio menos evidente:

```
Falta la variable de entorno DB_PASSWORD.
  - Copia la plantilla:      copy .env.example .env
  - En PowerShell:           $env:DB_PASSWORD = "<tu contrasena>"
```

Sin `JWT_SECRET` el síntoma sería peor: la aplicación arrancaría y todos los
inicios de sesión fallarían, porque ningún token se podría firmar.

Flyway crea el esquema y aplica las migraciones al arrancar. No hay que importar
nada a mano.

## Usuarios, roles y propuestas

La jerarquía de permisos es **lineal**: cada rol incluye todo lo que puede el
anterior, así que no hace falta enumerar permisos, solo comparar rangos.

| Rol | Rango | Puede además de lo anterior |
| --- | --- | --- |
| `USUARIO` | 0 | ver el mapa, filtrar, **proponer** lugares |
| `COLABORADOR` | 1 | crear y editar lugares; **aprobar o rechazar** propuestas, incluidas las suyas |
| `ADMIN` | 2 | **borrar** lugares; promover a colaborador; editar países |
| `ADMIN_SISTEMA` | 3 | promover o degradar administradores del sistema; desactivar cuentas |

La tabla no es solo "cada rol puede más": hay dos filas que rompen el patrón, y las
dos a propósito.

**Proponer deja de escalar hacia arriba.** `ADMIN` y `ADMIN_SISTEMA` **no**
proponen. Pueden crear el lugar con `POST /api/places`, así que proponerlo sería un
rodeo que además les impone esperar a que alguien lo apruebe. El `COLABORADOR` sí
puede seguir proponiendo aunque también pueda crear directamente: las dos vías son
suyas y nada obliga a quitarle una.

Es la **única regla del módulo que excluye en lugar de incluir**, y por eso no se
expresa con el `puede` de umbral sino con un método propio. El riesgo es concreto:
escribiéndola como `puede(authentication, 'COLABORADOR')`, que es el patrón de las
demás anotaciones, entrarían justo los dos roles que deben quedar fuera.

**El borrado no lo hereda el colaborador.** Es el único umbral de escritura que no
coincide con el de la creación, y sube a `ADMIN` porque el borrado no se puede
deshacer: `lugares` no tiene columna de baja lógica y ninguna clave foránea apunta a
`lugares.id` —las series históricas cuelgan de `paises`—, así que el `DELETE` no
dispara cascada ni error y la fila se pierde sin aviso. Crear y editar se corrigen;
borrar, no.

Con el umbral anterior existía además un recorrido de tres pasos: un `COLABORADOR`
podía proponer un sitio, moderarlo él mismo y borrarlo, y se iba un lugar curado sin
que nadie más hubiera intervenido.

### Dónde se hace esto en la interfaz

Los tres endpoints de escritura de `LugarController` tienen pantalla propia en
`hispania-atlas-ng`: la vista `/lugares` ("Gestionar lugares", en el menú de
cuenta), con `rolGuard('COLABORADOR')` porque ese es el umbral del `POST` y del
`PUT`. El botón de eliminar solo se pinta a partir de `ADMIN` y exige un segundo
clic, ya que el `DELETE` no tiene vuelta atrás.

Dos detalles de esa vista que evitan surprises en pruebas manuales:

- El `id` es obligatorio en el alta y lo elige quien escribe, porque a diferencia
  de las propuestas aquí no hay moderación que lo genere después. El formulario
  ofrece un botón "Generar" que aplica el mismo criterio que `slug()` en
  `PropuestaServiceImpl`, y avisa si el nombre no sirve.
- Tras cada escritura la vista llama a `AppStore.recargarLugares()`, que vuelve a
  pedir la lista al backend. Sin eso, `places$` está cacheado con `shareReplay` y
  el alta se vería en la pantalla de gestión pero no en el mapa hasta recargar la
  página.

La comprobación de permisos sigue siendo la de aquí: la vista replica los
umbrales para no pintar botones inútiles, y si alguien llama al endpoint a mano
la respuesta es la misma de siempre, 403.

### La moderación no se queda sin nadie

`ADMIN` **sigue moderando** aunque no pueda proponer. Si se le quitara, dejaría de
haber hueco cuando todavía no hay ningún colaborador dado de alta: las propuestas se
acumularían sin que nadie las atendiera, y la única cuenta con permiso para crearlas
sería justo la que no podría gestionarlas.

La autorevisión, en cambio, se **acota** en vez de levantarse: se sigue bloqueando al
`USUARIO`, que no puede crear lugares por la vía directa y por tanto no tiene otra
manera de resolver la suya. Para `COLABORADOR`+ desaparece, y no es un agujero:
auto-aprobarte no da ningún poder que no tuvieras ya creando ese mismo lugar. El caso
que resuelve es real: alguien propuso como `USUARIO`, le ascendieron y su propuesta
seguía pendiente sin que nadie pudiera cerrarla.

Las tres reglas que protegen la administración, todas en
`AdminUsuarioServiceImpl`:

1. **Rango superior.** Solo se modifica a quien tiene un rango menor. Por eso dos
   `ADMIN` no pueden tocarse entre sí.
2. **No delegar un poder que no se tiene.** Un `ADMIN` no puede promover a
   `ADMIN_SISTEMA`; si pudiera, bastaría con comprometer una cuenta intermedia.
3. **Nadie se modifica a sí mismo.** Evita degradaciones accidentales.

Ninguna de las tres se resuelve solo con `@PreAuthorize`: son relaciones entre dos
cuentas, no permisos sobre una ruta. La anotación se queda con la puerta
("esto exige `ADMIN`") y el servicio decide el resto.

Los permisos se expresan con el bean `Jerarquia`, que compara rangos:

```java
@PreAuthorize("@jerarquia.puede(authentication, 'COLABORADOR')")
```

Es mejor que `hasAnyRole('COLABORADOR', 'ADMIN', 'ADMIN_SISTEMA')`, que obligaría a
añadir el nuevo rol a cada anotación el día que se cree.

Y para la regla que excluye, el método propio:

```java
@PreAuthorize("@jerarquia.puedeProponer(authentication)")
```

### Endpoints de autenticación y propuestas

| Método | Ruta | Rol | Respuesta |
| --- | --- | --- | --- |
| POST | `/api/auth/registro` | público | 201 + token. Siempre nace como `USUARIO` |
| POST | `/api/auth/login` | público | 200 + token. 401 si falla |
| GET | `/api/auth/yo` | autenticado | La cuenta del token, con su rol, y un token renovado. 401 si la cuenta está desactivada |
| POST | `/api/propuestas` | `USUARIO` o `COLABORADOR` | 201. 403 para `ADMIN`+ |
| GET | `/api/propuestas/mias` | autenticado | Historial del propio usuario |
| GET | `/api/propuestas/pendientes` | `COLABORADOR`+ | Cola de moderación |
| PUT | `/api/propuestas/{id}/revision` | `COLABORADOR`+ | Aprueba o rechaza, propia incluida. 400 si rechaza sin motivo |
| GET | `/api/expresiones` | público | Todas las expresiones culturales |
| GET | `/api/expresiones/{code}` | público | Las de un país. `200 []` si no tiene ninguna, 404 si el país no existe |
| POST | `/api/expresiones/propuestas` | `USUARIO` o `COLABORADOR` | 201. 403 para `ADMIN`+ |
| GET | `/api/expresiones/propuestas/mias` | autenticado | Historial del propio usuario |
| GET | `/api/expresiones/propuestas/pendientes` | `COLABORADOR`+ | Cola de moderación |
| PUT | `/api/expresiones/propuestas/{id}/revision` | `COLABORADOR`+ | Aprueba o rechaza. Al aprobar inserta la expresión |
| GET | `/api/admin/usuarios` | `ADMIN`+ | Lista de cuentas, con filtro `?rol=` |
| GET | `/api/admin/usuarios/{id}` | `ADMIN`+ | Una cuenta |
| PUT | `/api/admin/usuarios/{id}/rol` | `ADMIN`+ | Cambia el rol, con las 3 reglas |
| PATCH | `/api/admin/usuarios/{id}/estado` | `ADMIN`+ | Activa o desactiva. **No hay borrado** |

**El registro no acepta un `rol`.** Aunque el JSON lo lleve, el DTO lo descarta:
aceptarlo sería la vía más fácil para que cualquiera se autoproclamara
administrador.

**Las cuentas no se borran, se desactivan.** Las propuestas y revisiones guardan
la referencia a su autor, así que un `DELETE` dejaría el historial huérfano.

**Al aprobar, el identificador del lugar se genera en ese momento**, a partir del
nombre y de las coordenadas (`museo_larco_12_073_77_070`). Pedirlo al proponer solo
generaría colisiones que el usuario no puede ver. La consecuencia es que el
sistema **no deduplica por nombre**: si el lugar ya existe, el moderador ve la
propuesta y la rechaza, porque no hay forma de que el sistema lo sepa.

En las expresiones el identificador sale del **título y del país**
(`el_tango_ar`), no de las coordenadas, porque una expresión no tiene dónde estar.
El país de sufijo no es decorativo: sin él, dos *El Tango* de países distintos
generarían el mismo identificador. Aun así el sistema **no deduplica por
`(país, categoría)`**: la restricción `UNIQUE` de la tabla lo impide y el servicio
lo explica con un 409, pero la propuesta se considera revisada por la persona, no
por una regla.

Las expresiones son de **solo lectura por HTTP**: no hay `POST /api/expresiones`.
La única vía de escritura es aprobar una propuesta, y por eso los endpoints de
moderación viven bajo `/api/expresiones/propuestas` y no como un recurso aparte.

### Sobre el token

El JWT dura 8 horas y **no se comprueba contra la base en cada petición**. Esa es
la contrapartida de no tener servidor de sesiones:

- Un cambio de rol no tiene efecto hasta que el token caduca **o hasta que el cliente
  llame a `/api/auth/yo`**, que devuelve uno nuevo (ver la sección siguiente).
- Desactivar una cuenta no la expulsa al instante, por el mismo motivo.

Con 8 horas la ventana es acotada. Si hiciera falta expulsar a alguien en el
segundo, la solución es una lista de tokens revocados, que devuelve el estado al
servidor y que aquí no se ha querido pagar.

### Renovación del token en `GET /api/auth/yo`

La ventana anterior se cierra en la práctica por un endpoint: **`/api/auth/yo`
devuelve un token nuevo, firmado con el rol que figura ahora en la base**, además
de los datos de la cuenta.

El frontend lo llama una vez al arrancar, desde `provideAppInitializer`, así que
la renovación es automática y no exige volver a autenticarse. Antes de este
cambio devolvía solo los datos: la interfaz pintaba el rol nuevo porque lo leía de
la base, pero el token conservaba el anterior y la API respondía 403. Ese era el
botón muerto del Sprint III.

Dos propiedades del contrato:

- **Una cuenta desactivada recibe 401, no un token nuevo.** Renovarle la sesión
  sería lo contrario de desactivar a alguien. Como el resto de la API no consulta
  la base, esta llamada es la única vía por la que una desactivación corta el
  acceso antes de que caduque el token.
- **Una cuenta inexistente recibe 404**, porque el identificador sale del claim
  `uid` del token y no de un parámetro.

El tipo de la respuesta es `AuthResponse`, el mismo del registro y del login, y
no un tipo nuevo: la forma de "aquí tienes tu token y tu cuenta" ya existía.

La limitación que queda es que la renovación solo ocurre al arrancar la
aplicación. Si a alguien lo promovieran mientras la tiene abierta, su token
antiguo sigue valiendo hasta que recargue. Renovar de forma continua exigiría un
endpoint de refresco por enganche, que ya se resolvió en otro Sprint.

## Endpoints

| Método | Ruta | Respuesta |
| --- | --- | --- |
| GET | `/api/countries` | Lista de países con serie histórica y lugares anidados |
| GET | `/api/countries/{code}` | Un país, o 404 |
| POST | `/api/countries` | 201 + `Location`. 409 si el `code` ya existe |
| PUT | `/api/countries/{code}` | Actualización parcial |
| DELETE | `/api/countries/{code}` | 204. Borra también lugares y serie |
| GET | `/api/places` | Lista de lugares |
| GET | `/api/places?category=DANZA` | Filtro por categoría |
| GET | `/api/places/{id}` | Un lugar, o 404 |
| GET | `/api/places/pais/{code}` | Lugares de un país, o 404 |
| POST | `/api/places` | 201 + `Location`. 409 si el `id` ya existe |
| PUT | `/api/places/{id}` | Actualización parcial |
| DELETE | `/api/places/{id}` | 204 |

Ejemplos:

```bash
# Lectura: pública, no hace falta token
curl http://localhost:8080/api/countries
curl "http://localhost:8080/api/places?category=ARQUEOLOGIA"

# Escritura: exige el rol COLABORADOR o superior
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"root","password":"tu-contrasena"}' | jq -r .token)

curl -X POST http://localhost:8080/api/places \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"id":"nuevo_lugar","name":"Nuevo lugar","country":"PE",
       "lat":-12.0,"lng":-77.0,"category":"ARTE"}'
```

Sin el `Authorization`, el `POST` devuelve 401. Con un token de `USUARIO`, 403.
`DELETE /api/places/{id}` es la excepción: exige `ADMIN`, así que un `COLABORADOR`
también recibe 403 ahí.

### Formato de error

Todos los errores —de validación, de negocio o de ruta inexistente— comparten la
misma forma, para que el cliente tenga un único formato que parsear:

```json
{
  "timestamp": "2026-09-26T18:01:02.813Z",
  "status": 400,
  "error": "Bad Request",
  "message": "id: El id solo admite minusculas, digitos y guion bajo; lat: La latitud debe estar entre -90 y 90",
  "path": "/api/places",
  "details": [
    { "field": "id",  "message": "El id solo admite minusculas, digitos y guion bajo" },
    { "field": "lat", "message": "La latitud debe estar entre -90 y 90" }
  ]
}
```

| Situación | Código |
| --- | --- |
| Recurso inexistente | 404 |
| Identificador repetido | 409 |
| Violación de un CHECK o clave foránea | 409 |
| Campo inválido o enum desconocido | 400 |
| Petición incoherente (rechazar sin motivo) | 400 |
| Sin token, o token caducado | 401 |
| Con token, pero el rol no llega | 403 |

## Base de datos

### Migraciones

| Fichero | Qué hace |
| --- | --- |
| `V1__Create_lugares_table.sql` | Tabla `lugares` |
| `V2__Add_category_constraints.sql` | CHECK de las 9 categorías |
| `V3__Create_paises_table.sql` | Tablas `paises` y `paises_series_historicas` |
| `V4__Insert_Into_Lugares_table.sql` | 8 lugares de Perú |
| `V5__indices_y_restricciones.sql` | Índices, unicidad y CHECK |
| `V6__usuarios_y_propuestas.sql` | Tablas `usuarios` y `propuestas_lugar` (nueva) |
| `V1_1__Normalizar_categorias.sql` | Corrige las 2 categorías que V1 sembró fuera del CHECK de V2 (nueva) |
| `V7__Normalizar_regiones_y_sembrar_paises.sql` | Corrige las regiones que escribió el enum viejo, siembra los 17 países que faltaban y añade el CHECK de `region` (nueva) |
| `V8__expresiones_culturales.sql` | Tablas `expresiones_culturales` y `propuestas_expresion`, con `pais_code` como FK, y siembra las 4 expresiones (nueva) |
| `V9__propuestas_expresion_coherencia.sql` | Los dos CHECK de coherencia de la cola que V8 no replicó de V6 (nueva) |

`V1` a `V4` están **copiados byte a byte** del proyecto de Quarkus. No es descuido:
Flyway guarda un checksum de cada migración ya aplicada, y reescribir aunque sea un
comentario haría que el arranque fallara con *"Validate failed: migration checksum
mismatch"*.

Por lo mismo, `V9` **no edita `V8`**, aunque `V8` sea la que se dejó incompleta: `V8` ya
está aplicada en las bases de desarrollo y reescribirla rompería su checksum. Lo que
faltaba llega como migración nueva.

#### `V1_1`: por qué existe y por qué su versión es 1.1

La base **no se podía construir**. `V1` sembraba dos lugares con `'Arte'` y
`'Arqueología'` (mayúscula inicial, con tilde), y el CHECK que añade `V2` solo acepta
las nueve constantes en mayúsculas. Applying V2 sobre esa base falla:

```
ERROR:  check constraint "lugares_category_check" of relation "lugares" is violated by some row
```

Como `V2` ya está aplicada en toda base existente y su checksum no se puede tocar,
`V1_1` no reescribe nada: **añade** la normalización. Dos `UPDATE` explícitos, sin
`ALTER` ni funciones, para que un fallo se pueda atribuir a una fila concreta.

Flyway interpreta `V1_1` como versión **1.1** (convierte los guiones bajos en puntos), y
por eso se ordena entre `V1` y `V2`:

- **Base nueva:** se aplica todo en orden natural, `1 → 1.1 → 2 → …`. Es lo que hace
  `MigracionesSobrePostgresTest`.
- **Base existente con `V1..V6`:** `1.1` es *menor* que la última aplicada, así que
  Flyway la rechazaría con *"Detected resolved migration not applied to database"*. Por
  eso `application.properties` fija `spring.flyway.out-of-order=true`, y en ese caso la
  migración **no toca ninguna fila**, porque el CHECK de `V2` ya obliga a los valores
  canónicos.

`out-of-order` es el precio de no poder reescribir el historial. Se paga solo aquí, y
las tres propiedades documentan el porqué junto a la línea que las activa.

#### `V7`: las regiones que escribió el enum viejo, y los 17 países que faltaban

Dos cosas distintas que pasaron a la vez, en la misma migración.

**El 500 de `GET /api/countries`.** `Region` se guardaba con
`@Enumerated(EnumType.STRING)`, que escribe el **nombre de la constante**. La columna
`paises.region` quedó llena de `NORTEAMERICA`, `ANDINA`, `CONO_SUR`… Esos nombres no
pueden ser el dato: `"Cono Sur"` lleva espacio y no es un identificador de Java válido.
Por eso `Region` lleva ahora su valor textual aparte y lo traduce `RegionConvertidor`.

Los cinco nombres difieren del texto canónico, así que hacen falta los cinco `UPDATE`
(cuatro solo por las mayúsculas). Con el enum viejo, leer esas filas lanzaba
`IllegalArgumentException` y el endpoint devolvía 500; y antes de `RegionConvertidor`,
el mismo dato salía en el JSON como `CONO_SUR`, que el frontend no reconoce y
descartaba **en silencio** dibujando el país como "Andina".

**Los países que faltaban.** `V3` solo siembra México y Perú, y el frontend ya define
19 en `countries.ts`. `V7` siembra los que falten.

El contrato de idempotencia es por código, no "los que faltan en esta base":

```sql
WHERE NOT EXISTS (SELECT 1 FROM paises p WHERE p.code = v.code)
```

Así la misma migración sirve para una base recién creada (donde `V3` ya puso `MX` y
`PE`), para una base con los 4 que había y para una base completa. Escribir en su lugar
`WHERE code NOT IN ('MX','PE','AR','CL')` haría que en una base nueva se saltaran
justo los países que sí hay que sembrar.

De los países que ya existen **solo se les corrige `region`**: `name`, `capital`, `lat`
y `lng` no se tocan, porque en una base real pueden estar corregidos a mano.

Lo que `V7` **no** hace:

- **No siembra series históricas.** Los 19 países quedan sin
  `paises_series_historicas`, así que sus gráficos saldrán vacíos.
  `PaisServiceImpl` responde con lista vacía en vez de fallar
  (`seriesPorPais.getOrDefault(codigo, List.of())`). Hace falta una migración de datos
  aparte.
- **No arregla el futuro.** Por eso termina añadiendo el CHECK
  `paises_region_check`: un valor inventado lo rechaza Postgres en el `INSERT`, con un
  error que nombra la fila, en vez de aparecer como un 500 tres capas más abajo. Ese
  hueco es el que dejó pasar el bug: `lugares.category` tiene CHECK desde `V2`, y
  `paises.region` no tenía ninguno.

Antes del CHECK hay un guardia que aborta la migración si queda algún valor que no sea
canónico, y el `RAISE EXCEPTION` **los lista**. Sin él, el CHECK fallaría con
`violated by some row`, que no dice cuál es el culpable. Flyway envuelve la migración en
una transacción, así que el guardia también deshace los `UPDATE` anteriores: no deja la
tabla a medio normalizar.

`V5` sí es nueva, y ataca tres problemas del esquema:

1. **Ninguna clave foránea tenía índice.** `lugares.pais_code` y
   `paises_series_historicas.pais_code` se resolvían con un escaneo secuencial en cada
   acceso perezoso. Con 17 filas no se nota; con 10 000, sí.
2. **No había unicidad en `(pais_code, year)`.** El frontend asume que el último
   elemento de `seriesHistoricas` es el año más reciente
   (`series[series.length - 1]`); con duplicados, esa suposición es falsa y las
   tarjetas mostrarían un valor arbitrario.
3. **`lugares.country` duplicaba `lugares.pais_code`** sin ninguna restricción que los
   mantuviera iguales. Ahora hay un `CHECK (country = pais_code)`, y por eso la entidad
   `Lugar` escribe ambas columnas siempre juntas, a través de `setPais(Pais)`.

`V6` añade `usuarios` y `propuestas_lugar`. Tres decisiones suyas:

1. **No siembra ninguna cuenta.** El primer `ADMIN_SISTEMA` lo crea el arranque desde
   variables de entorno. Una cuenta con contraseña fija en el SQL quedaría escrita en
   el historial de git, y da igual que el tutorial la llame "de ejemplo": existe en
   todos los entornos y en todos los despliegues futuros.
2. **Los CHECK repiten las reglas de Java.** El `rol` se valida en el enum y también en
   la base, para que un `INSERT` manual no deje un valor que la aplicación no sepa
   interpretar.
3. **La coherencia de la moderación se impone en la base.**
   `ck_propuestas_rechazo_coherente`
   exige que una propuesta esté `RECHAZADA` si y solo si tiene motivo, y
   `ck_propuestas_revision_coherente` que una propuesta revisada tenga fecha. Son las
   invariantes que el código de aplicación da por ciertas.

La propuesta **no tiene columna de `id` de lugar**: el identificador se genera al
aprobar, cuando se puede comprobar que no choque con ninguno existente.

### Configuración

`ddl-auto=validate`: Hibernate nunca crea ni altera tablas. El esquema es de Flyway, y
`validate` solo comprueba al arrancar que las entidades coincidan con las columnas
reales. Si alguien añade un campo a una entidad y no su migración, la aplicación no
arranca en lugar de fallar en producción.

`open-in-view=false`: la vista no puede abrir la sesión de Hibernate para serializar.
Cada DTO se construye dentro del servicio, con la sesión abierta. Por eso
`PaisServiceImpl.listarTodos()` hace **tres consultas en total** (países, lugares,
series) y las agrupa en memoria, en vez de dejar que Hibernate dispare un acceso
perezoso por país y por colección.

## Pruebas

```bash
./mvnw test
```

130 pruebas sin base de datos, más 20 de integración con Postgres:

| Clase | Qué cubre |
| --- | --- |
| `ResponseMapperTest` | Mapeo entidad → DTO, enums por nombre, `country` sincronizado con el país, métricas nulas |
| `LugarServiceImplTest` | Reglas de negocio con repositorios simulados: duplicados, país inexistente, actualización parcial, agrupación en memoria |
| `LugarControllerTest` | Slice de Spring MVC: rutas, forma del JSON, grupos de validación, cuerpo `ApiError` |
| `JerarquiaTest` | La jerarquía de roles, incluidas las igualdades: dos `ADMIN` no se tocan, nadie delega lo que no tiene, y `puedeProponer` excluye en vez de incluir |
| `PermisosDeEscrituraTest` | El umbral real de cada endpoint, deducido de su `@PreAuthorize` y evaluado con SpEL |
| `AdminUsuarioServiceImplTest` | Las tres reglas de administración y el orden de las comprobaciones |
| `PropuestaServiceImplTest` | Aprobar crea el lugar, rechazar exige motivo, la autorevisión se acota a quien no escribe directo, no se revisa dos veces |
| `PropuestaExpresionServiceImplTest` | El mismo flujo sobre expresiones: el slug lleva el país de sufijo, el país se comprueba antes de guardar, rechazar sin motivo no delega nada, y si la inserción falla la propuesta **no** queda aprobada |
| `AuthServiceImplTest` | `usuarioActual` renueva el token con el rol **de la base**, no el del token: cuenta inactiva sin token nuevo (401), inexistente 404, y el `uid` del claim |
| `RegionConvertidorTest` | El texto de `Region` en las dos direcciones, incluidos `"Cono Sur"` (con espacio) y el rechazo de `"NORTEAMERICA"` sin tilde |
| `PatrimonioApplicationTest` | El arranque aborta si faltan `DB_PASSWORD` o `JWT_SECRET`, en sus formas canónica y alternativa |

Sobre el total: **150 pruebas**, de las cuales 20 necesitan Docker. Las 130 restantes no
tocan una base de datos.

En `JerarquiaTest` se cubren los casos de igualdad a propósito: un `>=` donde
debería haber un `>` permitiría a un administrador degradar a otro, y el fallo no
daría ninguna excepción, sino un agujero silencioso.

`PermisosDeEscrituraTest` existe por el mismo motivo, aplicado a las anotaciones.
La seguridad de método no se carga en `@WebMvcTest`, así que `LugarControllerTest`
no puede comprobar los umbrales por HTTP; ahí se evalúa la expresión tal cual, con
el bean `jerarquia` de verdad y para cada rol. Si el borrado vuelve a escribirse con
`'COLABORADOR'` para "mantener el patrón" de la creación, no hay nada que falle en
ningún otro test: la anotación sería válida para Spring, el endpoint respondería, y
lo único que cambiaría es que un colaborador puede borrar un lugar curado.

### Pruebas de integración: `MigracionesSobrePostgresTest`

Hay una clase que levanta **un Postgres de verdad** con Testcontainers y le aplica la
cadena completa de migraciones. No es una base en memoria a propósito: los dos fallos
más caros de este proyecto solo se ven contra el motor real, y ninguno habría salido
con un H2 o un H2 con dialecto.

| Lo que comprueba | Qué fallo real detecta |
| --- | --- |
| Las 8 versiones aplicadas en orden: `1, 1.1, 2, 3, 4, 5, 6, 7` | Una migración borrada, renombrada o con la versión cambiada |
| `machu` → `ARQUEOLOGIA` y `mali` → `ARTE`, resueltas con `valueOf` | V1 sembrando `'Arqueología'` y el CHECK de V2 exigiendo mayúsculas |
| Ninguna categoría fuera de las nueve del enum | Enum y `CHECK` desincronizados en cualquier dirección |
| `region` en la base es `Norteamérica`, no `NORTEAMERICA` | El enum guardando el nombre de la constante |
| Los 19 países presentes y ninguna `region` fuera de las cinco canónicas | El 500 de `GET /api/countries` |
| `PaisRepository.findAll()` no lanza | El 500 de `GET /api/countries` |
| `region` de la respuesta está en la lista cerrada del frontend | México dibujándose como "Andina" sin ningún error visible |
| Los lugares de un país vienen anidados | El segundo fetch que el frontend ya no hace |

Que el contexto arranque **es parte de la aserción**: `ddl-auto=validate` compara las
entidades con el esquema migrado y detiene el arranque si no coinciden, así que la
prueba no puede pasar con el esquema desalineado aunque las consultas den bien.

Requiere Docker en la máquina. Levanta `postgres:17-alpine` y lo tira al terminar;
no toca el contenedor de desarrollo. Tarda ~30 s la primera vez (descarga la imagen) y
~20 s después.

La versión de Postgres importa, y aquí ya no hay discrepancia: el README declara
PostgreSQL 17, las pruebas usan `postgres:17-alpine`, y el servicio de Windows
`postgresql-x64-17` contra el que corre la aplicación en desarrollo también es 17.

Conviene saber, porque confunde, que en esta máquina hay **otro** Postgres: el contenedor
`postgres-db`, que es un 15. No lo usa la aplicación —no tiene ni la base
`hispania_db`—, pero publica el puerto 5432 y se le habla por el mismo sitio. Cuando los
dos compete por ese puerto, gana el servicio de Windows, y por eso
`docker exec postgres-db psql … -d hispania_db` responde *"database does not exist"* en
lugar de un error de conexión.

### Pruebas de migración: `MigracionV7PaisesTest`

`MigracionesSobrePostgresTest` aplica la cadena sobre una base **nueva**, y en una base
nueva `V3` siembra México y Perú con el texto ya correcto. El `CONO_SUR` de la base de
desarrollo no aparece ahí: hace falta una base donde alguien escribió con el enum viejo.
Esta clase existe justo para eso, y controla el punto de parada de Flyway: deja la base
en `V6`, la devuelve al estado que estaba la real, y solo entonces aplica `V7`.

| Lo que comprueba | Por qué |
| --- | --- |
| Los cinco nombres heredados (`NORTEAMERICA`, `CENTROAMERICA`, `CARIBE`, `ANDINA`, `CONO_SUR`) se normalizan | Que ningún `UPDATE` se quede sin ejercitar |
| Quedan los 19 países, sin duplicados | `AR` y `CL` ya estaban: `V7` no los re-inserta |
| No pisa `name`, `capital` ni coordenadas de un país existente | Que la siembra no gane la discusión con quien los corrigió a mano |
| Aplicar `V7` dos veces no inserta nada | Que se pueda reejecutar a mano durante una depuración |
| El guardia aborta y **deshace** los `UPDATE` previos | Que no deje la tabla con unas regiones normalizadas y otras no |
| El CHECK rechaza `'CONO_SUR'` con un error que nombra la restricción | El 500 en `GET /api/countries`, ahora en el `INSERT` |

Cada prueba usa **un esquema propio** en el mismo Postgres. Compartir uno obligaría a
que cada test empezara en un estado dependiente del orden, que es lo que hace que una
suite de migraciones no sirva para nada.

Estas pruebas no cubren todavía la renovación de token por HTTP: `AuthServiceImplTest`
lo verifica con una unidad, y el escenario completo (cambiar el rol y que el cambio
surta efecto en la siguiente llamada) se probó a mano contra la aplicación en marcha,
pero no está automatizado. Automatizarlo es el siguiente paso natural, porque necesita
el mismo Postgres que ya está montado aquí.


## Diferencias con la versión Quarkus

| Aspecto | Quarkus | Spring Boot 4 |
| --- | --- | --- |
| Acceso a datos | Panache (active record) | Spring Data JPA, repositorios declarativos |
| Capa de negocio | No existía; el controller llamaba al entity | `services` con interfaz e implementación |
| Transacciones | `@Transactional` de Jakarta | `@Transactional(readOnly = true)` en lectura, escritura explícito |
| Errores | `Response.status(...)` a mano, sin cuerpo común | `GlobalExceptionHandler` y cuerpo `ApiError` |
| Validación | Ninguna | Bean Validation con grupos por operación |
| CORS | `CorsFilter` (JAX-RS) duplicando `quarkus.http.cors` | `WebConfig` con `CorsProperties` tipado |
| Mapeo | DTOs con `static fromEntity` (presentación → persistencia) | `ResponseMapper` en la capa de servicios |

## Notas sobre Spring Boot 4

Boot 4 movió cada autoconfiguración técnica a su propio módulo, y `spring-boot-autoconfigure`
ya no las incluye. Dos consecuencias de este proyecto:

- **`spring-boot-flyway` es obligatorio.** Sin esa dependencia la aplicación **arranca
  bien** (Hibernate valida el esquema y las tablas ya existen) pero **Flyway no se
  ejecuta**: las migraciones no se aplican y el fallo aparece en el siguiente despliegue,
  al fallar por una columna que no existe. Es el fallo más difícil de detectar de esta
  migración, porque la aplicación parece correcta.
- **`@WebMvcTest` también se movió** a `spring-boot-webmvc-test`, con el paquete
  `org.springframework.boot.webmvc.test.autoconfigure`.

Además, Boot 4 usa **Jackson 3** (`tools.jackson`), no `com.fasterxml.jackson`. Los enum
de configuración van en mayúsculas y `WRITE_DATES_AS_TIMESTAMPS` ya viene desactivado,
así que no se usa `spring.jackson.serialization.write-dates-as-timestamps`.

## Notas de la migración desde Quarkus

- **Se añadió `GET /api/countries/{code}`.** `CountriesService.fetchCountryByCode` del
  frontend ya lo llamaba, pero el backend de Quarkus no lo tenía implementado, así que
  siempre caía en los datos de respaldo locales.
- **`CountryPanel` y el resto de vistas no cambiaron.** El contrato JSON es idéntico a
  propósito: `seriesHistoricas` sigue siendo una lista de `{year, gdp, ...}` y
  `category` sigue siendo la cadena del enumerado, no su ordinal.
- **La limitación del gráfico de Economía sigue vigente.** Cambiar el indicador a
  "Población" actualiza las tarjetas, pero la línea sigue mostrando el PBI, porque
  `paises_series_historicas` no tiene serie para las demás métricas. Es un problema del
  modelo de datos, no de la arquitectura.
