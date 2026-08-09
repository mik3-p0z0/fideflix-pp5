# Fideflix — Práctica Programada 5: Bases de Datos

Prueba de concepto cliente-servidor para la plataforma de contenido audiovisual **Fideflix**.
Un servidor multihilo atiende peticiones concurrentes de N clientes sobre sockets TCP y gestiona
la persistencia en **MySQL**.

**Curso:** Programación Cliente-Servidor · Universidad Fidélitas · II Cuatrimestre 2026

---

## Tabla de contenido

1. [Alcance](#1-alcance)
2. [Arquitectura](#2-arquitectura)
3. [Modelo de datos](#3-modelo-de-datos)
4. [Protocolo de aplicación](#4-protocolo-de-aplicación)
5. [Instalación](#5-instalación)
6. [Ejecución](#6-ejecución)
7. [Pruebas](#7-pruebas)
8. [Decisiones de diseño](#8-decisiones-de-diseño)
9. [Seguridad](#9-seguridad)
10. [Limitaciones conocidas](#10-limitaciones-conocidas)
11. [Integrantes](#11-integrantes)

---

## 1. Alcance

| Entrega | Qué resolvió |
|---|---|
| PP4 | Sockets TCP, protocolo de texto, un hilo por conexión, persistencia en archivo serializado (`usuarios.dat`) |
| **PP5 (esta)** | Migración completa a **MySQL**: CRUD de Películas, Documentales y Series, comentarios por obra, y eliminación del archivo serializado |

### Requerimientos de la consigna y dónde se cumplen

| Requerimiento | Implementación |
|---|---|
| El usuario ejecuta peticiones de creación, eliminación, actualización y obtención de Películas, Documentales y Series | `VentanaCatalogo` + `VentanaAudiovisualForm` → comandos `CREAR_AV`, `ACTUALIZAR`, `ELIMINAR`, `LISTAR`, `OBTENER` |
| El servidor toma las peticiones, se conecta a MySQL y ejecuta las tareas CRUD | `HiloCliente` enruta a `AudiovisualDAO` sobre JDBC |
| Escucha constante de múltiples clientes mediante hilos | `ServidorFideflix`: hilo aceptador dedicado + un `HiloCliente` por conexión |
| Ventanas necesarias para el objetivo | 3 ventanas nuevas + menú principal reescrito |
| Base de datos, schema y tablas | `sql/01_schema.sql` — 8 tablas normalizadas |

---

## 2. Arquitectura

```
┌──────────────────┐         ┌──────────────────────────────┐        ┌──────────┐
│  Cliente Swing   │  TCP    │        Servidor              │  JDBC  │  MySQL   │
│  (N ventanas)    │────────▶│  aceptador + N HiloCliente   │───────▶│ fideflix │
│                  │◀────────│  → DAO (PreparedStatement)   │◀───────│          │
└──────────────────┘  texto  └──────────────────────────────┘        └──────────┘
    localhost:5000
```

### Modelo de hilos

| Hilo | Cantidad | Responsabilidad |
|---|---|---|
| EDT de Swing | 1 | Dibuja las ventanas y atiende los botones |
| Aceptador | 1 | Bucle infinito en `accept()`, delega cada conexión |
| `HiloCliente` | N | Atiende una petición de un cliente y responde |

`accept()` es bloqueante, por eso vive en su propio hilo: en el EDT congelaría la interfaz.
Cada conexión aceptada se delega de inmediato a un `HiloCliente` nuevo y el aceptador vuelve a
`accept()`. Así se logra la *escucha constante* que exige la consigna.

### Paquetes

| Paquete | Responsabilidad |
|---|---|
| `fideflix.logica` | Modelo de dominio: `Audiovisual` (abstracta), `Pelicula`, `Documental`, `Serie`, `Usuario`, `Comentario`, `ItemCatalogo` |
| `fideflix.persistencia` | Acceso a datos (patrón DAO). **Todo el SQL vive aquí y solo aquí** |
| `fideflix.red` | Protocolo, servidor, hilos de atención, cliente de sockets y codificador de mensajes |
| `fideflix.interfaz` | Ventanas Swing y punto de entrada |
| `fideflix.excepciones` | Excepciones propias del dominio |

Las dependencias van en una sola dirección: `interfaz → red → persistencia → logica`.
Ninguna ventana conoce JDBC; ningún DAO conoce Swing.

---

## 3. Modelo de datos

Diagrama entidad-relación: [`docs/modelo_er.png`](docs/modelo_er.png)
*(Generado con Workbench → Database → Reverse Engineer.)*

### Tablas

| Tabla | Rol |
|---|---|
| `usuario` | Cuentas. `email` con `UNIQUE`, contraseña almacenada como hash |
| `audiovisual` | Atributos comunes de toda obra + discriminador `tipo` |
| `pelicula` / `documental` / `serie` | Atributos propios de cada subtipo, 1:1 con `audiovisual` |
| `genero` / `clasificacion` | Catálogos de dominio cerrado |
| `comentario` | Comentario de un usuario sobre una obra |

### Estrategia de herencia: *class table inheritance*

Java tiene herencia; SQL no. Se evaluaron las tres estrategias clásicas:

| Estrategia | Por qué se descartó / eligió |
|---|---|
| Tabla única con columna `tipo` y campos nulables | Una serie tendría `director` y `estudio` en `NULL` para siempre: la tabla mentiría sobre su propio dominio |
| Tres tablas independientes completas | `titulo`, `genero` y `calificacion_imdb` duplicados en tres lugares → anomalías de actualización |
| **Tabla base + 3 hijas 1:1** ✅ | Refleja el modelo Java, queda en 3FN, sin columnas nulas artificiales |

En las hijas, **la llave primaria es también la llave foránea**. Ese detalle impone la relación 1:1
estricta: una película no puede tener dos filas hijas porque la PK no admite duplicados.
El modelo se defiende solo.

### Integridad

- `ON DELETE CASCADE` en las hijas y en `comentario`: borrar una obra se lleva su fila hija y sus comentarios. Un solo `DELETE` limpia tres tablas.
- `ON DELETE SET NULL` en los catálogos: borrar el género "Drama" **no** debe borrar todas las películas dramáticas.
- `CHECK` sobre `calificacion_imdb` (0–10) y `anio_estreno` (1888–2200).
- `UNIQUE (titulo, anio_estreno)`: no dos obras iguales, pero se permiten remakes.
- Índices sobre `tipo` y `titulo`: las columnas por las que se filtra y se ordena.

> La cascada se elige **por relación**, según qué significa la dependencia en el negocio.
> No es una configuración uniforme aplicada por costumbre.

---

## 4. Protocolo de aplicación

Texto plano sobre TCP. Una línea por petición; la respuesta puede ser una línea o
un encabezado con conteo seguido de N registros. Campos separados por `|`.

### Comandos

| Petición | Respuesta |
|---|---|
| `CREAR\|nombre\|email\|contrasena\|fecha` | `OK\|id` · `DUPLICADO\|email` · `ERROR\|detalle` |
| `LOGIN\|email\|contrasena` | `OK\|id\|nombre\|fecha` · `DENEGADO` |
| `LISTAR\|tipo` | `OK\|n` + n registros |
| `OBTENER\|id` | `OK\|1` + 1 registro · `NO_ENCONTRADO` |
| `CREAR_AV\|tipo\|titulo\|desc\|anio\|imdb\|clasif\|genero\|e1\|e2\|e3` | `OK\|id` · `DUPLICADO\|msg` |
| `ACTUALIZAR\|id\|tipo\|...` | `OK\|id` · `NO_ENCONTRADO` |
| `ELIMINAR\|id` | `OK\|id` · `NO_ENCONTRADO` |
| `COMENTAR\|idObra\|idUsuario\|texto` | `OK\|idComentario` · `NO_ENCONTRADO` |
| `LISTAR_COMENTS\|idObra` | `OK\|n` + n líneas `id\|autor\|fecha\|texto` |
| `CATALOGOS` | `OK\|n` + n líneas `GENERO\|id\|nombre` o `CLASIFICACION\|id\|codigo` |

Formato de registro (11 campos): `id|tipo|titulo|descripcion|anio|imdb|clasificacion|genero|e1|e2|e3`

Los tres últimos campos cambian de significado según el tipo — por eso `tipo` viaja **antes** que
ellos, para que el receptor sepa cómo interpretarlos cuando lleguen:

| Tipo | e1 | e2 | e3 |
|---|---|---|---|
| `PELICULA` | duración | director | estudio |
| `DOCUMENTAL` | duración | director | tema |
| `SERIE` | temporadas | episodios | estado |

### Dos problemas que el protocolo tuvo que resolver

**1. Caracteres que rompen el formato.** Con texto libre (descripciones, comentarios) aparecen dos roturas: un `|` dentro de un campo agrega columnas fantasma y corre todos los siguientes; un salto de línea parte el mensaje en dos, y como el receptor usa `readLine()`, lee media petición y deja la otra mitad envenenando el mensaje siguiente.

Solución: `Protocolo.escapar()` / `desescapar()` sustituyen esos dos caracteres por marcadores inofensivos. Mismo principio por el que una URL escribe `%20` en lugar de un espacio: si un carácter tiene significado estructural, no puede viajar crudo dentro de un dato.

**2. Respuestas de longitud variable.** `LISTAR` devuelve N registros. Se resolvió con un encabezado `OK|n` seguido de exactamente n líneas — el mismo principio del `Content-Length` de HTTP: decir cuánto viene antes de mandarlo. La alternativa (un centinela tipo `FIN`) obliga a garantizar que ese texto nunca aparezca en los datos, y esa garantía es la que siempre termina rompiéndose.

---

## 5. Instalación

### Requisitos

- JDK 26 (o el configurado en `nbproject/project.properties`)
- MySQL Server 8.0.16 o superior *(los `CHECK` se ignoran en versiones anteriores)*
- MySQL Workbench
- NetBeans IDE (proyecto Ant)
- MySQL Connector/J — **ya incluido** en `Fideflix/lib/`

### Pasos

**1. Crear la base y el usuario de aplicación.** En Workbench, conectado como `root`:

```sql
CREATE DATABASE IF NOT EXISTS fideflix
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'fideflix_app'@'localhost' IDENTIFIED BY 'TU_CLAVE_AQUI';

-- Privilegios MINIMOS: solo manipulación de datos.
-- Sin CREATE, ALTER ni DROP: la aplicación no tiene por qué poder
-- modificar la estructura de la base en tiempo de ejecución.
GRANT SELECT, INSERT, UPDATE, DELETE ON fideflix.* TO 'fideflix_app'@'localhost';
FLUSH PRIVILEGES;
```

**2. Crear el esquema.** Ejecutar como `root`, en orden:

```
sql/01_schema.sql            -- las 8 tablas
sql/02_datos_iniciales.sql   -- catálogos + 6 obras + usuario de prueba
```

> `01_schema.sql` requiere `root` porque contiene DDL. Que `fideflix_app` **no** pueda ejecutarlo
> es intencional: separación entre administración de la estructura y operación sobre los datos.

**3. Configurar las credenciales.**

```
copy Fideflix\db.properties.example Fideflix\db.properties
```

Editar `db.properties` y poner la contraseña real de `fideflix_app`.
**Este archivo no se versiona** — está en `.gitignore`.

**4. Abrir el proyecto en NetBeans** y ejecutar `fideflix.interfaz.Main`.

---

## 6. Ejecución

`Main.java` levanta dos ventanas:

- **`VentanaServidor`** — inicia automáticamente la escucha en el puerto 5000 y muestra la bitácora en vivo. Cerrarla termina toda la aplicación.
- **`VentanaLanzador`** — cada clic en *Nuevo cliente* abre una sesión de login independiente.

Esto permite demostrar la atención concurrente sin volver a ejecutar el programa.

**Usuario de prueba:** `prueba@fideflix.com` / `admin123`
*(Creado por `02_datos_iniciales.sql`. Eliminarlo en cualquier uso que no sea evaluación.)*

Flujo: login → **Ver catálogo** → Nuevo / Editar / Eliminar / Comentarios.

---

## 7. Pruebas

Tres clases ejecutables (clic derecho → *Run File*), en orden de alcance creciente:

| Clase | Qué aísla |
|---|---|
| `persistencia.PruebaConexion` | Conectividad JDBC: driver, credenciales, base alcanzable |
| `persistencia.PruebaDAO` | La capa de datos completa — 30 verificaciones |
| `red.PruebaRed` | Extremo a extremo por sockets reales, incluida la concurrencia |

> `PruebaRed` levanta su propio servidor: **cerrar `Main` antes de ejecutarla** o el puerto 5000
> estará ocupado.

Cada prueba aísla una capa distinta a propósito. Si algo falla más adelante, la que pasa elimina
sospechosos: depurar es reducir la lista de culpables posibles.

### Las verificaciones que importan

**Atomicidad de la transacción.** `PruebaDAO` inserta una película con `duracion_min = 99999`, que desborda el `SMALLINT UNSIGNED` de la tabla hija. El primer `INSERT` ya se ejecutó cuando el segundo falla. Se cuenta antes y después: si el total no cambió, el `ROLLBACK` funcionó. Si aumentó, hay una fila huérfana y la transacción es decorativa.

**Resistencia a inyección SQL.** Se inserta una obra titulada `Prueba'); DROP TABLE comentario; --` y se verifica que se guarde como texto literal y que la tabla siga existiendo.

**Escape del protocolo.** `PruebaRed` envía una descripción con `|` y saltos de línea reales y verifica que vuelva byte a byte idéntica.

**Concurrencia real.** Ocho hilos bloqueados en un `CountDownLatch` salen simultáneamente a crear obras. Sin esa barrera se lanzarían escalonados y la prueba sería decorativa. Que los ocho ids sean distintos demuestra que no hubo *lost update*.

---

## 8. Decisiones de diseño

### El candado global se eliminó, no se migró

En PP4, cada operación estaba envuelta en `synchronized (CANDADO_ARCHIVO)`. Era imprescindible:
leer-modificar-guardar un archivo no es atómico, y sin candado el segundo hilo en guardar pisaba
al primero (*lost update*).

Con MySQL ese candado **sobra y perjudica**. El motor ya resuelve la concurrencia con transacciones
y bloqueo a nivel de fila, que es mucho más fino que un candado global de la JVM. Mantenerlo
obligaría a que los N hilos pasen de a uno por la base: sería construir un servidor multihilo y
convertirlo en secuencial a mano.

**La atomicidad ya no la sostiene un `synchronized` de Java: la sostienen el `COMMIT`/`ROLLBACK`
y las restricciones del motor.**

### Detección de duplicados: insertar y dejar que el motor rechace

Antes se preguntaba `contains()` y después se insertaba: **dos pasos**, con una ventana entre
ellos por donde otro hilo puede colarse (condición de carrera TOCTOU — *time of check, time of use*).
Ahora se intenta insertar y se captura `SQLIntegrityConstraintViolationException`: **un paso atómico**.

Es la diferencia entre *mirar antes de saltar* y *pedir perdón en vez de permiso*. En concurrencia
gana la segunda, porque la primera asume que el mundo no cambia entre que mirás y que saltás.

### `tipo` en `audiovisual` es una denormalización consciente

Estrictamente es redundante: el tipo podría deducirse con tres `LEFT JOIN`. Se incluyó porque
listar el catálogo es la operación más frecuente y así se resuelve con un `SELECT` simple y un
`switch` directo en el mapeo.

**Una denormalización justificada y documentada suma criterio; una accidental resta.** Esta está
documentada aquí y en el `.sql`.

### La fecha de registro la pone el servidor

En PP4 salía de un `JTextField` de texto libre. Eso rompía la columna `DATE` con cualquier entrada
inválida, y además permitía al usuario mentir. La fecha de registro es **un hecho que el sistema
conoce**, no un dato que se pregunta: ahora la asigna `CURDATE()`. Mismo criterio que
`comentario.fecha_hora DEFAULT CURRENT_TIMESTAMP` — el reloj confiable es el del servidor, no el
de la máquina del cliente.

### Los combos se llenan desde la base

Si las opciones vinieran de un `String[]` escrito a mano, nada garantizaría que coincidan con las
tablas `genero` y `clasificacion`. Trayéndolas del servidor es **imposible** elegir un valor
inexistente, y la llave foránea nunca falla por culpa de la interfaz. La restricción del motor y
las opciones de la pantalla salen de la misma fuente de verdad.

### Una petición por conexión

Se mantuvo el esquema de PP4 (como HTTP/1.0) en lugar de un bucle que sostenga el socket abierto.
La alternativa es más eficiente, pero exige comando de salida, detección de clientes caídos y
gestión de sesión: tres fuentes de bugs sin beneficio en este alcance.

Elegir lo simple *sabiendo* que existe lo complejo es criterio; elegirlo por desconocimiento, no.

### Ventanas escritas a mano, sin archivos `.form`

Los `.form` son XML generado por el diseñador y son prácticamente imposibles de fusionar cuando
dos personas editan la misma ventana. En un proyecto grupal versionado eso cuesta horas.
El precio es perder el diseñador visual en esas pantallas.

### El bug del `ArrayList static`

En PP4, `Audiovisual` guardaba los comentarios en un `ArrayList<String>` declarado `static`.
Al ser estático, la lista pertenecía a la **clase** y no a cada objeto: todas las obras compartían
los mismos comentarios, y uno sobre *Interstellar* aparecía también en *Breaking Bad*.

El modelo relacional hace ese error imposible: al obligar a declarar la llave foránea, expone lo
que Java dejaba pasar en silencio. `PruebaDAO` verifica explícitamente que un comentario **no**
aparezca en otra obra.

---

## 9. Seguridad

| Control | Implementación |
|---|---|
| Inyección SQL | `PreparedStatement` con parámetros en **todo** acceso. Cero concatenación de datos de usuario |
| Mínimo privilegio | La app se conecta como `fideflix_app`, sin `CREATE`/`ALTER`/`DROP`. Nunca como `root` |
| Contraseñas | Se almacena SHA-256 hexadecimal, nunca el texto plano. El hasheo ocurre en el servidor |
| Enumeración de usuarios | `DENEGADO` genérico: no se distingue "email inexistente" de "clave incorrecta" |
| Fuga de información | Los errores de MySQL **no** viajan al cliente. `SQLState`, código y detalle quedan en la bitácora del servidor |
| Secretos | Las credenciales viven en `db.properties`, fuera del código y fuera del repositorio |
| Bitácora | Las contraseñas se censuran antes de escribirse al log |
| Defensa en profundidad | La base valida con `CHECK`, `UNIQUE` y `FOREIGN KEY` aunque la interfaz falle |

**Por qué `PreparedStatement` funciona** (y esto es lo que importa entender, no la sintaxis):
envía la *estructura* de la consulta al motor **antes** que los datos. MySQL ya decidió que eso es
un `INSERT` con siete valores cuando los valores llegan, por un canal separado, tratados como
datos puros. No es escapar comillas: es **separar el código de los datos de raíz**.

**Validación en dos capas.** La interfaz valida para dar buena experiencia; el servidor valida
porque el cliente no es confiable — cualquiera puede mandar una línea cruda al puerto 5000 con
`telnet`. Validación en la interfaz = comodidad. Validación en el servidor = seguridad. Nunca son
lo mismo, aunque el código se parezca.

---

## 10. Limitaciones conocidas

Ninguna de estas es un defecto de la implementación: son el límite del alcance, asumido
conscientemente y declarado.

| Limitación | Impacto real | Solución en producción |
|---|---|---|
| Las contraseñas viajan en texto plano por el socket | Quien observe la red captura credenciales | `SSLSocket` / TLS |
| SHA-256 sin *salt* | Hash rápido (una GPU prueba miles de millones/s) y vulnerable a *rainbow tables* | bcrypt, scrypt o **Argon2id** |
| `useSSL=false` en la URL JDBC | Tráfico app↔MySQL sin cifrar. Tolerable solo porque ambos están en `localhost` | TLS entre aplicación y motor |
| Conexión JDBC nueva por operación | Coste de *handshake* + autenticación en cada llamada | Pool de conexiones (HikariCP) |
| *Thread-per-connection* | No escala más allá de cientos de clientes | `ExecutorService` con pool acotado, o NIO |
| Sin bloqueo optimista | Dos ediciones simultáneas: gana la última, la primera se pierde en silencio | Columna `version` + `UPDATE ... WHERE version = ?` |
| El tipo de una obra no se puede cambiar al editar | Convertir película en serie exige borrar e insertar filas hijas en la misma transacción | Implementable; se omitió por ser un camino poco usado y propenso a fallos sutiles |
| Las llamadas de red ocurren en el EDT | Con un catálogo grande o el servidor remoto, la ventana se congelaría | `SwingWorker`: consultar en `doInBackground()`, actualizar en `done()` |
| Sin control de roles | Cualquier usuario autenticado puede editar o borrar cualquier obra | Tabla de roles y verificación en el servidor |

---

## 11. Integrantes

| Nombre | Carné |
|---|---|
| Michael Pozo | mpozo50214 |
| *(completar)* | |

---

## Estructura del repositorio

```
practica_programada5/
├── README.md
├── .gitignore  ·  .gitattributes
├── sql/                              ← lo lee una persona o Workbench
│   ├── 01_schema.sql
│   ├── 02_datos_iniciales.sql
│   └── 03_verificacion.sql
├── docs/
│   ├── GUIA_PP5_Fideflix.md          ← guía de construcción, fase por fase
│   ├── FASE4_AudiovisualDAO.md       ← diseño del DAO central
│   ├── FASE9_pruebas.md              ← protocolo de validación
│   └── modelo_er.png
└── Fideflix/                         ← proyecto NetBeans
    ├── lib/mysql-connector-j-*.jar
    ├── db.properties.example          (se versiona)
    ├── db.properties                  (NO se versiona)
    └── src/fideflix/{logica,persistencia,red,interfaz,excepciones}
```

Dentro de `Fideflix/` va todo lo que la aplicación necesita **en tiempo de ejecución**;
en la raíz, lo que lee una persona. Al comprimir `Fideflix/` para la entrega, el proyecto queda
completo y autosuficiente.
