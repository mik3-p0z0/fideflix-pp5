package fideflix.persistencia;

import fideflix.logica.ItemCatalogo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/*
 * DAO de las tablas de catalogo: 'genero' y 'clasificacion'.
 *
 * Sirve para poblar los JComboBox de la ventana de formulario. Que las
 * opciones vengan de la base y no de un arreglo escrito a mano en la
 * interfaz tiene una consecuencia concreta: es IMPOSIBLE que el usuario
 * elija un genero que no existe en la tabla, asi que la llave foranea
 * nunca falla por culpa de la interfaz. La restriccion del motor y las
 * opciones de la pantalla salen de la misma fuente de verdad.
 */
public final class CatalogoDAO {

    private CatalogoDAO() {
    }

    /*
     * Consultas fijas, sin ningun dato que venga del usuario.
     * Aqui una concatenacion seria inofensiva, pero igual se usa
     * PreparedStatement: la coherencia evita que un dia alguien copie
     * este metodo como plantilla para uno que SI recibe parametros.
     * Las buenas practicas valen justamente cuando parecen innecesarias.
     */
    private static final String SQL_GENEROS =
            "SELECT id, nombre FROM genero ORDER BY nombre";

    private static final String SQL_CLASIFICACIONES =
            "SELECT id, codigo FROM clasificacion ORDER BY id";

    public static List<ItemCatalogo> listarGeneros() throws SQLException {
        return consultar(SQL_GENEROS, "nombre");
    }

    public static List<ItemCatalogo> listarClasificaciones() throws SQLException {
        return consultar(SQL_CLASIFICACIONES, "codigo");
    }

    /*
     * Metodo privado compartido: las dos consultas tienen la misma forma
     * (id + etiqueta), solo cambian la tabla y el nombre de la columna.
     * Extraerlo evita duplicar el bloque try-with-resources dos veces.
     *
     * try-with-resources anidado: Connection, PreparedStatement y
     * ResultSet se cierran en orden inverso al de apertura, incluso si
     * ocurre una excepcion. Los tres son recursos del sistema; el
     * ResultSet ademas puede tener un cursor abierto en el servidor.
     */
    private static List<ItemCatalogo> consultar(String sql, String columnaEtiqueta)
            throws SQLException {

        List<ItemCatalogo> items = new ArrayList<>();

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                items.add(new ItemCatalogo(rs.getInt("id"),
                                           rs.getString(columnaEtiqueta)));
            }
        }
        return items;
    }

    /*
     * Busca el id de un genero por su nombre.
     *
     * Lo necesita AudiovisualDAO cuando recibe un objeto de dominio que
     * trae el genero como texto (asi lo modela Audiovisual desde la PP4).
     *
     * Devuelve null si no existe, en lugar de 0. La diferencia importa:
     * 0 podria confundirse con un id valido, mientras que null obliga a
     * quien llama a decidir explicitamente que hacer con la ausencia.
     * Aqui esa decision es guardar NULL en la columna, que es legal
     * porque genero_id es nullable.
     */
    public static Integer idDeGenero(Connection con, String nombre) throws SQLException {
        return buscarId(con, "SELECT id FROM genero WHERE nombre = ?", nombre);
    }

    public static Integer idDeClasificacion(Connection con, String codigo) throws SQLException {
        return buscarId(con, "SELECT id FROM clasificacion WHERE codigo = ?", codigo);
    }

    /*
     * Recibe la Connection como parametro en vez de abrir una propia.
     *
     * Esto NO es un descuido: cuando AudiovisualDAO esta dentro de una
     * transaccion, esta busqueda debe correr sobre ESA MISMA conexion.
     * Una conexion distinta seria una transaccion distinta y no veria
     * los cambios en curso. Compartir la conexion es lo que permite
     * componer operaciones dentro de una unidad atomica.
     */
    private static Integer buscarId(Connection con, String sql, String valor)
            throws SQLException {

        if (valor == null || valor.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
        // Nota: no se cierra 'con' aqui. Quien la abrio es quien la cierra.
        // Cerrar un recurso que no te pertenece rompe al que te llamo.
    }
}
