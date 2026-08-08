# Fideflix — Práctica Programada 5 (Bases de Datos)
## Prompt-guía de ejecución, desde cero y replicable

> **Cómo usar este documento.** Sirve como dos cosas a la vez:
> 1. **Plan de trabajo**: ejecutá las fases en orden. Cada una termina en un estado verificable y en un commit.
> 2. **Prompt reutilizable**: para pedir ayuda con una etapa concreta, copiá el bloque de esa fase y agregá al final: *"Ejecutá la Fase N sobre el proyecto Fideflix respetando el estilo de comentarios existente. Explicá el porqué antes del cómo."*
>
> La regla del documento: **nunca ejecutés un paso cuya razón no puedas explicar en una frase.** Si no podés, releé el bloque *"Por qué"* de esa fase. El objetivo no es entregar; es que la arquitectura te quede en la mano.

---

## 0. Contexto y supuestos

### 0.1 De dónde partimos (estado real del proyecto, verificado)

El proyecto NetBeans (Ant) `Fideflix` ya resuelve la PP4:

| Capa | Clases | Qué hace hoy |
|---|---|---|
| `fideflix.logica` | `Audiovisual` (abstracta), `Pelicula`, `Documental`, `Serie`, `Usuario` | Modelo de dominio con herencia y `Comparable` |
| `fideflix.red` | `Protocolo`, `ClienteFideflix`, `ServidorFideflix`, `HiloCliente` | Sockets TCP en `localhost:5000`, protocolo de texto con `\|`, un hilo por conexión |
| `fideflix.persistencia` | `PersistenciaUsuarios` | Serialización de `ArrayList<Usuario>` a `usuarios.dat` |
| `fideflix.interfaz` | `Main`, `VentanaServidor`, `VentanaLanzador`, `ventanaInicioSesion`, `VentanaCrearUsuario`, `VentanaMenuPrincipal` | Swing; el `Main` levanta servidor + lanzador de N clientes |

Lo que **ya está bien** y no se toca: la separación por capas, el hilo aceptador con `volatile`, el `Consumer<String>` como bitácora inyectada, los timeouts del cliente, la censura de contraseñas en el log. Eso es criterio, no accidente.

Lo que **la PP5 obliga a cambiar**: la persistencia deja de ser un archivo y pasa a MySQL, y el CRUD deja de ser solo de usuarios para cubrir Películas, Documentales y Series.

### 0.2 Dos defectos actuales que esta práctica corrige (no son opinión, son bugs)

1. **`Audiovisual.comentarios` es `static`.** Eso significa que *todos* los audiovisuales comparten la misma lista: un comentario sobre *Interstellar* aparece también en *Breaking Bad*. Es un error de modelado — el comentario pertenece a una obra concreta, no a la clase. En PP5 pasa a ser la tabla `comentario` con llave foránea a `audiovisual`. La base de datos, al forzarte a declarar la relación, hace visible el error que Java te dejó pasar en silencio.
2. **Las contraseñas se guardan en claro** en `usuarios.dat` y viajan en claro por el socket. Lo segundo no lo vamos a resolver en esta práctica (exigiría TLS). Lo primero sí: en MySQL guardamos un *hash*, no la contraseña. Ver §5.4.

### 0.3 Decisiones ya tomadas (base de esta guía)

| Decisión | Elegido | Justificación corta |
|---|---|---|
| Alcance de MySQL | Usuarios + audiovisuales + comentarios | Persistencia híbrida (mitad archivo, mitad BD) es incoherencia arquitectónica y cuesta puntos en DD.2 |
| Mapeo de herencia | Tabla base + 3 hijas 1:1 (*class table inheritance*) | Refleja el modelo Java, queda en 3FN, sin columnas NULL inútiles |
| GitHub | Repo nuevo, commit baseline con PP4 funcionando | El baseline es tu punto de retorno cuando el CRUD rompa algo |
| Motor | MySQL 8.x + Workbench + Connector/J (JDBC) | Exigencia de la consigna |

### 0.4 Supuestos declarados

- Windows, NetBeans, JDK 26 (`javac.source=26` en `nbproject/project.properties`).
- MySQL Server y Workbench se instalan en la misma máquina; el servidor Java y MySQL conviven en `localhost`.
- El grupo trabajará sobre el repo, pero esta guía asume que **vos** hacés el setup inicial y luego agregás colaboradores.
- Si algún supuesto no se cumple (por ejemplo, MySQL en otra máquina), lo que cambia es la URL JDBC y los permisos del usuario MySQL, nada más.

---

## 1. Arquitectura destino

### 1.1 El cambio en una imagen

**Antes (PP4):**

```
[Cliente Swing] --socket TCP--> [HiloCliente] --synchronized--> [usuarios.dat]
```

**Después (PP5):**

```
[Cliente Swing] --socket TCP--> [HiloCliente] --> [DAO] --JDBC--> [MySQL: fideflix]
     N ventanas                  N hilos            PreparedStatement    transacciones
```

### 1.2 El punto conceptual más importante de toda la práctica

En PP4, la concurrencia la resolviste vos con `synchronized (ServidorFideflix.CANDADO_ARCHIVO)`. Tenías que hacerlo: un archivo no tiene noción de "dos escritores a la vez", y sin candado el segundo hilo en guardar pisaba al primero (*lost update*).

**Con MySQL ese candado global sobra y además es contraproducente.** El motor ya resuelve el acceso concurrente con transacciones y bloqueo a nivel de fila (InnoDB). Si dejás el `synchronized` global, estás obligando a que todos tus hilos pasen de a uno por la base — es decir, **construiste un servidor multihilo y después lo convertiste en secuencial a mano**. Es el equivalente a comprar una autopista de seis carriles y poner un solo peaje.

> **Regla de la Fase 7:** el `synchronized` se elimina y se reemplaza por *transacciones* donde una operación toca más de una tabla. La atomicidad ya no la garantiza un candado de Java: la garantiza el `COMMIT`/`ROLLBACK` de MySQL.

Analogía: en PP4 vos eras el bibliotecario que solo dejaba entrar a una persona a la vez a la sala de archivos. En PP5 contratás una biblioteca profesional que sabe prestar cien libros distintos simultáneamente y solo bloquea *el libro* que dos personas quieren al mismo tiempo. Seguir parado en la puerta contando gente es desconfiar del profesional que contrataste.

### 1.3 Segunda regla no negociable: `Connection` no es *thread-safe*

Nunca declares un `static Connection` compartido. Cada `HiloCliente` abre su propia conexión, la usa y la cierra con *try-with-resources*. Un `Connection` compartido entre hilos produce errores erráticos e irreproducibles (mezcla de transacciones entre clientes), que son los peores de depurar.

---

## 2. Orden de fases y por qué ese orden

| # | Fase | Termina cuando... |
|---|---|---|
| 0 | Git + GitHub, baseline PP4 | Existe un commit con el proyecto PP4 compilando |
| 1 | MySQL Server + Workbench + usuario dedicado | Podés conectarte desde Workbench con el usuario `fideflix_app` |
| 2 | Diseño y creación del schema (DDL) | El script corre limpio y las tablas existen |
| 3 | Connector/J en el proyecto NetBeans | Un `Class.forName` / conexión de prueba imprime "conectado" |
| 4 | Capa de persistencia JDBC (DAOs) | Los DAOs compilan y una prueba de consola inserta y lee |
| 5 | Migración de usuarios a MySQL | Login y registro funcionan sin `usuarios.dat` |
| 6 | Extensión del protocolo | `Protocolo.java` documenta los comandos CRUD |
| 7 | Servidor: enrutamiento a DAOs, sin candado global | Dos clientes hacen CRUD simultáneo sin corromper datos |
| 8 | Cliente: ventanas de catálogo, formulario y comentarios | Un usuario logueado puede crear/editar/borrar/listar |
| 9 | Validación y pruebas | Todos los casos de §9 pasan |
| 10 | Entrega | ZIP + README + script SQL + repo público |

**Por qué Git antes que la base de datos.** Dos razones concretas:

1. **Punto de retorno.** La Fase 5 elimina `PersistenciaUsuarios` y reescribe el login. Si algo se rompe y no tenés el baseline commiteado, perdiste la PP4 funcionando y te quedaste sin entrega.
2. **El `.gitignore` hay que escribirlo *antes* de crear `db.properties`.** Si creás el archivo de credenciales primero y commiteás sin pensar, la contraseña de MySQL queda en el historial de GitHub **para siempre** — borrarla en un commit posterior no la elimina, solo la deja de mostrar en la punta. Ordenar las fases así no es manía: es evitar una fuga de credenciales que después no se puede deshacer sin reescribir historial.

**Por qué la base de datos antes que el código Java.** Porque el esquema es el contrato. Si escribís los DAOs primero y después descubrís que te falta una columna, reescribís los DAOs. El orden correcto es siempre: modelo de datos → acceso a datos → protocolo → interfaz. Las dependencias van en una sola dirección.

---

## FASE 0 — Git y GitHub (baseline)

### Por qué

Git no es "el lugar donde se sube la tarea". Es una máquina del tiempo con la que podés experimentar sin miedo. El valor real aparece cuando un cambio rompe algo y `git diff` te dice exactamente qué tocaste.

### Pasos

**0.1 Verificar / instalar Git**

```bash
git --version
```
Si no está: descargalo de git-scm.com. Configuración de identidad (una sola vez por máquina):

```bash
git config --global user.name "Tu Nombre"
git config --global user.email "tu-correo@ejemplo.com"
```

**0.2 Inicializar el repo en la carpeta de la práctica**

```bash
cd "ruta/a/practica_programada5"
git init
git branch -M main
```

**0.3 Crear el `.gitignore` ANTES del primer `git add`**

Archivo `.gitignore` en la raíz:

```gitignore
# --- Artefactos de compilación de NetBeans (se regeneran, no se versionan) ---
build/
dist/
*.class

# --- Configuración local y privada de NetBeans (rutas de TU máquina) ---
nbproject/private/

# --- Credenciales: NUNCA al repositorio ---
db.properties
**/db.properties

# --- Datos locales heredados de PP4 ---
usuarios.dat

# --- Sistema operativo / IDE ---
.DS_Store
Thumbs.db
*.log
```

Por qué cada bloque:
- `build/` y `dist/` son **salida** del compilador. Versionar salida es como guardar el pan y la harina: se regenera con un clic, y en grupo genera conflictos constantes en archivos binarios.
- `nbproject/private/` contiene rutas absolutas de tu máquina (`C:\Users\micha\...`). A tus compañeros les rompe el proyecto.
- `db.properties` es el archivo de credenciales. La regla es: **el repo lleva la plantilla, nunca los secretos.**

**0.4 Commit baseline**

```bash
git add .
git status          # LEER la lista antes de confirmar: ¿aparece algún secreto?
git commit -m "baseline: solución PP4 (cliente-servidor con sockets y archivo .dat)"
```

`git status` antes de cada commit es un hábito de higiene, no un trámite. Es el momento en que detectás que estás por subir algo que no debías.

**0.5 Crear el repo en GitHub y enlazarlo**

En github.com → New repository → nombre `fideflix-pp5` (o similar) → **sin** README ni .gitignore (ya los tenés localmente, evitás un conflicto de historias). Luego:

```bash
git remote add origin https://github.com/TU_USUARIO/fideflix-pp5.git
git push -u origin main
```

**0.6 Rama de trabajo para la PP5**

```bash
git checkout -b feature/pp5-mysql
```

Por qué una rama: `main` queda apuntando a la última versión que *funciona*. Experimentás en la rama; cuando la PP5 está estable, la fusionás. Si el profesor pide ver la entrega y tu rama está a medias, `main` sigue siendo presentable.

**0.7 Colaboradores** (Settings → Collaborators) y regla de grupo:

> **Advertencia sobre los `.form` de NetBeans.** Los archivos `.form` son XML generado por el diseñador visual y son un imán de conflictos: si dos personas editan la misma ventana, el merge es prácticamente irreconciliable. Acuerden **una persona por ventana**. No es burocracia: es la diferencia entre integrar en cinco minutos o perder una noche.

### Validación de la Fase 0

- `git log --oneline` muestra el commit baseline.
- El repo en GitHub **no** contiene `build/`, `nbproject/private/` ni `usuarios.dat`.

### Commit
`chore: configuración de git y .gitignore`

---

## FASE 1 — MySQL Server, Workbench y usuario dedicado

### Por qué

Dos ideas que suelen aprenderse tarde y duelen:

1. **Servidor ≠ cliente.** MySQL Server es el motor (un servicio que corre en segundo plano). Workbench es solo un cliente gráfico para hablarle. Podés desinstalar Workbench y tu aplicación sigue funcionando. Confundirlos lleva a preguntas como "¿por qué mi programa no encuentra la base si Workbench está abierto?".
2. **Nunca conectes tu aplicación como `root`.** `root` puede borrar cualquier base del servidor. Si tu app tiene una inyección SQL o un bug, el daño potencial es todo el motor. Un usuario con permisos solo sobre `fideflix` limita el radio de explosión. Esto es el **principio de mínimo privilegio** (NIST SP 800-53, control AC-6) y es exactamente el tipo de criterio que separa un "aplica conocimientos técnicos" de un "analiza buenas prácticas" en la rúbrica.

### Pasos

**1.1 Instalar** MySQL Community Server + MySQL Workbench (el *MySQL Installer for Windows* trae ambos). Durante la instalación:
- Tipo de configuración: *Development Computer*.
- Puerto: **3306** (por defecto).
- Método de autenticación: *Strong Password Encryption* (`caching_sha2_password`). Anotá la contraseña de `root` en un gestor, no en un `.txt` del escritorio.
- Marcá "Start MySQL Server at System Startup" para no tener que arrancarlo a mano cada vez.

**1.2 Verificar que el servicio corre**

```powershell
Get-Service -Name "MySQL*"
```
Debe decir `Running`. Alternativa gráfica: `services.msc`.

**1.3 Crear el usuario de aplicación.** Abrí Workbench, conectate como `root` y ejecutá:

```sql
-- Usuario dedicado de la aplicación. Solo puede conectarse desde la misma
-- maquina ('localhost'): si el servidor MySQL quedara expuesto en la red,
-- este usuario no serviria para entrar desde fuera.
CREATE USER 'fideflix_app'@'localhost' IDENTIFIED BY 'CambiaEstaClave_2026!';

-- Permisos MINIMOS: solo operaciones de datos, solo sobre la base fideflix.
-- Nota que NO incluimos DROP, ALTER ni CREATE: la aplicacion no tiene por que
-- poder modificar la estructura de la base en tiempo de ejecucion. Si una
-- inyeccion SQL lograra ejecutar "DROP TABLE", MySQL la rechazaria por permisos.
-- Esto es defensa en profundidad: la validacion en codigo puede fallar,
-- el permiso del motor no.
GRANT SELECT, INSERT, UPDATE, DELETE ON fideflix.* TO 'fideflix_app'@'localhost';

FLUSH PRIVILEGES;
```

> Ejecutá esto **después** de crear la base (Fase 2) o antes, indistinto: el `GRANT` sobre `fideflix.*` es válido aunque la base aún no exista.

**1.4 Probar la conexión con el usuario nuevo.** En Workbench: `+` junto a *MySQL Connections* → Connection Name `fideflix_app@localhost`, Username `fideflix_app` → *Test Connection*.

### Validación de la Fase 1

- El servicio MySQL está en `Running`.
- Podés abrir una sesión en Workbench como `fideflix_app`.
- Como `fideflix_app`, un `DROP DATABASE fideflix;` **falla** por permisos. Si funciona, revisá el `GRANT`.

---

## FASE 2 — Diseño del esquema y DDL

### Por qué: el problema de fondo

Java tiene herencia. SQL no. `Pelicula extends Audiovisual` no tiene traducción directa a tablas, y hay tres estrategias clásicas (Fowler, *Patterns of Enterprise Application Architecture*):

| Estrategia | Cómo | Ventaja | Costo |
|---|---|---|---|
| *Single table* | Una tabla con columna `tipo` y todos los campos nulables | Cero JOINs | Una serie tendría `director` y `estudio` en NULL: la tabla miente sobre su propio dominio |
| *Concrete table* | Tres tablas independientes completas | Simple | `titulo`, `genero`, `imdb` duplicados en tres lugares → anomalías de actualización |
| ***Class table*** (elegida) | Tabla base + una hija por subclase, 1:1 | 3FN real, sin NULLs artificiales, refleja el modelo Java | JOINs y una transacción por inserción |

Elegimos *class table* porque el indicador DD.2 evalúa explícitamente **"analiza formas de normalización"**. Con esta estrategia podés defender cada tabla; con la primera tenés que justificar por qué la mitad de tus columnas están vacías.

### Decisiones de modelado y su justificación

- **`clasificacion` y `genero` como tablas de catálogo.** Son dominios cerrados y repetidos. Como texto libre, tarde o temprano tenés `"Ciencia Ficción"`, `"ciencia ficcion"` y `"Sci-Fi"` conviviendo, y ningún `GROUP BY` sirve. Como llave foránea, la integridad la garantiza el motor y los `JComboBox` de la interfaz se llenan solos desde la tabla.
  *Escape hatch honesto:* si vas muy justo de tiempo, degradalos a `VARCHAR(50)`. Perdés normalización pero la práctica sigue siendo válida. No lo recomiendo.
- **`tipo` en la tabla base es una denormalización controlada.** Estrictamente es redundante (podrías deducir el tipo con tres `LEFT JOIN`). Lo incluimos porque listar el catálogo es la operación más frecuente y así se resuelve con un `SELECT` simple. **Declaralo como decisión consciente en el README**: una denormalización justificada suma; una accidental resta.
- **`ON DELETE CASCADE`** en las hijas y en `comentario`: borrar un audiovisual debe borrar su fila hija y sus comentarios. Sin cascada quedan filas huérfanas — basura que ninguna consulta encuentra pero que ocupa espacio y rompe conteos.
- **`contrasena_hash CHAR(64)`**: guardamos SHA-256 en hexadecimal, no la contraseña. Ver §5.4 para las limitaciones de esta elección, que hay que declarar.

### Script DDL

Guardalo como `sql/01_schema.sql` en el repo (**el script SQL es parte de la entrega**, no un archivo suelto en tu escritorio).

```sql
-- =====================================================================
-- Fideflix - Practica Programada 5
-- Script de creacion del esquema. Idempotente: se puede correr de nuevo.
-- Motor: MySQL 8.x / InnoDB
-- ADVERTENCIA: DROP DATABASE borra todos los datos. En un entorno real
-- jamas se ejecuta contra produccion; aqui es una base de desarrollo.
-- =====================================================================

DROP DATABASE IF EXISTS fideflix;
CREATE DATABASE fideflix
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
-- utf8mb4: soporta tildes, enes y emojis (el viejo "utf8" de MySQL era
-- de 3 bytes e incompleto). ai_ci = accent-insensitive, case-insensitive:
-- buscar "avatar" encuentra "Avatar".

USE fideflix;

-- ---------------------------------------------------------------------
-- Catalogos (dominios cerrados)
-- ---------------------------------------------------------------------
CREATE TABLE clasificacion (
    id     TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL UNIQUE   -- G, PG, PG-13, R, TV-MA
) ENGINE=InnoDB;

CREATE TABLE genero (
    id     SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Usuarios (reemplaza usuarios.dat)
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(80)  NOT NULL,
    -- UNIQUE sobre email = equivalente en la BD del Usuario.equals()
    -- que ya escribiste en Java. La diferencia: aqui la garantia es del
    -- motor y ningun hilo puede saltarsela por una condicion de carrera.
    email           VARCHAR(120) NOT NULL UNIQUE,
    contrasena_hash CHAR(64)     NOT NULL,  -- SHA-256 en hexadecimal
    fecha_registro  DATE         NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Tabla base de la jerarquia (atributos comunes de Audiovisual)
-- ---------------------------------------------------------------------
CREATE TABLE audiovisual (
    id                INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    titulo            VARCHAR(150) NOT NULL,
    descripcion       TEXT,
    anio_estreno      SMALLINT UNSIGNED,
    calificacion_imdb DECIMAL(3,1),
    clasificacion_id  TINYINT UNSIGNED,
    genero_id         SMALLINT UNSIGNED,
    -- Discriminador: denormalizacion CONSCIENTE (ver README).
    tipo              ENUM('PELICULA','DOCUMENTAL','SERIE') NOT NULL,

    CONSTRAINT fk_av_clasificacion FOREIGN KEY (clasificacion_id)
        REFERENCES clasificacion(id) ON DELETE SET NULL,
    CONSTRAINT fk_av_genero FOREIGN KEY (genero_id)
        REFERENCES genero(id) ON DELETE SET NULL,

    -- CHECK: la BD tambien valida. La validacion en la interfaz se puede
    -- saltar (un cliente modificado, una peticion cruda por telnet);
    -- la del motor no. Defensa en profundidad aplicada a datos.
    CONSTRAINT ck_imdb CHECK (calificacion_imdb BETWEEN 0 AND 10),
    CONSTRAINT ck_anio CHECK (anio_estreno BETWEEN 1888 AND 2200),

    -- No dos obras con mismo titulo y mismo anio.
    CONSTRAINT uq_titulo_anio UNIQUE (titulo, anio_estreno)
) ENGINE=InnoDB;

CREATE INDEX idx_av_tipo   ON audiovisual(tipo);
CREATE INDEX idx_av_titulo ON audiovisual(titulo);
-- Por que indices: sin ellos, filtrar por tipo obliga a un recorrido
-- completo de la tabla. Con pocos registros no se nota; el habito de
-- indexar lo que se filtra si.

-- ---------------------------------------------------------------------
-- Tablas hijas. La PK es tambien FK: relacion 1:1 estricta.
-- ---------------------------------------------------------------------
CREATE TABLE pelicula (
    audiovisual_id INT UNSIGNED PRIMARY KEY,
    duracion_min   SMALLINT UNSIGNED,
    director       VARCHAR(120),
    estudio        VARCHAR(120),
    CONSTRAINT fk_pelicula_av FOREIGN KEY (audiovisual_id)
        REFERENCES audiovisual(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE documental (
    audiovisual_id INT UNSIGNED PRIMARY KEY,
    duracion_min   SMALLINT UNSIGNED,
    director       VARCHAR(120),
    tema           VARCHAR(120),
    CONSTRAINT fk_documental_av FOREIGN KEY (audiovisual_id)
        REFERENCES audiovisual(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE serie (
    audiovisual_id  INT UNSIGNED PRIMARY KEY,
    num_temporadas  TINYINT UNSIGNED,
    num_episodios   SMALLINT UNSIGNED,
    estado          VARCHAR(30),
    CONSTRAINT fk_serie_av FOREIGN KEY (audiovisual_id)
        REFERENCES audiovisual(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Comentarios: corrige el bug de la lista 'static' de PP4.
-- Cada comentario pertenece a UNA obra y a UN usuario.
-- ---------------------------------------------------------------------
CREATE TABLE comentario (
    id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    audiovisual_id INT UNSIGNED NOT NULL,
    usuario_id     INT UNSIGNED NOT NULL,
    texto          TEXT NOT NULL,
    fecha_hora     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_com_av FOREIGN KEY (audiovisual_id)
        REFERENCES audiovisual(id) ON DELETE CASCADE,
    CONSTRAINT fk_com_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_com_av ON comentario(audiovisual_id);
```

Y `sql/02_datos_iniciales.sql`:

```sql
USE fideflix;

INSERT INTO clasificacion (codigo) VALUES
    ('G'), ('PG'), ('PG-13'), ('R'), ('TV-MA'), ('TV-14');

INSERT INTO genero (nombre) VALUES
    ('Ciencia Ficcion'), ('Drama'), ('Accion'), ('Comedia'),
    ('Documental'), ('Suspenso'), ('Naturaleza'), ('Historia');

-- Ejemplo de insercion completa de una pelicula: DOS tablas, UNA transaccion.
-- Si el segundo INSERT fallara y no hubiera transaccion, quedaria un
-- audiovisual sin su fila hija: un registro invalido e invisible.
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('Interstellar', 'Exploradores viajan por un agujero de gusano.',
        2014, 8.7,
        (SELECT id FROM clasificacion WHERE codigo = 'PG-13'),
        (SELECT id FROM genero WHERE nombre = 'Ciencia Ficcion'),
        'PELICULA');
INSERT INTO pelicula (audiovisual_id, duracion_min, director, estudio)
VALUES (LAST_INSERT_ID(), 169, 'Christopher Nolan', 'Paramount');
COMMIT;
```

### Consulta de lectura del catálogo (la vas a usar en el DAO)

```sql
-- LEFT JOIN a las tres hijas: solo una traera datos segun el tipo.
SELECT a.id, a.titulo, a.anio_estreno, a.calificacion_imdb, a.tipo,
       c.codigo AS clasificacion, g.nombre AS genero,
       COALESCE(p.duracion_min, d.duracion_min)      AS duracion,
       COALESCE(p.director,     d.director)          AS director,
       p.estudio, d.tema,
       s.num_temporadas, s.num_episodios, s.estado
FROM audiovisual a
LEFT JOIN clasificacion c ON c.id = a.clasificacion_id
LEFT JOIN genero        g ON g.id = a.genero_id
LEFT JOIN pelicula      p ON p.audiovisual_id = a.id
LEFT JOIN documental    d ON d.audiovisual_id = a.id
LEFT JOIN serie         s ON s.audiovisual_id = a.id
ORDER BY a.titulo;
```

### Validación de la Fase 2

- El script corre sin errores desde Workbench (como `root` para el DDL).
- `SHOW TABLES;` lista las 8 tablas.
- Prueba de cascada: `DELETE FROM audiovisual WHERE titulo='Interstellar';` → `SELECT COUNT(*) FROM pelicula;` devuelve una fila menos.
- Prueba de constraint: insertar `calificacion_imdb = 15` debe **fallar**. Si no falla, el `CHECK` no se aplicó (MySQL solo los respeta desde 8.0.16 — verificá versión con `SELECT VERSION();`).

### Commit
`feat(db): esquema normalizado de fideflix con herencia por tablas`

---

## FASE 3 — Connector/J en NetBeans

### Por qué

JDBC es una **interfaz estándar** de Java; no sabe hablar MySQL. El Connector/J es el *driver*: el traductor entre las llamadas JDBC y el protocolo de red que entiende MySQL. Analogía: JDBC es el idioma común y el driver es el intérprete para ese país concreto. Cambiar de MySQL a PostgreSQL cambia el intérprete, no tu código — ese desacoplamiento es precisamente el valor de JDBC.

### Pasos

1. Descargá **MySQL Connector/J** desde dev.mysql.com/downloads/connector/j (elegí *Platform Independent*, extraé el `.jar`). Verificá cuál es la versión vigente en el sitio; debe ser compatible con tu MySQL Server 8.x.
2. Creá una carpeta `lib/` en la raíz del repo y copiá ahí el `.jar`. **Sí se versiona**: que tus compañeros clonen y compile sin cazar descargas.
3. NetBeans: clic derecho en el proyecto → *Properties* → *Libraries* → *Add JAR/Folder* → seleccioná el `.jar` con ruta **relativa**.
4. Verificá que `nbproject/project.properties` ahora tenga el jar en `javac.classpath` (hoy está vacío) y commiteá ese cambio.

**Conexión de prueba** (clase temporal, se borra después):

```java
// Prueba minima de conectividad. Se ejecuta UNA vez y se elimina.
// Desde Connector/J 8 no hace falta Class.forName(): el driver se
// registra solo via ServiceLoader (JDBC 4.0+).
try (java.sql.Connection c = java.sql.DriverManager.getConnection(
        "jdbc:mysql://localhost:3306/fideflix?serverTimezone=America/Costa_Rica",
        "fideflix_app", "CambiaEstaClave_2026!")) {
    System.out.println("Conectado a: " + c.getMetaData().getDatabaseProductVersion());
}
```

### Errores típicos y su lectura

| Error | Qué significa realmente |
|---|---|
| `No suitable driver found` | El `.jar` no está en el classpath de **ejecución**, no solo de compilación |
| `Communications link failure` | El servicio MySQL no corre, o el puerto está mal |
| `Access denied for user` | Usuario/contraseña/host equivocados — ojo con `'fideflix_app'@'localhost'` vs `@'%'` |
| `Unknown database 'fideflix'` | No corriste el script DDL |

Leer el error antes de googlearlo es una habilidad; los cuatro de arriba te dicen exactamente dónde mirar.

### Commit
`build: agrega MySQL Connector/J al classpath`

---

## FASE 4 — Capa de persistencia JDBC (DAOs)

### Por qué el patrón DAO

*Data Access Object*: una clase por entidad que concentra todo el SQL. La regla es **cero SQL fuera de `fideflix.persistencia`**. Si mañana cambia una tabla, tocás un archivo; si el SQL está desparramado entre las ventanas y los hilos, tocás doce y te olvidás de tres. Es la misma lógica por la que `HiloCliente` no sabe nada de Swing.

### Estructura a crear

```
fideflix/persistencia/
    ConexionBD.java        <- obtiene conexiones, lee credenciales de db.properties
    UsuarioDAO.java        <- registrar, autenticar
    AudiovisualDAO.java    <- CRUD completo con transacciones
    ComentarioDAO.java     <- insertar y listar por obra
    CatalogoDAO.java       <- generos y clasificaciones (para los combos)
```

`PersistenciaUsuarios.java` se **elimina** al terminar la Fase 5 (no antes: sirve para la migración).

### 4.1 `ConexionBD` y el archivo de credenciales

`db.properties` (en la raíz del proyecto NetBeans, **ignorado por git**):

```properties
db.url=jdbc:mysql://localhost:3306/fideflix?serverTimezone=America/Costa_Rica&useSSL=false&allowPublicKeyRetrieval=true
db.user=fideflix_app
db.password=CambiaEstaClave_2026!
```

`db.properties.example` (**este sí se versiona**, con valores ficticios):

```properties
db.url=jdbc:mysql://localhost:3306/fideflix?serverTimezone=America/Costa_Rica
db.user=fideflix_app
db.password=REEMPLAZAR
```

> **Advertencia de seguridad — `useSSL=false`.** Desactiva el cifrado del canal entre la app y MySQL. Es tolerable **solo** porque ambos están en `localhost` (el tráfico no sale de la máquina). En cualquier despliegue real, `useSSL=false` significa que cualquiera en la red puede leer tus consultas y credenciales. Documentá esta limitación en el README: reconocer un riesgo asumido conscientemente vale más que ocultarlo.

Esqueleto de `ConexionBD`:

```java
package fideflix.persistencia;
// Responsabilidad unica: entregar conexiones configuradas.
// Las credenciales NO se escriben en el codigo: van en db.properties,
// que esta en .gitignore. Codigo y secretos tienen ciclos de vida
// distintos (el codigo se comparte, los secretos no).
public final class ConexionBD {
    // Se cargan una sola vez al iniciar (bloque static).
    // IMPORTANTE: se devuelve una Connection NUEVA por llamada.
    // Nunca una static compartida: Connection NO es thread-safe y este
    // servidor atiende N clientes en N hilos simultaneos.
    public static java.sql.Connection obtener() throws java.sql.SQLException { ... }
}
```

### 4.2 La regla que no se negocia: `PreparedStatement`

**Jamás** construyas SQL concatenando texto que vino del usuario:

```java
// PROHIBIDO. Vulnerable a inyeccion SQL (OWASP Top 10 A03:2021, CWE-89).
String sql = "SELECT * FROM usuario WHERE email = '" + email + "'";
```

Si el usuario escribe `' OR '1'='1`, la consulta devuelve todos los usuarios y el login se salta entero.

```java
// CORRECTO: PreparedStatement con parametros.
String sql = "SELECT id, nombre, fecha_registro FROM usuario "
           + "WHERE email = ? AND contrasena_hash = ?";
try (PreparedStatement ps = con.prepareStatement(sql)) {
    ps.setString(1, email);
    ps.setString(2, hash);
    ...
}
```

**Por qué funciona** (y esto es lo que hay que entender, no memorizar): el `PreparedStatement` envía la *estructura* de la consulta al motor **antes** que los datos. MySQL ya decidió que eso es un `SELECT` con dos filtros; los valores llegan después, por un canal separado, y se tratan como datos puros. No hay forma de que un dato "se convierta" en instrucción. No es escapar comillas: es separar el código de los datos, de raíz.

### 4.3 Transacción en `AudiovisualDAO.insertar()`

Insertar una película toca dos tablas. O entran las dos o no entra ninguna:

```java
// Patron de transaccion. Sin esto, un fallo en el segundo INSERT deja
// una fila en 'audiovisual' sin su hija: un registro corrupto que la
// consulta del catalogo mostraria a medias.
Connection con = ConexionBD.obtener();
try {
    con.setAutoCommit(false);            // 1. desactivar el commit implicito

    // 2. INSERT en la tabla base, pidiendo la llave generada
    PreparedStatement psBase = con.prepareStatement(SQL_BASE,
            Statement.RETURN_GENERATED_KEYS);
    // ... setters ...
    psBase.executeUpdate();
    int idGenerado;
    try (ResultSet rs = psBase.getGeneratedKeys()) {
        rs.next();
        idGenerado = rs.getInt(1);
    }

    // 3. INSERT en la hija usando esa llave
    // ... segun el tipo: pelicula / documental / serie ...

    con.commit();                        // 4. todo o nada
} catch (SQLException e) {
    con.rollback();                      // 5. deshacer TODO lo del try
    throw e;
} finally {
    con.setAutoCommit(true);
    con.close();
}
```

> **Error clásico:** hacer `rollback()` sin haber hecho `setAutoCommit(false)`. En modo autocommit cada sentencia se confirma sola y el rollback no deshace nada. La transacción empieza cuando desactivás autocommit, no cuando pensás en ella.

### 4.4 Cambio necesario en el modelo de dominio

Agregá un campo `id` (entero) a `Audiovisual` y a `Usuario`, con su getter/setter. Sin identificador no hay `UPDATE ... WHERE id = ?` ni `DELETE`: hoy tus objetos se identifican por título o email, y el CRUD necesita una llave estable que no cambie cuando el usuario corrige un título.

También: quitá el `static` de `comentarios` en `Audiovisual` (o eliminá la lista y el método, ya que los comentarios ahora viven en la BD y se consultan por demanda).

### Validación de la Fase 4

- Una clase `main` temporal inserta una película y la lee de vuelta.
- Provocá un fallo a propósito en el segundo INSERT (por ejemplo, un `duracion_min` fuera de rango) y verificá con Workbench que **no** quedó fila en `audiovisual`. Si quedó, tu transacción no está funcionando.

### Commit
`feat(persistencia): capa DAO con JDBC, PreparedStatement y transacciones`

---

## FASE 5 — Migrar usuarios de `.dat` a MySQL

### Por qué primero los usuarios y no los audiovisuales

Porque el flujo de usuarios ya existe end-to-end (ventana → cliente → protocolo → hilo → persistencia). Migrar **lo que ya funciona** te deja validar toda la cadena JDBC con una funcionalidad conocida. Si empezaras por el CRUD de audiovisuales, un fallo podría estar en el DAO, en el protocolo nuevo, en la ventana nueva o en el enrutamiento: cuatro sospechosos. Migrando usuarios, hay uno solo.

Es depuración por reducción de variables, y aplica a todo lo que programes de acá en adelante.

### Pasos

1. Escribir `UsuarioDAO.registrar(...)` y `UsuarioDAO.autenticar(email, hash)`.
2. En `HiloCliente.procesarCrear()` y `procesarLogin()`: reemplazar las llamadas a `PersistenciaUsuarios` por el DAO.
3. **Eliminar el `synchronized (ServidorFideflix.CANDADO_ARCHIVO)`** de ambos métodos (ver §1.2). El `UNIQUE` sobre `email` es ahora quien impide duplicados, incluso si dos hilos registran el mismo correo en el mismo milisegundo — algo que tu `contains()` en memoria sí podía dejar pasar en una condición de carrera.
4. Manejar `SQLIntegrityConstraintViolationException` → responder `DUPLICADO|email`. **Este es el cambio mental clave:** antes preguntabas "¿ya existe?" y después insertabas (dos pasos, ventana de carrera entre ellos). Ahora intentás insertar y dejás que el motor te diga si chocó (un paso atómico). Es la diferencia entre *look before you leap* y *easier to ask forgiveness*: en concurrencia, la segunda gana.
5. Eliminar `PersistenciaUsuarios.java`, la constante `CANDADO_ARCHIVO` y el archivo `usuarios.dat`.

### 5.1 Hashing de contraseñas

```java
// Se guarda el hash, no la contrasena. Si la base se filtra, el atacante
// no obtiene credenciales utilizables directamente.
//
// ADVERTENCIA (declararla en el README): SHA-256 sin salt es el MINIMO
// aceptable para una practica academica, NO para produccion. Es un hash
// rapido: una GPU prueba miles de millones por segundo, y sin salt dos
// usuarios con la misma clave producen el mismo hash (visible en la tabla).
// Produccion exige bcrypt, scrypt o Argon2id, que son deliberadamente
// lentos y llevan salt por diseno. Ver OWASP Password Storage Cheat Sheet.
private static String hashear(String texto) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] bytes = md.digest(texto.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
}
```

El hasheo va **en el servidor**, no en el cliente. Si el cliente enviara el hash, ese hash *sería* la credencial: quien lo capture entra sin conocer la contraseña original. (La contraseña sigue viajando en claro por el socket — limitación heredada que solo TLS resuelve. Declarala, no la escondas.)

### Validación de la Fase 5

- Registrar un usuario nuevo → aparece en `SELECT * FROM usuario;` con hash de 64 caracteres hexadecimales.
- Login correcto → entra. Login con clave errada → `DENEGADO`.
- Registrar dos veces el mismo email → `DUPLICADO`.
- **`usuarios.dat` no existe y la aplicación funciona igual.**

### Commit
`refactor(usuarios): migra autenticacion de archivo serializado a MySQL`

---

## FASE 6 — Extensión del protocolo

### Por qué hay que rediseñar y no solo agregar comandos

Tu protocolo actual tiene dos límites que el CRUD de audiovisuales rompe de inmediato:

1. **El separador `|` colisiona con el texto libre.** Una descripción o un comentario pueden contener `|`. Peor: pueden contener un **salto de línea**, y como el protocolo es "una línea por mensaje" (`readLine()`), un enter en la descripción parte el mensaje en dos y el servidor lee basura. No es hipotético: pasa la primera vez que alguien pega texto de Wikipedia.
2. **Una respuesta = una línea.** `LISTAR` devuelve N registros. No entran en una línea de forma legible.

### Solución 1: codificar los campos de texto libre

```java
// En Protocolo.java. Sustituye los caracteres que romperian el formato
// por secuencias inofensivas, y los restaura del otro lado.
// El orden importa: al desescapar, primero los marcadores propios.
public static String escapar(String s) {
    if (s == null) return "";
    return s.replace("|", "&sep;")
            .replace("\r", "")
            .replace("\n", "&nl;");
}
public static String desescapar(String s) {
    return s.replace("&nl;", "\n").replace("&sep;", "|");
}
```

### Solución 2: respuestas multilínea con encabezado de conteo

```
Cliente:   LISTAR|TODOS
Servidor:  OK|3          <- encabezado: cuantas lineas vienen
           1|PELICULA|Interstellar|2014|8.7|PG-13|Ciencia Ficcion|169|Nolan|Paramount
           2|SERIE|Breaking Bad|2008|9.5|TV-MA|Drama|5|62|Finalizada
           3|DOCUMENTAL|Planet Earth|2006|9.4|G|Naturaleza|550|Fothergill|Naturaleza
```

El cliente lee la primera línea, extrae `n`, y hace exactamente `n` `readLine()`. Sin ambigüedad y sin necesidad de un marcador de fin. Es el mismo principio que el `Content-Length` de HTTP: decir cuánto viene antes de mandarlo.

### Comandos a agregar en `Protocolo.java`

```java
// ─── CRUD de audiovisuales ───────────────────────────────────────────
public static final String CMD_LISTAR      = "LISTAR";      // LISTAR|TODOS|PELICULA|SERIE|DOCUMENTAL
public static final String CMD_OBTENER     = "OBTENER";     // OBTENER|id
public static final String CMD_CREAR_AV    = "CREAR_AV";    // CREAR_AV|tipo|titulo|desc|anio|imdb|clasifId|generoId|e1|e2|e3
public static final String CMD_ACTUALIZAR  = "ACTUALIZAR";  // ACTUALIZAR|id|...(mismos campos)
public static final String CMD_ELIMINAR    = "ELIMINAR";    // ELIMINAR|id
// ─── Comentarios y catalogos ─────────────────────────────────────────
public static final String CMD_COMENTAR        = "COMENTAR";        // COMENTAR|idAv|idUsuario|texto
public static final String CMD_LISTAR_COMENTS  = "LISTAR_COMENTS";  // LISTAR_COMENTS|idAv
public static final String CMD_CATALOGOS       = "CATALOGOS";       // para poblar los JComboBox
// ─── Nuevo codigo de respuesta ───────────────────────────────────────
public static final String RSP_NO_ENCONTRADO = "NO_ENCONTRADO";     // id inexistente
```

Los campos `e1|e2|e3` son los tres específicos de cada subtipo (película: duración/director/estudio; documental: duración/director/tema; serie: temporadas/episodios/estado). Un solo formato de mensaje para los tres tipos evita triplicar el enrutador. Documentalo en el encabezado de `Protocolo.java` con el mismo estilo que ya usás — ese archivo es el contrato entre las dos mitades del sistema y debe leerse solo.

### Commit
`feat(protocolo): comandos CRUD, escape de texto y respuestas multilinea`

---

## FASE 7 — Servidor: enrutamiento a DAOs

### Por qué hay que cambiar el ciclo de vida del hilo

Hoy `HiloCliente.run()` hace: leer **una** petición → responder → cerrar. Para una interfaz de catálogo eso significa abrir y cerrar un socket TCP por cada clic (listar, editar, guardar, borrar). Funciona, pero desperdicia el *handshake* de TCP en cada acción.

**Dos opciones, elegí una y justificala:**

| Opción | Cómo | Cuándo conviene |
|---|---|---|
| **A. Mantener conexión por petición** | No tocás `run()`, solo agregás casos al `switch` | Menos cambios, menor riesgo. Suficiente para la práctica |
| **B. Bucle de peticiones** | `while ((peticion = entrada.readLine()) != null)` — el hilo vive mientras el cliente está conectado | Más eficiente y más realista, pero exige comando `SALIR`, manejo de clientes que se caen sin avisar, y timeouts |

Recomendación: **A**, y mencionás B en el README como evolución natural. La opción B mejora rendimiento pero introduce gestión de sesión, que es una fuente de bugs que no aporta puntos en esta rúbrica. Elegir lo simple *sabiendo* que existe lo complejo es criterio; elegirlo por desconocimiento, no.

### Cambios concretos

1. Ampliar el `switch` de `procesar()` con los comandos nuevos, un método privado por comando (mantené el estilo actual: `procesarCrearAv`, `procesarListar`, ...).
2. **Eliminar todos los `synchronized`.** Ya no hay recurso compartido en memoria que proteger.
3. Cada método privado: abre conexión vía DAO, ejecuta, mapea el resultado al formato del protocolo.
4. Manejo de errores: capturar `SQLException` y responder `ERROR|mensaje_generico`. **No devuelvas `e.getMessage()` crudo al cliente**: los mensajes de MySQL revelan nombres de tablas, columnas y a veces fragmentos de la consulta — información útil para un atacante (OWASP A05:2021, *Security Misconfiguration*). El detalle completo va a la bitácora del servidor; al cliente, un mensaje neutro.
5. Extender `censurar()` para que no registre textos de comentarios completos en el log si son largos.
6. En `ClienteFideflix`, agregar `enviarPeticionMultilinea(String)` que devuelva `List<String>` leyendo el conteo del encabezado.

### Validación de la Fase 7

Esta es **la prueba que demuestra el requisito de hilos de la consigna**:

1. Abrí el proyecto, entrá con dos clientes distintos desde el lanzador.
2. Cliente A crea una película; cliente B lista el catálogo → **B ve la película de A**. Eso prueba que el estado es compartido y vive en el servidor, no en cada cliente.
3. La bitácora de `VentanaServidor` muestra conexiones con nombres de hilo distintos (`cliente-1`, `cliente-2`).
4. Cliente A y B editan **el mismo registro** casi a la vez: no hay corrupción; gana el último `UPDATE` (comportamiento esperado y declarable como limitación: *last write wins*, sin bloqueo optimista).

### Commit
`feat(servidor): enrutamiento CRUD a DAOs y eliminacion del candado global`

---

## FASE 8 — Cliente: ventanas nuevas

### Por qué estas tres ventanas y no otras

La consigna pide "modifique y cree las ventanas necesarias". *Necesarias* se deduce de las operaciones: listar (una vista), crear/editar (un formulario), comentar (un diálogo). Tres ventanas cubren el CRUD completo sin inventar pantallas que no aportan.

| Ventana | Contenido | Nota |
|---|---|---|
| `VentanaCatalogo` | `JTable` + filtro por tipo + botones Nuevo / Editar / Eliminar / Comentarios / Actualizar | El `JTable` con `DefaultTableModel` es el corazón de la vista |
| `VentanaAudiovisualForm` | Campos comunes + panel que cambia según el tipo (`CardLayout`) + combos de género y clasificación | Sirve para crear **y** editar: si recibe un id, precarga; si no, crea |
| `VentanaComentarios` | Lista de comentarios de una obra + caja de texto para agregar | Aquí se ve el resultado de corregir el bug del `static` |

`VentanaMenuPrincipal` se modifica: agregar el botón que abre el catálogo, y guardar el `Usuario` autenticado (con su `id`) para poder atribuir los comentarios.

### Detalles de implementación que importan

**1. `CardLayout` para los campos específicos.** En vez de tres formularios casi idénticos, uno solo con un panel que intercambia las tres variantes según el `JComboBox` de tipo. Menos código duplicado, un solo lugar donde corregir.

**2. Confirmación antes de eliminar.**

```java
// Toda operacion destructiva se confirma. El borrado es en cascada:
// tambien desaparecen los comentarios asociados. Decirselo al usuario
// no es cortesia, es consentimiento informado.
int r = JOptionPane.showConfirmDialog(this,
        "Se eliminara \"" + titulo + "\" y todos sus comentarios.\n"
      + "Esta accion no se puede deshacer. Continuar?",
        "Confirmar eliminacion", JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE);
if (r != JOptionPane.YES_OPTION) return;
```

**3. Validar en el cliente Y en el servidor.** El cliente valida para dar buena experiencia (avisar antes de mandar); el servidor valida porque **el cliente no es confiable**. Cualquiera puede mandar una línea cruda al puerto 5000 con `telnet` o `netcat`. Validación en la interfaz = comodidad; validación en el servidor = seguridad. Nunca son la misma cosa, aunque el código se parezca.

**4. Regla de oro de Swing, que ya venís respetando.** Todo lo que toca componentes va en el EDT (`EventQueue.invokeLater`). Y al revés: si una consulta se vuelve lenta, la llamada al servidor no debe hacerse en el EDT — congelaría la ventana. Para esta práctica, con datos locales y pocas filas, la llamada directa es aceptable; si notás congelamientos, la herramienta correcta es `SwingWorker` (`doInBackground()` consulta, `done()` actualiza la tabla). Mencionalo en el README.

### Validación de la Fase 8

Recorrido completo, con dos clientes abiertos:
- Registrar usuario → login → menú → catálogo carga desde MySQL.
- Crear una serie → aparece en la tabla del **otro** cliente al refrescar → aparece en Workbench.
- Editar el título → cambia en la BD.
- Agregar comentario → aparece asociado solo a esa obra (no a todas: el bug del `static` quedó atrás).
- Eliminar → desaparece la fila y sus comentarios.

### Commit
`feat(interfaz): catalogo, formulario de audiovisuales y comentarios`

---

## FASE 9 — Validación y pruebas

Marcá cada casilla. Lo que no se prueba, no funciona: solo no ha fallado todavía.

### Funcional

- [ ] Registro de usuario nuevo → fila en `usuario` con hash de 64 hex.
- [ ] Registro con email repetido → `DUPLICADO`, sin fila nueva.
- [ ] Login correcto / login incorrecto.
- [ ] Crear los tres tipos (película, documental, serie) → fila en base + fila en la hija correcta.
- [ ] Listar todos y filtrar por cada tipo.
- [ ] Actualizar campos comunes y específicos.
- [ ] Eliminar → cascada verificada en `pelicula`/`serie`/`documental` y en `comentario`.
- [ ] Comentar y listar comentarios por obra.

### Concurrencia (el requisito explícito de la consigna)

- [ ] Dos clientes conectados a la vez; la bitácora muestra hilos distintos.
- [ ] A crea, B refresca y lo ve.
- [ ] A y B crean simultáneamente → ambos registros existen, ninguno se pierde.
- [ ] Detener el servidor con clientes abiertos → los clientes muestran error controlado, no una excepción sin manejar.

### Robustez

- [ ] Servidor apagado + cliente intenta operar → mensaje claro, sin *stack trace* en pantalla.
- [ ] MySQL detenido (`net stop MySQL80`) + servidor corriendo → error controlado en la bitácora.
- [ ] Campos vacíos, año `"abc"`, IMDb `15` → rechazados con mensaje.
- [ ] Reiniciar todo → los datos siguen ahí (esto es lo que prueba que la persistencia es real).

### Seguridad

- [ ] **Prueba de inyección SQL**: crear una película con título
      `Prueba'); DROP TABLE comentario; --`
      Resultado esperado: se guarda como **texto literal** y la tabla `comentario` sigue existiendo. Si desapareciera, tenés concatenación en algún DAO.
- [ ] Ninguna contraseña aparece en la bitácora del servidor.
- [ ] `git log -p | grep -i "password"` no muestra credenciales reales.
- [ ] Como `fideflix_app`, `DROP TABLE usuario;` falla por permisos.

> Si alguna de estas cuatro falla, no es un detalle: es la diferencia entre "aplica conocimientos técnicos" (nivel 2) y "analiza buenas prácticas" (nivel 3) en DD.1.

---

## FASE 10 — Entrega

### Contenido del repositorio

```
fideflix-pp5/
├── README.md                    <- ver plantilla abajo
├── .gitignore
├── db.properties.example
├── sql/
│   ├── 01_schema.sql
│   └── 02_datos_iniciales.sql
├── docs/
│   └── modelo_er.png            <- exportado desde Workbench (Database > Reverse Engineer)
├── lib/
│   └── mysql-connector-j-<version>.jar
└── Fideflix/                    <- proyecto NetBeans
```

**El diagrama ER se genera solo:** Workbench → *Database* → *Reverse Engineer* → seleccionar `fideflix`. Exportalo como imagen. Un profesor que ve el ER entiende tu modelo en diez segundos; sin él tiene que leer el DDL. Es el mejor retorno por esfuerzo de toda la entrega.

### README mínimo

1. Descripción y alcance de la PP5.
2. Requisitos (JDK, MySQL 8.x, Connector/J versión X).
3. **Instalación paso a paso**: correr `01_schema.sql`, `02_datos_iniciales.sql`, crear usuario MySQL, copiar `db.properties.example` a `db.properties` y completar.
4. Ejecución: `Main.java` levanta servidor + lanzador.
5. Modelo de datos + imagen del ER + justificación de la estrategia de herencia.
6. Protocolo: tabla de comandos y respuestas.
7. **Decisiones de diseño**: por qué *class table inheritance*, por qué el discriminador `tipo`, por qué conexión por petición.
8. **Limitaciones conocidas y declaradas** (§ siguiente).
9. Integrantes del grupo.

El punto 8 es el que más separa una entrega de otra. Un estudiante que lista sus propias limitaciones demuestra que entiende el sistema; uno que las omite deja al evaluador la duda de si las desconoce.

### Merge y ZIP

```bash
git checkout main
git merge feature/pp5-mysql
git tag -a v5.0 -m "Practica Programada 5 - MySQL"
git push origin main --tags
```

El ZIP para la plataforma: exportar desde el repo limpio (`git archive`) o comprimir la carpeta **sin** `build/`, `dist/` ni `db.properties`.

---

## Riesgos y limitaciones (declaralas en el README)

| Limitación | Impacto real | Cómo se resuelve en producción |
|---|---|---|
| Contraseñas en claro por el socket | Cualquiera con acceso a la red captura credenciales | `SSLSocket` / TLS |
| SHA-256 sin *salt* | Vulnerable a *rainbow tables* y a fuerza bruta con GPU | bcrypt / scrypt / Argon2id |
| `useSSL=false` en la URL JDBC | Tráfico app↔MySQL sin cifrar | TLS entre aplicación y motor |
| Conexión JDBC nueva por petición | Coste de *handshake* en cada operación | Pool de conexiones (HikariCP) |
| *Thread-per-connection* | No escala más allá de cientos de clientes | `ExecutorService` con pool acotado, o NIO |
| Sin bloqueo optimista | Dos ediciones simultáneas: gana la última, la primera se pierde en silencio | Columna `version` + `UPDATE ... WHERE version = ?` |
| Protocolo de texto propio | Frágil ante campos con caracteres especiales | JSON + una librería de serialización |
| Sin control de roles | Cualquier usuario logueado puede borrar cualquier obra | Tabla de roles y verificación en el servidor |

Ninguna de estas es un defecto de la práctica: son el límite del alcance. Nombrarlas es lo que convierte una tarea en un análisis.

---

## Mapeo a la rúbrica

| Indicador | Dónde se evidencia |
|---|---|
| **DD.1** Desarrolla los requerimientos | CRUD completo de los 3 tipos (Fases 4, 7, 8), servidor multihilo en escucha constante (ya en PP4, verificado en Fase 9), ventanas nuevas (Fase 8) |
| **DD.2** Criterio técnico en la BD | Esquema en 3FN con herencia por tablas, catálogos normalizados, FK con cascada, índices, CHECK, transacciones, usuario de mínimo privilegio, diagrama ER (Fases 1, 2, 4) |
| **DD.3** Funcionalidad adecuada | Flujo completo login → catálogo → CRUD → comentarios, validación en dos capas, confirmaciones destructivas, manejo de errores sin *stack traces*, pruebas de §9 |

Los niveles **3 (Autónomo)** y **4 (Estratégico)** hablan de *analizar buenas prácticas* y *crear soluciones*, no de que el programa corra. Se evidencian en: el README con decisiones justificadas, la sección de limitaciones, el uso de `PreparedStatement` con la razón explicada, la eliminación argumentada del candado global, y el historial de commits que muestra un proceso ordenado en vez de un único commit gigante llamado "entrega final".

---

## Próximos pasos de aprendizaje

Ordenados por retorno inmediato:

1. **Pool de conexiones (HikariCP).** Cambio pequeño, mejora medible. Te enseña por qué abrir conexiones es caro.
2. **JUnit 5 + Testcontainers.** Probar DAOs contra un MySQL efímero en Docker. Es el salto de "probé a mano" a "el sistema se prueba solo".
3. **JPA / Hibernate.** Ahora que mapeaste la herencia a mano, vas a entender exactamente qué hacen `@Inheritance(strategy = JOINED)` y por qué existe el problema N+1. Aprender el ORM después del SQL crudo, nunca antes.
4. **Flyway o Liquibase.** Versionar el esquema como versionás el código. El día que trabajes en equipo con una base compartida, esto deja de ser opcional.
5. **TLS sobre sockets (`SSLSocket`) y Argon2id.** Cerrar las dos limitaciones de seguridad que dejaste declaradas.
6. **`ExecutorService` en lugar de `new Thread()` por conexión.** Un pool acotado impide que mil conexiones creen mil hilos y tumben la JVM.

---

## Referencias (verificables)

1. **MySQL 8.x Reference Manual** — dev.mysql.com/doc/refman/8.0/en/ (DDL, InnoDB, transacciones, `GRANT`)
2. **OWASP SQL Injection Prevention Cheat Sheet** — cheatsheetseries.owasp.org (consultas parametrizadas)
3. **OWASP Password Storage Cheat Sheet** — cheatsheetseries.owasp.org (por qué Argon2id/bcrypt y no SHA-256)
4. **Oracle Java Tutorials — JDBC Basics** — docs.oracle.com/javase/tutorial/jdbc/ (`PreparedStatement`, transacciones, `getGeneratedKeys`)
5. **CWE-89: SQL Injection** — cwe.mitre.org/data/definitions/89.html

---

## Resumen accionable

- **Empezá por Git**, no por MySQL: el `.gitignore` tiene que existir antes que `db.properties`, o la contraseña queda en el historial para siempre.
- **El orden es**: repo → motor y usuario de mínimo privilegio → esquema → driver → DAOs → migrar usuarios → protocolo → servidor → interfaz → pruebas → entrega. Cada fase termina en un commit y en un estado verificable.
- **Eliminá el `synchronized` global** al pasar a MySQL: el motor ya maneja concurrencia, y mantener el candado convierte tu servidor multihilo en uno secuencial.
- **`PreparedStatement` siempre**, y entendé el porqué (separa estructura de datos antes de que el dato llegue), no solo la sintaxis. Probalo con el caso de inyección de §9.
- **Transacción obligatoria** en toda operación que toque tabla base + tabla hija: sin ella quedan registros huérfanos que ninguna consulta muestra completos.
- **El README con decisiones justificadas y limitaciones declaradas** es lo que mueve la nota de "funciona" (nivel 2) a "analiza y crea" (niveles 3-4).
