-- =====================================================================
-- Fideflix - Practica Programada 5
-- 02_datos_iniciales.sql : catalogos y datos de prueba
--
-- EJECUTAR: despues de 01_schema.sql.
-- Como 'root' o como 'fideflix_app' (son INSERT, no DDL: la app si tiene
-- permiso para esto).
--
-- Por que existen datos de prueba: para poder validar el CRUD y las
-- consultas ANTES de escribir una sola linea de Java. Si algo falla mas
-- adelante, ya sabes que el problema no esta en la base.
-- =====================================================================

USE fideflix;

-- ---------------------------------------------------------------------
-- CATALOGOS
-- ---------------------------------------------------------------------

INSERT INTO clasificacion (codigo) VALUES
    ('G'), ('PG'), ('PG-13'), ('R'),
    ('TV-G'), ('TV-14'), ('TV-MA');

INSERT INTO genero (nombre) VALUES
    ('Ciencia Ficcion'), ('Drama'), ('Accion'), ('Comedia'),
    ('Suspenso'), ('Terror'), ('Naturaleza'), ('Historia'),
    ('Biografia'), ('Animacion');

-- ---------------------------------------------------------------------
-- DATOS DE PRUEBA
--
-- PATRON CLAVE: insertar un audiovisual toca DOS tablas (la base y la
-- hija). O entran las dos o no entra ninguna. Sin transaccion, un fallo
-- en el segundo INSERT dejaria una fila en 'audiovisual' sin su hija:
-- un registro a medias que la consulta del catalogo mostraria incompleto
-- y que nunca podrias corregir desde la interfaz.
--
-- LAST_INSERT_ID() devuelve el AUTO_INCREMENT generado por el INSERT
-- anterior EN ESTA MISMA CONEXION. Ese detalle es importante: es seguro
-- aunque otros clientes esten insertando al mismo tiempo, porque el
-- valor es por conexion, no global. En Java el equivalente es
-- Statement.RETURN_GENERATED_KEYS + getGeneratedKeys().
-- ---------------------------------------------------------------------

-- ===== PELICULA 1 =====
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('Interstellar',
        'Un grupo de exploradores viaja a traves de un agujero de gusano en busca de un nuevo hogar para la humanidad.',
        2014, 8.7,
        (SELECT id FROM clasificacion WHERE codigo = 'PG-13'),
        (SELECT id FROM genero WHERE nombre = 'Ciencia Ficcion'),
        'PELICULA');
INSERT INTO pelicula (audiovisual_id, duracion_min, director, estudio)
VALUES (LAST_INSERT_ID(), 169, 'Christopher Nolan', 'Paramount Pictures');
COMMIT;

-- ===== PELICULA 2 =====
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('El Padrino',
        'La cronica de la familia Corleone, una dinastia del crimen organizado en Nueva York.',
        1972, 9.2,
        (SELECT id FROM clasificacion WHERE codigo = 'R'),
        (SELECT id FROM genero WHERE nombre = 'Drama'),
        'PELICULA');
INSERT INTO pelicula (audiovisual_id, duracion_min, director, estudio)
VALUES (LAST_INSERT_ID(), 175, 'Francis Ford Coppola', 'Paramount Pictures');
COMMIT;

-- ===== SERIE 1 =====
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('Breaking Bad',
        'Un profesor de quimica con cancer terminal se asocia con un ex alumno para fabricar y vender metanfetamina.',
        2008, 9.5,
        (SELECT id FROM clasificacion WHERE codigo = 'TV-MA'),
        (SELECT id FROM genero WHERE nombre = 'Drama'),
        'SERIE');
INSERT INTO serie (audiovisual_id, num_temporadas, num_episodios, estado)
VALUES (LAST_INSERT_ID(), 5, 62, 'Finalizada');
COMMIT;

-- ===== SERIE 2 =====
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('Stranger Things',
        'La desaparicion de un nino destapa un experimento secreto y fuerzas sobrenaturales en un pueblo de Indiana.',
        2016, 8.6,
        (SELECT id FROM clasificacion WHERE codigo = 'TV-14'),
        (SELECT id FROM genero WHERE nombre = 'Ciencia Ficcion'),
        'SERIE');
INSERT INTO serie (audiovisual_id, num_temporadas, num_episodios, estado)
VALUES (LAST_INSERT_ID(), 4, 34, 'En emision');
COMMIT;

-- ===== DOCUMENTAL 1 =====
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('Planet Earth',
        'Recorrido por los ecosistemas del planeta y la vida silvestre que los habita.',
        2006, 9.4,
        (SELECT id FROM clasificacion WHERE codigo = 'TV-G'),
        (SELECT id FROM genero WHERE nombre = 'Naturaleza'),
        'DOCUMENTAL');
INSERT INTO documental (audiovisual_id, duracion_min, director, tema)
VALUES (LAST_INSERT_ID(), 550, 'Alastair Fothergill', 'Naturaleza y biodiversidad');
COMMIT;

-- ===== DOCUMENTAL 2 =====
START TRANSACTION;
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES ('The Social Dilemma',
        'Ex ejecutivos de empresas tecnologicas exponen el impacto de las redes sociales en la sociedad.',
        2020, 7.6,
        (SELECT id FROM clasificacion WHERE codigo = 'PG-13'),
        (SELECT id FROM genero WHERE nombre = 'Historia'),
        'DOCUMENTAL');
INSERT INTO documental (audiovisual_id, duracion_min, director, tema)
VALUES (LAST_INSERT_ID(), 94, 'Jeff Orlowski', 'Tecnologia y sociedad');
COMMIT;

-- ---------------------------------------------------------------------
-- USUARIO DE PRUEBA
--
-- El hash corresponde a la contrasena literal: admin123
-- Se genero con SHA2('admin123', 256), la MISMA funcion que replicara
-- Java con MessageDigest. Que ambos lados produzcan el mismo hash es
-- justamente lo que permite que el login funcione.
--
-- ADVERTENCIA: usuario de prueba con contrasena trivial. Existe solo
-- para validar el login sin depender de la interfaz. Borralo antes de
-- entregar, o al menos no lo presentes como ejemplo de buena practica.
-- ---------------------------------------------------------------------
INSERT INTO usuario (nombre, email, contrasena_hash, fecha_registro)
VALUES ('Usuario Prueba', 'prueba@fideflix.com',
        SHA2('admin123', 256), CURDATE());

-- Comentario de prueba, atado a UNA obra concreta.
INSERT INTO comentario (audiovisual_id, usuario_id, texto)
VALUES ((SELECT id FROM audiovisual WHERE titulo = 'Interstellar'),
        (SELECT id FROM usuario WHERE email = 'prueba@fideflix.com'),
        'La banda sonora de Hans Zimmer sostiene toda la pelicula.');
