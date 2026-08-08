-- =====================================================================
-- Fideflix - Practica Programada 5
-- 01_schema.sql : creacion del esquema
--
-- MOTOR      : MySQL 8.x / InnoDB
-- EJECUTAR   : como 'root' desde MySQL Workbench.
--              El usuario 'fideflix_app' NO puede correr este script y eso
--              es intencional: no tiene privilegios CREATE ni DROP.
--              Separacion de responsabilidades: el DDL (estructura) es
--              tarea de administracion; el DML (datos) es tarea de la
--              aplicacion. Que la app no pueda alterar la estructura es
--              una barrera de seguridad, no una molestia.
--
-- IDEMPOTENTE: se puede volver a ejecutar. Borra las tablas y las recrea.
--
-- ADVERTENCIA: este script DESTRUYE todos los datos existentes en las
-- tablas de fideflix. Es una base de desarrollo. Contra un entorno real
-- jamas se ejecuta un script con DROP TABLE.
-- =====================================================================

USE fideflix;

-- ---------------------------------------------------------------------
-- Limpieza previa.
--
-- EL ORDEN IMPORTA Y NO ES ARBITRARIO: se borra desde las tablas HIJAS
-- hacia las PADRES. Una llave foranea es una promesa ("esta fila apunta
-- a una que existe"); MySQL no permite romperla borrando primero al
-- padre. Es el orden inverso al de creacion.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS comentario;
DROP TABLE IF EXISTS pelicula;
DROP TABLE IF EXISTS documental;
DROP TABLE IF EXISTS serie;
DROP TABLE IF EXISTS audiovisual;
DROP TABLE IF EXISTS usuario;
DROP TABLE IF EXISTS genero;
DROP TABLE IF EXISTS clasificacion;

-- =====================================================================
-- CATALOGOS
--
-- Por que tablas y no columnas VARCHAR: genero y clasificacion son
-- dominios CERRADOS (un conjunto conocido y finito de valores).
-- Como texto libre, tarde o temprano conviven "Ciencia Ficcion",
-- "ciencia ficcion" y "Sci-Fi" en la misma columna, y ningun GROUP BY
-- vuelve a servir. Como llave foranea, la integridad la garantiza el
-- motor y los JComboBox de la interfaz se llenan solos desde la tabla.
-- =====================================================================

CREATE TABLE clasificacion (
    id     TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    -- TINYINT UNSIGNED: 0 a 255. Nunca vamos a tener 255 clasificaciones.
    -- Elegir el tipo mas pequeno que cubra el dominio no es tacaneria:
    -- tipos mas chicos = indices mas chicos = menos lecturas de disco.
    codigo VARCHAR(10) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE genero (
    id     SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- =====================================================================
-- USUARIOS  (reemplaza a usuarios.dat de la PP4)
-- =====================================================================

CREATE TABLE usuario (
    id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(80)  NOT NULL,

    -- UNIQUE sobre email es el equivalente en la base de datos del
    -- Usuario.equals() que ya escribiste en Java (compara por email).
    -- La diferencia es sustancial: alla la unicidad dependia de que un
    -- contains() se ejecutara antes del add(), y dos hilos podian colarse
    -- entre esos dos pasos. Aqui la garantiza el motor de forma atomica.
    email           VARCHAR(120) NOT NULL UNIQUE,

    -- CHAR(64), no VARCHAR: un SHA-256 en hexadecimal mide SIEMPRE 64
    -- caracteres. Con longitud fija, CHAR evita el byte extra de longitud
    -- que lleva VARCHAR y le dice al lector que el dato no varia.
    --
    -- ADVERTENCIA DE SEGURIDAD: aqui va el HASH, jamas la contrasena.
    -- SHA-256 sin salt es el minimo aceptable en un trabajo academico y
    -- NO es aceptable en produccion: es un hash rapido (una GPU prueba
    -- miles de millones por segundo) y sin salt dos usuarios con la misma
    -- clave producen el mismo hash, visible a simple vista en la tabla.
    -- Produccion exige bcrypt, scrypt o Argon2id.
    -- Ver: OWASP Password Storage Cheat Sheet.
    contrasena_hash CHAR(64)     NOT NULL,

    -- DATE, no VARCHAR. Guardar fechas como texto es un error clasico:
    -- impide ordenar cronologicamente, comparar rangos y usar funciones
    -- de fecha. El DAO se encarga de convertir desde/hacia String.
    fecha_registro  DATE         NOT NULL
) ENGINE=InnoDB;

-- =====================================================================
-- JERARQUIA AUDIOVISUAL
--
-- Java tiene herencia; SQL no. Estrategia elegida: CLASS TABLE
-- INHERITANCE (tabla base + una hija por subclase, relacion 1:1).
--
-- Alternativas descartadas y por que:
--  - Tabla unica con columna 'tipo' y campos nulables: una serie tendria
--    'director' y 'estudio' en NULL para siempre. La tabla mentiria sobre
--    su propio dominio.
--  - Tres tablas independientes completas: titulo, genero y calificacion
--    duplicados en tres lugares -> anomalias de actualizacion.
-- =====================================================================

CREATE TABLE audiovisual (
    id                INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    titulo            VARCHAR(150) NOT NULL,
    descripcion       TEXT,
    anio_estreno      SMALLINT UNSIGNED,
    calificacion_imdb DECIMAL(3,1),
    -- DECIMAL, no FLOAT ni DOUBLE. Los flotantes son binarios y no
    -- representan exactamente valores como 8.7: comparar por igualdad
    -- falla de formas dificiles de depurar. DECIMAL(3,1) guarda el numero
    -- tal cual: 3 digitos totales, 1 decimal.

    clasificacion_id  TINYINT UNSIGNED,
    genero_id         SMALLINT UNSIGNED,

    -- DISCRIMINADOR. Estrictamente es redundante: el tipo se podria
    -- deducir con tres LEFT JOIN. Es una DENORMALIZACION CONSCIENTE:
    -- listar el catalogo es la operacion mas frecuente de la aplicacion
    -- y asi se resuelve con un SELECT simple.
    -- Una denormalizacion justificada y documentada suma criterio;
    -- una accidental resta. Esta esta documentada aqui y en el README.
    tipo              ENUM('PELICULA','DOCUMENTAL','SERIE') NOT NULL,

    CONSTRAINT fk_av_clasificacion FOREIGN KEY (clasificacion_id)
        REFERENCES clasificacion(id) ON DELETE SET NULL,
    CONSTRAINT fk_av_genero FOREIGN KEY (genero_id)
        REFERENCES genero(id) ON DELETE SET NULL,
    -- ON DELETE SET NULL: si se borra un genero del catalogo, la obra
    -- sobrevive sin genero. Seria absurdo que borrar "Drama" del catalogo
    -- eliminara todas las peliculas dramaticas.

    -- CHECK: la base tambien valida. La validacion de la interfaz se
    -- puede saltar (un cliente modificado, una peticion cruda al puerto
    -- 5000 con telnet). La del motor no. Defensa en profundidad.
    -- Requiere MySQL 8.0.16 o superior; en versiones previas los CHECK
    -- se parsean pero se ignoran en silencio.
    CONSTRAINT ck_imdb CHECK (calificacion_imdb BETWEEN 0 AND 10),
    CONSTRAINT ck_anio CHECK (anio_estreno BETWEEN 1888 AND 2200),
    -- 1888: "Roundhay Garden Scene", la pelicula mas antigua conservada.

    -- Regla de negocio: no dos obras con el mismo titulo y el mismo anio.
    -- Permite remakes (mismo titulo, anio distinto).
    CONSTRAINT uq_titulo_anio UNIQUE (titulo, anio_estreno)
) ENGINE=InnoDB;

-- Indices sobre las columnas por las que se FILTRA y se ORDENA.
-- Sin ellos, filtrar por tipo obliga a recorrer la tabla completa.
-- Con veinte registros no se nota; el habito de indexar lo que se filtra,
-- si. Un indice acelera las lecturas y encarece las escrituras: por eso
-- se indexa lo que se consulta, no todo.
CREATE INDEX idx_av_tipo   ON audiovisual(tipo);
CREATE INDEX idx_av_titulo ON audiovisual(titulo);

-- ---------------------------------------------------------------------
-- TABLAS HIJAS
--
-- La llave primaria es TAMBIEN la llave foranea. Ese detalle impone la
-- relacion 1:1 estricta: una pelicula no puede tener dos filas hijas,
-- porque la PK no admite duplicados. El modelo se defiende solo.
--
-- ON DELETE CASCADE: borrar el audiovisual borra su fila hija. Sin
-- cascada quedan filas huerfanas: basura que ninguna consulta encuentra
-- pero que ocupa espacio y descuadra los conteos.
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
    audiovisual_id INT UNSIGNED PRIMARY KEY,
    num_temporadas TINYINT UNSIGNED,
    num_episodios  SMALLINT UNSIGNED,
    estado         VARCHAR(30),
    CONSTRAINT fk_serie_av FOREIGN KEY (audiovisual_id)
        REFERENCES audiovisual(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================================
-- COMENTARIOS
--
-- Esta tabla corrige un BUG real de la PP4: en Audiovisual.java la lista
-- 'comentarios' era static, es decir COMPARTIDA por todas las instancias.
-- Un comentario sobre "Interstellar" aparecia tambien en "Breaking Bad".
--
-- El modelo relacional hace imposible ese error: al declarar la llave
-- foranea, el comentario queda atado a UNA obra concreta. La base de
-- datos, al obligarte a declarar la relacion, expone el error que Java
-- te dejo pasar en silencio.
-- =====================================================================

CREATE TABLE comentario (
    id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    audiovisual_id INT UNSIGNED NOT NULL,
    usuario_id     INT UNSIGNED NOT NULL,
    texto          TEXT NOT NULL,
    fecha_hora     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- DEFAULT CURRENT_TIMESTAMP: la marca de tiempo la pone el servidor
    -- de base de datos, no el cliente. El reloj de la maquina del usuario
    -- puede estar mal o ser manipulado; el del servidor es la unica
    -- fuente de tiempo confiable del sistema.

    CONSTRAINT fk_com_av FOREIGN KEY (audiovisual_id)
        REFERENCES audiovisual(id) ON DELETE CASCADE,
    CONSTRAINT fk_com_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_com_av ON comentario(audiovisual_id);
-- Se consulta siempre "los comentarios DE esta obra": ese es el filtro
-- y por eso ese es el indice.
