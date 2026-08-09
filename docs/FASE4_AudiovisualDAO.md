# Fase 4 — `AudiovisualDAO`: diseño y justificación

> **Estado: implementado.** Este documento dejó de ser una guía para escribir la clase y pasó a ser su documentación de diseño. Sirve para dos cosas: entender *por qué* el código está hecho así, y tener a mano los argumentos para la defensa. Cada decisión de acá está también comentada en el `.java`.


Esta es la clase central de la práctica. Concentra los tres conceptos que la rúbrica evalúa en DD.2 y que un profesor va a preguntar en la defensa:

1. **Transacciones** — insertar una obra toca dos tablas.
2. **`LAST_INSERT_ID` / `getGeneratedKeys`** — la hija necesita el id que generó la base.
3. **Mapeo de herencia** — una consulta plana debe volver a ser `Pelicula`, `Documental` o `Serie`.

Ubicación: `Fideflix/src/fideflix/persistencia/AudiovisualDAO.java`

---

## 1. Contrato público

```java
public final class AudiovisualDAO {

    private AudiovisualDAO() { }

    /** @param tipo "PELICULA" | "DOCUMENTAL" | "SERIE", o null/"TODOS" para todos. */
    public static List<Audiovisual> listar(String tipo) throws SQLException;

    /** @return la obra, o null si el id no existe. */
    public static Audiovisual obtener(int id) throws SQLException;

    /** Inserta base + hija en UNA transacción. Devuelve el objeto con su id asignado. */
    public static Audiovisual insertar(Audiovisual av) throws SQLException;

    /** Actualiza base + hija en UNA transacción. @return false si el id no existía. */
    public static boolean actualizar(Audiovisual av) throws SQLException;

    /** @return false si el id no existía. La cascada borra hija y comentarios. */
    public static boolean eliminar(int id) throws SQLException;
}
```

**Por qué `listar` recibe el tipo como `String` y no un `enum`.** Un `enum` sería más seguro en Java, pero el valor llega desde el protocolo de red como texto. Convertirlo a `enum` para volver a convertirlo a texto en el SQL agrega una capa sin beneficio real en este alcance. Es una decisión discutible — si preferís el `enum`, defendela y adelante.

---

## 2. Las consultas

### 2.1 Lectura (`listar` y `obtener`)

```sql
SELECT a.id, a.titulo, a.descripcion, a.anio_estreno, a.calificacion_imdb, a.tipo,
       c.codigo AS clasificacion, g.nombre AS genero,
       p.duracion_min AS p_duracion, p.director AS p_director, p.estudio,
       d.duracion_min AS d_duracion, d.director AS d_director, d.tema,
       s.num_temporadas, s.num_episodios, s.estado
FROM audiovisual a
LEFT JOIN clasificacion c ON c.id = a.clasificacion_id
LEFT JOIN genero        g ON g.id = a.genero_id
LEFT JOIN pelicula      p ON p.audiovisual_id = a.id
LEFT JOIN documental    d ON d.audiovisual_id = a.id
LEFT JOIN serie         s ON s.audiovisual_id = a.id
```

- Para `listar` sin filtro: agregá `ORDER BY a.titulo`.
- Para `listar` con filtro: `WHERE a.tipo = ?` antes del `ORDER BY`.
- Para `obtener`: `WHERE a.id = ?`.

**Por qué `LEFT JOIN` y no `JOIN`.** Un `JOIN` normal solo devuelve filas que existen en *ambas* tablas. Como una película no tiene fila en `serie`, un `JOIN` la eliminaría del resultado y terminarías con cero filas. `LEFT JOIN` conserva la fila de la izquierda y rellena con `NULL` lo que no encuentra. De las tres hijas, exactamente una traerá datos y dos vendrán en `NULL`.

**Por qué los alias `p_duracion` / `d_duracion`.** `pelicula` y `documental` tienen columnas con el mismo nombre (`duracion_min`, `director`). Sin alias, `rs.getInt("duracion_min")` es ambiguo y JDBC devuelve la primera que encuentre — un bug silencioso. Los alias eliminan la ambigüedad.

> Alternativa: `COALESCE(p.duracion_min, d.duracion_min) AS duracion`, que unifica en una sola columna. Es más corto y funciona igual de bien acá. Elegí una y sé consistente.

### 2.2 Escritura

```sql
-- Tabla base
INSERT INTO audiovisual (titulo, descripcion, anio_estreno, calificacion_imdb,
                         clasificacion_id, genero_id, tipo)
VALUES (?, ?, ?, ?, ?, ?, ?)

-- Hijas (una según el tipo)
INSERT INTO pelicula   (audiovisual_id, duracion_min, director, estudio) VALUES (?, ?, ?, ?)
INSERT INTO documental (audiovisual_id, duracion_min, director, tema)    VALUES (?, ?, ?, ?)
INSERT INTO serie      (audiovisual_id, num_temporadas, num_episodios, estado) VALUES (?, ?, ?, ?)

-- Actualización
UPDATE audiovisual SET titulo=?, descripcion=?, anio_estreno=?, calificacion_imdb=?,
                       clasificacion_id=?, genero_id=? WHERE id=?
UPDATE pelicula   SET duracion_min=?, director=?, estudio=? WHERE audiovisual_id=?
UPDATE documental SET duracion_min=?, director=?, tema=?    WHERE audiovisual_id=?
UPDATE serie      SET num_temporadas=?, num_episodios=?, estado=? WHERE audiovisual_id=?

-- Borrado (solo la base: la cascada hace el resto)
DELETE FROM audiovisual WHERE id=?
```

Fijate que el `UPDATE` de la base **no toca `tipo`**. Ver §6.

---

## 3. `insertar()` — el método clave

### La lógica, en orden

1. Abrir `Connection` con `ConexionBD.obtener()`.
2. `con.setAutoCommit(false)` — **acá empieza la transacción.**
3. Resolver los ids de catálogo con `CatalogoDAO.idDeGenero(con, ...)` e `idDeClasificacion(con, ...)`, pasándoles **esta misma conexión**.
4. Determinar el tipo con `instanceof` sobre el objeto recibido.
5. `INSERT` en `audiovisual` con `Statement.RETURN_GENERATED_KEYS`.
6. Leer el id generado con `ps.getGeneratedKeys()`.
7. `INSERT` en la tabla hija que corresponda, usando ese id.
8. `con.commit()`.
9. En el `catch`: `con.rollback()` y re-lanzar.
10. En el `finally`: `con.setAutoCommit(true)` y cerrar.

### Esqueleto

```java
Connection con = null;
try {
    con = ConexionBD.obtener();
    con.setAutoCommit(false);      // 1. arranca la transaccion

    // 2. ... INSERT base + getGeneratedKeys ...
    // 3. ... INSERT hija con ese id ...

    con.commit();                  // 4. todo o nada
    return av;

} catch (SQLException e) {
    if (con != null) con.rollback();   // 5. deshacer TODO lo del try
    throw e;                           // el llamador debe enterarse
} finally {
    if (con != null) {
        con.setAutoCommit(true);
        con.close();
    }
}
```

### Por qué acá no se usa try-with-resources

En el resto de los DAO sí se usa, y es lo correcto. Acá no, porque necesitás la referencia a `con` **dentro del `catch`** para hacer `rollback()`, y una variable declarada en el `try-with-resources` no es visible ahí. Es la excepción que confirma la regla: el patrón se elige por lo que el código necesita, no por costumbre.

### El error clásico

Hacer `rollback()` sin haber hecho `setAutoCommit(false)`. En modo autocommit **cada sentencia se confirma sola**, así que cuando llegás al `rollback` ya no hay nada que deshacer y falla en silencio. La transacción empieza cuando desactivás autocommit, no cuando pensás en ella.

### Por qué esto importa de verdad

Sin transacción, si el segundo `INSERT` falla te queda una fila en `audiovisual` **sin fila hija**. La consulta del catálogo la mostraría con todos los campos específicos vacíos, y no habría forma de arreglarla desde la interfaz. Es exactamente el registro huérfano que detecta el bloque 3 de `03_verificacion.sql`.

---

## 4. `actualizar()`

Misma estructura de transacción. Dos diferencias:

- Se usan `UPDATE`, no `INSERT`.
- Para saber si el id existía, revisá lo que devuelve `executeUpdate()` sobre la tabla base: es la cantidad de filas afectadas. Si da `0`, el id no existe → `rollback()` y `return false`.

Ese detalle es lo que permite responder `NO_ENCONTRADO` en el protocolo en vez de un `OK` mentiroso.

---

## 5. El mapeo: de `ResultSet` a objetos

Un método privado que recibe el `ResultSet` posicionado en una fila y devuelve el `Audiovisual` concreto:

```java
private static Audiovisual mapear(ResultSet rs) throws SQLException {
    String tipo = rs.getString("tipo");
    Audiovisual av = switch (tipo) {
        case "PELICULA"   -> new Pelicula(...);
        case "DOCUMENTAL" -> new Documental(...);
        case "SERIE"      -> new Serie(...);
        default -> throw new SQLException("Tipo desconocido: " + tipo);
    };
    av.setId(rs.getInt("id"));
    return av;
}
```

**Acá se cierra el círculo del discriminador `tipo`.** Cuando justificamos esa columna como denormalización consciente, el argumento fue "listar el catálogo es la operación más frecuente". Este método es la prueba: sin `tipo` habría que averiguar la clase preguntando cuál de las tres hijas trajo datos no nulos. Con `tipo`, un `switch` directo.

**Cuidado con los nulos y los tipos primitivos.** `rs.getInt()` devuelve `0` cuando la columna es `NULL`, sin avisar. Para `anio_estreno` o `duracion_min` eso puede pasar como un dato válido. Si querés distinguir "no tiene valor" de "vale cero", consultá `rs.wasNull()` inmediatamente después de leer, o usá `rs.getObject(col, Integer.class)`.

**Para `setInt` con valores nulos** (por ejemplo, `genero_id` cuando el género no existe en el catálogo): `ps.setInt(5, null)` no compila. Usá:

```java
if (generoId == null) ps.setNull(5, java.sql.Types.INTEGER);
else                  ps.setInt(5, generoId);
// o directamente: ps.setObject(5, generoId, java.sql.Types.INTEGER);
```

---

## 6. Decisión de alcance: no se cambia el tipo al editar

Convertir una película en serie exigiría borrar la fila de `pelicula` e insertar una en `serie` dentro de la misma transacción. Es posible, pero agrega un camino de código que casi nunca se usa y que puede fallar de formas sutiles.

**Decisión: el tipo se elige al crear y no se modifica después.** En la Fase 8, el combo de tipo queda deshabilitado en modo edición. Declaralo en el README como limitación consciente. Marcar un límite y justificarlo demuestra más criterio que implementarlo a medias.

---

## 7. Validación

Escribí un `PruebaDAO.java` con `main` (mismo espíritu que `PruebaConexion`) que recorra:

1. `listar(null)` → 6 obras.
2. `listar("SERIE")` → 2.
3. `insertar(...)` una película nueva → verificá en Workbench que hay fila en `audiovisual` **y** en `pelicula`.
4. `obtener(idNuevo)` → devuelve un objeto que es `instanceof Pelicula`.
5. `actualizar(...)` cambiando el título → confirmá el cambio en la base.
6. `eliminar(idNuevo)` → verificá que desapareció de las dos tablas.
7. `obtener(99999)` → `null`, sin excepción.

### La prueba que no podés saltarte

Provocá un fallo **a propósito** en el segundo `INSERT` (por ejemplo, mandá `duracion_min = 99999`, que desborda `SMALLINT UNSIGNED`) y verificá en Workbench que **no quedó fila en `audiovisual`**.

Si quedó, tu transacción no está funcionando, y lo vas a descubrir ahora en vez de durante la defensa.

### Prueba de inyección

Insertá una película con título:

```
Prueba'); DROP TABLE comentario; --
```

Se tiene que guardar como **texto literal** y `comentario` debe seguir existiendo. Si desapareciera, hay concatenación en algún lado.

---

## 8. Cuando termines

Pasame el archivo y lo reviso contra esta especificación: transacción bien cerrada, `PreparedStatement` en todos los accesos, manejo de nulos y cierre de recursos.

Después de eso viene la Fase 5, que es corta: reemplazar `PersistenciaUsuarios` por `UsuarioDAO` en `HiloCliente` y eliminar el candado global.
