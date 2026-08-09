package fideflix.persistencia;

import fideflix.logica.Comentario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/*
 * DAO de la tabla 'comentario'.
 *
 * Es la pieza que corrige el bug de la lista static de Audiovisual: en
 * lugar de una coleccion en memoria compartida por todas las obras, cada
 * comentario es una fila con dos llaves foraneas que dicen exactamente
 * de que obra habla y quien lo escribio.
 *
 * Notese que no hay metodo actualizar(): un comentario publicado no se
 * edita en este sistema. No es un olvido, es una decision de alcance, y
 * un DAO deberia exponer solo las operaciones que el negocio permite.
 * Un CRUD completo "por si acaso" es superficie de ataque regalada.
 */
public final class ComentarioDAO {

    private ComentarioDAO() {
    }

    /* No se envia la fecha: la pone MySQL con el DEFAULT
     * CURRENT_TIMESTAMP de la columna. El reloj de la maquina del
     * cliente puede estar mal o ser manipulado; el del servidor de base
     * de datos es la unica fuente de tiempo confiable del sistema. */
    private static final String SQL_INSERTAR =
            "INSERT INTO comentario (audiovisual_id, usuario_id, texto) "
          + "VALUES (?, ?, ?)";

    /* JOIN a usuario para traer el NOMBRE del autor en la misma consulta.
     * La alternativa seria devolver el usuario_id y que la ventana pida
     * el nombre despues, una consulta por comentario: es el problema
     * N+1, la causa mas comun de lentitud en aplicaciones con base de
     * datos. Una consulta que trae todo lo que la pantalla necesita
     * siempre le gana a muchas consultas pequenas. */
    private static final String SQL_LISTAR_POR_OBRA =
            "SELECT c.id, c.audiovisual_id, u.nombre AS autor, c.texto, "
          + "       DATE_FORMAT(c.fecha_hora, '%d/%m/%Y %H:%i') AS fecha "
          + "FROM comentario c "
          + "JOIN usuario u ON u.id = c.usuario_id "
          + "WHERE c.audiovisual_id = ? "
          + "ORDER BY c.fecha_hora DESC";

    private static final String SQL_ELIMINAR =
            "DELETE FROM comentario WHERE id = ?";

    /*
     * Inserta un comentario.
     *
     * No hace falta transaccion: es UNA sola sentencia sobre UNA sola
     * tabla, y toda sentencia individual en InnoDB ya es atomica por si
     * misma. La transaccion explicita se reserva para cuando hay varias
     * operaciones que deben ocurrir juntas o no ocurrir, como en
     * AudiovisualDAO.insertar(). Abrir transacciones "por costumbre"
     * agrega ruido sin agregar garantias.
     *
     * @return el id generado para el comentario.
     */
    public static int insertar(int audiovisualId, int usuarioId, String texto)
            throws SQLException {

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_INSERTAR,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, audiovisualId);
            ps.setInt(2, usuarioId);
            // El texto es contenido libre escrito por el usuario: el
            // candidato perfecto para una inyeccion SQL. Como parametro
            // de PreparedStatement es solo un dato, aunque contenga
            // comillas, punto y coma o un DROP TABLE completo.
            ps.setString(3, texto);

            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                return claves.next() ? claves.getInt(1) : 0;
            }
        }
    }

    /*
     * Devuelve los comentarios de UNA obra, del mas reciente al mas
     * antiguo.
     *
     * Si la obra no tiene comentarios devuelve una lista VACIA, nunca
     * null. Es una convencion que vale la pena adoptar siempre: quien
     * llama puede recorrerla con un for sin preguntar nada, y se elimina
     * de raiz una clase entera de NullPointerException.
     */
    public static List<Comentario> listarPorAudiovisual(int audiovisualId)
            throws SQLException {

        List<Comentario> lista = new ArrayList<>();

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_LISTAR_POR_OBRA)) {

            ps.setInt(1, audiovisualId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Comentario(
                            rs.getInt("id"),
                            rs.getInt("audiovisual_id"),
                            rs.getString("autor"),
                            rs.getString("texto"),
                            rs.getString("fecha")));
                }
            }
        }
        return lista;
    }

    /*
     * Elimina un comentario por id.
     * @return true si borro una fila, false si el id no existia.
     *
     * executeUpdate() devuelve la cantidad de filas afectadas. Usar ese
     * dato en vez de asumir el exito permite distinguir "se borro" de
     * "no habia nada que borrar", y esa diferencia es la que el servidor
     * necesita para responder NO_ENCONTRADO en lugar de OK.
     */
    public static boolean eliminar(int id) throws SQLException {
        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_ELIMINAR)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
