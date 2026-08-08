-- =====================================================================
-- Fideflix - Practica Programada 5
-- 03_verificacion.sql : pruebas del esquema
--
-- EJECUTAR: sentencia por sentencia (Ctrl+Enter en Workbench sobre cada
-- bloque), leyendo el resultado de cada una. Ejecutarlo entero de un
-- golpe no sirve: el objetivo es COMPARAR lo obtenido contra lo esperado.
--
-- Una prueba que no leiste no es una prueba.
-- =====================================================================

USE fideflix;

-- ---------------------------------------------------------------------
-- 1. Estructura: deben existir 8 tablas
-- ---------------------------------------------------------------------
SHOW TABLES;
-- ESPERADO: audiovisual, clasificacion, comentario, documental,
--           genero, pelicula, serie, usuario


-- ---------------------------------------------------------------------
-- 2. Consulta del catalogo (la que usara AudiovisualDAO en la Fase 4)
--
-- LEFT JOIN a las tres hijas: para cada fila solo UNA traera datos, las
-- otras dos vienen en NULL. COALESCE toma el primer valor no nulo, que
-- es como unificamos 'duracion' y 'director' entre peliculas y
-- documentales en una sola columna de salida.
-- ---------------------------------------------------------------------
SELECT a.id, a.tipo, a.titulo, a.anio_estreno, a.calificacion_imdb,
       c.codigo AS clasificacion, g.nombre AS genero,
       COALESCE(p.duracion_min, d.duracion_min) AS duracion,
       COALESCE(p.director,     d.director)     AS director,
       p.estudio, d.tema,
       s.num_temporadas, s.num_episodios, s.estado
FROM audiovisual a
LEFT JOIN clasificacion c ON c.id = a.clasificacion_id
LEFT JOIN genero        g ON g.id = a.genero_id
LEFT JOIN pelicula      p ON p.audiovisual_id = a.id
LEFT JOIN documental    d ON d.audiovisual_id = a.id
LEFT JOIN serie         s ON s.audiovisual_id = a.id
ORDER BY a.titulo;
-- ESPERADO: 6 filas, cada una con los campos de SU tipo llenos
--           y los de los otros dos tipos en NULL.


-- ---------------------------------------------------------------------
-- 3. Integridad referencial: no debe haber huerfanos
--
-- Un huerfano seria una fila en 'audiovisual' sin su fila hija.
-- Si esta consulta devuelve algo, alguna transaccion quedo a medias.
-- ---------------------------------------------------------------------
SELECT a.id, a.titulo, a.tipo
FROM audiovisual a
LEFT JOIN pelicula   p ON p.audiovisual_id = a.id
LEFT JOIN documental d ON d.audiovisual_id = a.id
LEFT JOIN serie      s ON s.audiovisual_id = a.id
WHERE p.audiovisual_id IS NULL
  AND d.audiovisual_id IS NULL
  AND s.audiovisual_id IS NULL;
-- ESPERADO: 0 filas.


-- =====================================================================
-- PRUEBAS NEGATIVAS
--
-- Aqui el EXITO es que la sentencia FALLE. Estamos comprobando que las
-- restricciones realmente se aplican, no que existan en el papel.
-- Una restriccion que nunca viste rechazar algo es una hipotesis, no
-- una garantia.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 4. CHECK de calificacion: IMDb fuera del rango 0-10
-- ---------------------------------------------------------------------
INSERT INTO audiovisual (titulo, anio_estreno, calificacion_imdb, tipo)
VALUES ('Prueba CHECK', 2020, 15.0, 'PELICULA');
-- ESPERADO: Error 3819 "Check constraint 'ck_imdb' is violated".
-- Si INSERTA, tu MySQL es anterior a 8.0.16 y los CHECK se ignoran.
-- Comprobalo con:  SELECT VERSION();


-- ---------------------------------------------------------------------
-- 5. UNIQUE de email en usuario
-- ---------------------------------------------------------------------
INSERT INTO usuario (nombre, email, contrasena_hash, fecha_registro)
VALUES ('Duplicado', 'prueba@fideflix.com', SHA2('otra', 256), CURDATE());
-- ESPERADO: Error 1062 "Duplicate entry ... for key 'usuario.email'".
-- Este error es el que HiloCliente traducira a la respuesta DUPLICADO
-- del protocolo en la Fase 5.


-- ---------------------------------------------------------------------
-- 6. Llave foranea: genero inexistente
-- ---------------------------------------------------------------------
INSERT INTO audiovisual (titulo, anio_estreno, genero_id, tipo)
VALUES ('Prueba FK', 2020, 9999, 'PELICULA');
-- ESPERADO: Error 1452, falla la restriccion fk_av_genero.


-- =====================================================================
-- PRUEBA DE CASCADA
--
-- Borrar un audiovisual debe arrastrar su fila hija y sus comentarios.
-- Contamos antes, borramos, contamos despues.
-- =====================================================================

-- 7a. Conteo previo
SELECT (SELECT COUNT(*) FROM audiovisual) AS audiovisuales,
       (SELECT COUNT(*) FROM pelicula)    AS peliculas,
       (SELECT COUNT(*) FROM comentario)  AS comentarios;
-- ESPERADO: 6 / 2 / 1

-- 7b. Borrado del audiovisual que tiene comentario asociado
DELETE FROM audiovisual WHERE titulo = 'Interstellar';

-- 7c. Conteo posterior
SELECT (SELECT COUNT(*) FROM audiovisual) AS audiovisuales,
       (SELECT COUNT(*) FROM pelicula)    AS peliculas,
       (SELECT COUNT(*) FROM comentario)  AS comentarios;
-- ESPERADO: 5 / 1 / 0
-- Un solo DELETE limpio tres tablas. Eso es ON DELETE CASCADE: la
-- integridad la mantiene el motor, no un metodo de Java que alguien
-- podria olvidarse de llamar.


-- ---------------------------------------------------------------------
-- 8. Restaurar el estado de prueba
-- ---------------------------------------------------------------------
-- Volve a ejecutar 01_schema.sql y 02_datos_iniciales.sql para dejar
-- la base limpia antes de empezar la Fase 3.
