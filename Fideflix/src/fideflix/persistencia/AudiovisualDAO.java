package fideflix.persistencia;

import fideflix.logica.Audiovisual;
import fideflix.logica.Documental;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/*
 * ═══════════════════════════════════════════════════════════════════
 * DAO DE LA JERARQUIA AUDIOVISUAL
 * ═══════════════════════════════════════════════════════════════════
 *
 * Es la clase central de la PP5. Concentra tres problemas que no
 * aparecen en un CRUD de una sola tabla:
 *
 *  1. TRANSACCIONES: insertar una obra escribe en DOS tablas
 *     (audiovisual + la hija correspondiente). O entran las dos o no
 *     entra ninguna.
 *
 *  2. LLAVES GENERADAS: la tabla hija necesita el id que recien acaba
 *     de generar el AUTO_INCREMENT de la tabla base.
 *
 *  3. MAPEO DE HERENCIA: SQL devuelve filas planas; el programa espera
 *     objetos Pelicula, Documental o Serie. La traduccion en los dos
 *     sentidos ocurre aqui y en ningun otro lado.
 *
 * REGLA DE LA CAPA: fuera del paquete fideflix.persistencia no hay ni
 * una linea de SQL. Si manana cambia una tabla, se toca un archivo.
 */
public final class AudiovisualDAO {

    private AudiovisualDAO() {
    }

    // ═════════════════════════════════════════════════════════════════
    // CONSULTAS
    // ═════════════════════════════════════════════════════════════════

    /*
     * SELECT base con LEFT JOIN a las tres hijas.
     *
     * Por que LEFT JOIN y no JOIN: un JOIN normal solo devuelve filas
     * presentes en AMBAS tablas. Como una pelicula no tiene fila en
     * 'serie', un JOIN la eliminaria del resultado y terminariamos con
     * cero filas. LEFT JOIN conserva la fila de la izquierda y rellena
     * con NULL lo que no encuentra: de las tres hijas, exactamente una
     * trae datos y dos vienen nulas.
     *
     * Por que los alias p_duracion / d_duracion: 'pelicula' y
     * 'documental' tienen columnas con el MISMO nombre (duracion_min,
     * director). Sin alias, rs.getInt("duracion_min") es ambiguo y JDBC
     * devuelve la primera que encuentra: un bug silencioso que aparece
     * solo con ciertos datos. Los alias lo eliminan de raiz.
     */
    private static final String SQL_SELECT_BASE =
            "SELECT a.id, a.titulo, a.descripcion, a.anio_estreno, "
          + "       a.calificacion_imdb, a.tipo, "
          + "       c.codigo AS clasificacion, g.nombre AS genero, "
          + "       p.duracion_min AS p_duracion, p.director AS p_director, p.estudio, "
          + "       d.duracion_min AS d_duracion, d.director AS d_director, d.tema, "
          + "       s.num_temporadas, s.num_episodios, s.estado "
          + "FROM audiovisual a "
          + "LEFT JOIN clasificacion c ON c.id = a.clasificacion_id "
          + "LEFT JOIN genero        g ON g.id = a.genero_id "
          + "LEFT JOIN pelicula      p ON p.audiovisual_id = a.id "
          + "LEFT JOIN documental    d ON d.audiovisual_id = a.id "
          + "LEFT JOIN serie         s ON s.audiovisual_id = a.id ";

    private static final String SQL_LISTAR_TODOS =
            SQL_SELECT_BASE + "ORDER BY a.titulo";

    private static final String SQL_LISTAR_POR_TIPO =
            SQL_SELECT_BASE + "WHERE a.tipo = ? ORDER BY a.titulo";

    private static final String SQL_OBTENER =
            SQL_SELECT_BASE + "WHERE a.id = ?";

    private static final String SQL_INSERT_BASE =
            "INSERT INTO audiovisual (titulo, descripcion, anio_estreno, "
          + "calificacion_imdb, clasificacion_id, genero_id, tipo) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_INSERT_PELICULA =
            "INSERT INTO pelicula (audiovisual_id, duracion_min, director, estudio) "
          + "VALUES (?, ?, ?, ?)";

    private static final String SQL_INSERT_DOCUMENTAL =
            "INSERT INTO documental (audiovisual_id, duracion_min, director, tema) "
          + "VALUES (?, ?, ?, ?)";

    private static final String SQL_INSERT_SERIE =
            "INSERT INTO serie (audiovisual_id, num_temporadas, num_episodios, estado) "
          + "VALUES (?, ?, ?, ?)";

    /* El UPDATE de la base NO toca la columna 'tipo': el tipo se elige
     * al crear y no se modifica despues. Cambiar una pelicula por una
     * serie exigiria borrar la fila hija e insertar otra dentro de la
     * misma transaccion; es posible, pero agrega un camino de codigo
     * que casi nunca se usa y que falla de formas sutiles. Limitacion
     * consciente, declarada en el README. */
    private static final String SQL_UPDATE_BASE =
            "UPDATE audiovisual SET titulo = ?, descripcion = ?, anio_estreno = ?, "
          + "calificacion_imdb = ?, clasificacion_id = ?, genero_id = ? "
          + "WHERE id = ?";

    private static final String SQL_UPDATE_PELICULA =
            "UPDATE pelicula SET duracion_min = ?, director = ?, estudio = ? "
          + "WHERE audiovisual_id = ?";

    private static final String SQL_UPDATE_DOCUMENTAL =
            "UPDATE documental SET duracion_min = ?, director = ?, tema = ? "
          + "WHERE audiovisual_id = ?";

    private static final String SQL_UPDATE_SERIE =
            "UPDATE serie SET num_temporadas = ?, num_episodios = ?, estado = ? "
          + "WHERE audiovisual_id = ?";

    /* Solo se borra la fila base: ON DELETE CASCADE se encarga de la
     * hija y de los comentarios. Un unico DELETE limpia tres tablas, y
     * esa garantia la sostiene el motor, no un metodo de Java que
     * alguien podria olvidarse de llamar. */
    private static final String SQL_ELIMINAR =
            "DELETE FROM audiovisual WHERE id = ?";

    // ═════════════════════════════════════════════════════════════════
    // LECTURA
    // ═════════════════════════════════════════════════════════════════

    /*
     * Lista el catalogo, opcionalmente filtrado por tipo.
     *
     * @param tipo "PELICULA", "DOCUMENTAL", "SERIE", o null / "TODOS"
     *             para traer todo.
     * @return lista posiblemente VACIA, nunca null. Es una convencion
     *         que conviene sostener siempre: quien llama puede recorrer
     *         con un for sin preguntar nada, y desaparece toda una
     *         clase de NullPointerException.
     */
    public static List<Audiovisual> listar(String tipo) throws SQLException {

        boolean sinFiltro = (tipo == null || tipo.isBlank() || "TODOS".equalsIgnoreCase(tipo));
        String sql = sinFiltro ? SQL_LISTAR_TODOS : SQL_LISTAR_POR_TIPO;

        List<Audiovisual> lista = new ArrayList<>();

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (!sinFiltro) {
                // El tipo llega desde la red. Aunque sea uno de tres
                // valores esperados, entra como PARAMETRO, no concatenado.
                ps.setString(1, tipo.toUpperCase());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /*
     * Busca una obra por su id.
     * @return el objeto concreto, o null si el id no existe.
     */
    public static Audiovisual obtener(int id) throws SQLException {

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_OBTENER)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // INSERCION  (el metodo clave de la practica)
    // ═════════════════════════════════════════════════════════════════

    /*
     * Inserta una obra: fila en 'audiovisual' + fila en la tabla hija,
     * dentro de UNA SOLA TRANSACCION.
     *
     * ─── POR QUE UNA TRANSACCION ────────────────────────────────────
     * Son dos INSERT. Sin transaccion, si el segundo falla queda una
     * fila en 'audiovisual' SIN su fila hija: un registro huerfano que
     * la consulta del catalogo mostraria con todos los campos
     * especificos vacios y que no habria forma de reparar desde la
     * interfaz. Es exactamente lo que detecta el bloque 3 de
     * 03_verificacion.sql.
     *
     * ─── POR QUE NO SE USA try-with-resources AQUI ──────────────────
     * En el resto de los DAO si se usa, y es lo correcto. Aqui no,
     * porque hace falta la referencia a 'con' DENTRO del catch para
     * poder llamar a rollback(), y una variable declarada en el
     * try-with-resources no es visible ahi. El patron se elige por lo
     * que el codigo necesita, no por costumbre.
     *
     * @return el mismo objeto, ya con su id asignado por la base.
     */
    public static Audiovisual insertar(Audiovisual av) throws SQLException {

        Connection con = null;
        try {
            con = ConexionBD.obtener();

            // ── INICIO DE LA TRANSACCION ──────────────────────────────
            // ERROR CLASICO: llamar a rollback() sin haber hecho esto.
            // En modo autocommit cada sentencia se confirma sola, y el
            // rollback no tendria nada que deshacer. La transaccion
            // empieza aqui, no cuando uno piensa en ella.
            con.setAutoCommit(false);

            // Resolucion de catalogos SOBRE LA MISMA CONEXION: una
            // conexion distinta seria una transaccion distinta y no
            // veria los cambios en curso.
            Integer generoId = CatalogoDAO.idDeGenero(con, av.getGenero());
            Integer clasifId = CatalogoDAO.idDeClasificacion(con, av.getClasificacion());

            String tipo = tipoDe(av);
            int idGenerado;

            // ── 1. INSERT en la tabla base ────────────────────────────
            try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_BASE,
                    Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, av.getTitulo());
                ps.setString(2, av.getDescripcion());
                setEnteroOpcional(ps, 3, av.getEstreno());
                ps.setDouble(4, av.getCalificacion_IMDb());
                setIdOpcional(ps, 5, clasifId);
                setIdOpcional(ps, 6, generoId);
                ps.setString(7, tipo);

                ps.executeUpdate();

                // ── 2. Recuperar el AUTO_INCREMENT ────────────────────
                // Equivale al LAST_INSERT_ID() de SQL. El valor es POR
                // CONEXION, no global: es seguro aunque otros clientes
                // esten insertando en el mismo instante.
                try (ResultSet claves = ps.getGeneratedKeys()) {
                    if (!claves.next()) {
                        throw new SQLException(
                                "La base no devolvio el id generado para el audiovisual.");
                    }
                    idGenerado = claves.getInt(1);
                }
            }

            // ── 3. INSERT en la tabla hija ────────────────────────────
            insertarHija(con, av, idGenerado);

            // ── 4. CONFIRMAR: todo o nada ─────────────────────────────
            con.commit();

            av.setId(idGenerado);
            return av;

        } catch (SQLException e) {
            // ── 5. DESHACER TODO lo hecho dentro del try ──────────────
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    // Si hasta el rollback falla, se adjunta como causa
                    // suprimida para no perder el error ORIGINAL, que es
                    // el que explica que paso realmente.
                    e.addSuppressed(ex);
                }
            }
            throw e;

        } finally {
            if (con != null) {
                // Restaurar el modo por defecto antes de devolver la
                // conexion, y cerrarla pase lo que pase.
                try {
                    con.setAutoCommit(true);
                } finally {
                    con.close();
                }
            }
        }
    }

    /*
     * Inserta la fila hija segun el tipo concreto del objeto.
     * Recibe la conexion de la transaccion en curso: NO abre la suya.
     */
    private static void insertarHija(Connection con, Audiovisual av, int id)
            throws SQLException {

        // switch sobre patrones de tipo (Java 21+). Es mas seguro que
        // una cadena de if/instanceof con casts manuales: el compilador
        // liga la variable ya convertida y no hay riesgo de castear mal.
        switch (av) {
            case Pelicula p -> {
                try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_PELICULA)) {
                    ps.setInt(1, id);
                    setEnteroOpcional(ps, 2, p.getDuracion());
                    ps.setString(3, p.getDirector());
                    ps.setString(4, p.getEstudio());
                    ps.executeUpdate();
                }
            }
            case Documental d -> {
                try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_DOCUMENTAL)) {
                    ps.setInt(1, id);
                    setEnteroOpcional(ps, 2, d.getDuracion());
                    ps.setString(3, d.getDirector());
                    ps.setString(4, d.getTema());
                    ps.executeUpdate();
                }
            }
            case Serie s -> {
                try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_SERIE)) {
                    ps.setInt(1, id);
                    setEnteroOpcional(ps, 2, s.getNumTemporadas());
                    setEnteroOpcional(ps, 3, s.getNumEpisodios());
                    ps.setString(4, s.getEstado());
                    ps.executeUpdate();
                }
            }
            default -> throw new SQLException(
                    "Subtipo de Audiovisual no soportado: " + av.getClass().getName());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // ACTUALIZACION
    // ═════════════════════════════════════════════════════════════════

    /*
     * Actualiza base + hija en una transaccion.
     *
     * @return false si el id no existia (nada que actualizar).
     *
     * Como se detecta: executeUpdate() devuelve la CANTIDAD DE FILAS
     * AFECTADAS. Si sobre la tabla base da 0, el id no existe. Usar ese
     * dato en vez de asumir el exito es lo que permite responder
     * NO_ENCONTRADO en el protocolo en lugar de un OK mentiroso.
     */
    public static boolean actualizar(Audiovisual av) throws SQLException {

        Connection con = null;
        try {
            con = ConexionBD.obtener();
            con.setAutoCommit(false);

            Integer generoId = CatalogoDAO.idDeGenero(con, av.getGenero());
            Integer clasifId = CatalogoDAO.idDeClasificacion(con, av.getClasificacion());

            int filas;
            try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_BASE)) {
                ps.setString(1, av.getTitulo());
                ps.setString(2, av.getDescripcion());
                setEnteroOpcional(ps, 3, av.getEstreno());
                ps.setDouble(4, av.getCalificacion_IMDb());
                setIdOpcional(ps, 5, clasifId);
                setIdOpcional(ps, 6, generoId);
                ps.setInt(7, av.getId());
                filas = ps.executeUpdate();
            }

            if (filas == 0) {
                // El id no existe: se deshace y se informa. No se lanza
                // excepcion porque "no encontrado" es un resultado
                // legitimo del negocio, no un fallo del sistema.
                con.rollback();
                return false;
            }

            actualizarHija(con, av);
            con.commit();
            return true;

        } catch (SQLException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
            }
            throw e;

        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                } finally {
                    con.close();
                }
            }
        }
    }

    private static void actualizarHija(Connection con, Audiovisual av) throws SQLException {

        switch (av) {
            case Pelicula p -> {
                try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_PELICULA)) {
                    setEnteroOpcional(ps, 1, p.getDuracion());
                    ps.setString(2, p.getDirector());
                    ps.setString(3, p.getEstudio());
                    ps.setInt(4, p.getId());
                    ps.executeUpdate();
                }
            }
            case Documental d -> {
                try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_DOCUMENTAL)) {
                    setEnteroOpcional(ps, 1, d.getDuracion());
                    ps.setString(2, d.getDirector());
                    ps.setString(3, d.getTema());
                    ps.setInt(4, d.getId());
                    ps.executeUpdate();
                }
            }
            case Serie s -> {
                try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_SERIE)) {
                    setEnteroOpcional(ps, 1, s.getNumTemporadas());
                    setEnteroOpcional(ps, 2, s.getNumEpisodios());
                    ps.setString(3, s.getEstado());
                    ps.setInt(4, s.getId());
                    ps.executeUpdate();
                }
            }
            default -> throw new SQLException(
                    "Subtipo de Audiovisual no soportado: " + av.getClass().getName());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // BORRADO
    // ═════════════════════════════════════════════════════════════════

    /*
     * Elimina una obra. La fila hija y los comentarios se van solos por
     * ON DELETE CASCADE, asi que basta con UNA sentencia y no hace falta
     * transaccion.
     *
     * @return false si el id no existia.
     */
    public static boolean eliminar(int id) throws SQLException {

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_ELIMINAR)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // MAPEO  ResultSet -> objetos de dominio
    // ═════════════════════════════════════════════════════════════════

    /*
     * Convierte la fila actual del ResultSet en el objeto concreto.
     *
     * AQUI SE CIERRA EL CIRCULO DEL DISCRIMINADOR 'tipo'. Cuando lo
     * justificamos como denormalizacion consciente, el argumento fue
     * que listar el catalogo es la operacion mas frecuente. Este metodo
     * es la prueba: sin la columna 'tipo' habria que deducir la clase
     * averiguando cual de las tres hijas trajo datos no nulos, fila por
     * fila. Con ella, un switch directo.
     *
     * PRECONDICION: el ResultSet ya esta posicionado en una fila
     * (alguien llamo a next() antes).
     */
    private static Audiovisual mapear(ResultSet rs) throws SQLException {

        String tipo        = rs.getString("tipo");
        String titulo      = rs.getString("titulo");
        String descripcion = rs.getString("descripcion");
        int    estreno     = rs.getInt("anio_estreno");
        String clasif      = rs.getString("clasificacion");
        double imdb        = rs.getDouble("calificacion_imdb");
        String genero      = rs.getString("genero");

        // Nota sobre nulos: rs.getInt() devuelve 0 cuando la columna es
        // NULL, sin avisar. Para estos campos 0 es un valor imposible en
        // el dominio (no hay peliculas del ano 0), asi que funciona como
        // marcador de ausencia. Si hiciera falta distinguir "sin valor"
        // de "vale cero", la herramienta correcta es rs.wasNull()
        // inmediatamente despues de leer, o rs.getObject(col, Integer.class).

        Audiovisual av = switch (tipo) {
            case "PELICULA" -> new Pelicula(titulo, descripcion, estreno, clasif, imdb, genero,
                    rs.getInt("p_duracion"),
                    rs.getString("p_director"),
                    rs.getString("estudio"));

            case "DOCUMENTAL" -> new Documental(titulo, descripcion, estreno, clasif, imdb, genero,
                    rs.getInt("d_duracion"),
                    rs.getString("d_director"),
                    rs.getString("tema"));

            case "SERIE" -> new Serie(titulo, descripcion, estreno, clasif, imdb, genero,
                    rs.getInt("num_temporadas"),
                    rs.getInt("num_episodios"),
                    rs.getString("estado"));

            // Solo puede ocurrir si alguien agrega un valor al ENUM de
            // la tabla sin agregar la subclase en Java. Fallar con un
            // mensaje claro es mucho mejor que devolver null y que el
            // NullPointerException aparezca tres capas mas arriba.
            default -> throw new SQLException("Tipo desconocido en la base: " + tipo);
        };

        av.setId(rs.getInt("id"));
        return av;
    }

    // ═════════════════════════════════════════════════════════════════
    // UTILIDADES PRIVADAS
    // ═════════════════════════════════════════════════════════════════

    /*
     * Devuelve el valor del discriminador segun la clase concreta.
     * Debe coincidir EXACTAMENTE con el ENUM de la tabla; si no, MySQL
     * rechaza la insercion.
     */
    private static String tipoDe(Audiovisual av) throws SQLException {
        return switch (av) {
            case Pelicula p   -> "PELICULA";
            case Documental d -> "DOCUMENTAL";
            case Serie s      -> "SERIE";
            default -> throw new SQLException(
                    "Subtipo de Audiovisual no soportado: " + av.getClass().getName());
        };
    }

    /*
     * Escribe un entero que puede estar "ausente".
     *
     * El modelo usa int primitivo, que no admite null: la ausencia se
     * representa con 0. Pero 0 no pasa el CHECK ck_anio (1888-2200) y
     * ademas seria un dato falso. Por eso 0 o negativo se traducen a
     * NULL en la base, que es como el modelo relacional expresa
     * "este dato no se conoce".
     *
     * ps.setInt(i, null) no compila (int es primitivo); para NULL hay
     * que declarar explicitamente el tipo SQL con setNull().
     */
    private static void setEnteroOpcional(PreparedStatement ps, int indice, int valor)
            throws SQLException {
        if (valor > 0) {
            ps.setInt(indice, valor);
        } else {
            ps.setNull(indice, Types.INTEGER);
        }
    }

    /*
     * Escribe un id de catalogo que puede ser null (el nombre no existia
     * en la tabla de catalogo). Las columnas clasificacion_id y
     * genero_id son nullable justamente para permitirlo.
     */
    private static void setIdOpcional(PreparedStatement ps, int indice, Integer id)
            throws SQLException {
        if (id != null) {
            ps.setInt(indice, id);
        } else {
            ps.setNull(indice, Types.INTEGER);
        }
    }
}
