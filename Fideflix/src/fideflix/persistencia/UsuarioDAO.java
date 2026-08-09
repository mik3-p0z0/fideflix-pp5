package fideflix.persistencia;

import fideflix.logica.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;

/*
 * DAO de la tabla 'usuario'. Reemplaza a PersistenciaUsuarios, que
 * serializaba un ArrayList completo a usuarios.dat.
 *
 * ─── QUE CAMBIA RESPECTO A LA PP4 ───────────────────────────────────
 *
 * 1. DESAPARECE EL CANDADO GLOBAL.
 *    En PP4, HiloCliente envolvia cada operacion en
 *    synchronized (ServidorFideflix.CANDADO_ARCHIVO). Habia que hacerlo:
 *    un archivo no entiende de escritores concurrentes y sin candado el
 *    segundo hilo en guardar pisaba al primero (lost update).
 *    MySQL resuelve eso solo, con transacciones y bloqueo por fila.
 *    Mantener el candado convertiria un servidor multihilo en uno
 *    secuencial: seria comprar una autopista y poner un unico peaje.
 *
 * 2. CAMBIA LA ESTRATEGIA CONTRA DUPLICADOS.
 *    Antes: preguntar "¿ya existe?" (contains) y despues insertar. Son
 *    DOS pasos, y entre uno y otro hay una ventana donde otro hilo puede
 *    colarse (condicion de carrera TOCTOU: time of check, time of use).
 *    Ahora: intentar insertar y dejar que el UNIQUE del motor rechace.
 *    Un solo paso atomico. Es la diferencia entre "mirar antes de
 *    saltar" y "pedir perdon en vez de permiso"; en concurrencia, la
 *    segunda gana siempre.
 *
 * 3. SE GUARDA UN HASH, NO LA CONTRASENA.
 */
public final class UsuarioDAO {

    private UsuarioDAO() {
    }

    /* La fecha de registro la pone la BASE DE DATOS con CURDATE(), no el
     * usuario. En la PP4 salia de un JTextField de texto libre, lo cual
     * tiene dos problemas: cualquier cosa que no sea una fecha valida
     * rompe la columna DATE, y ademas el usuario podria mentir sobre
     * cuando se registro. La fecha de registro es un HECHO QUE EL
     * SISTEMA CONOCE, no un dato que se pide. Mismo criterio que
     * comentario.fecha_hora con DEFAULT CURRENT_TIMESTAMP. */
    private static final String SQL_INSERTAR =
            "INSERT INTO usuario (nombre, email, contrasena_hash, fecha_registro) "
          + "VALUES (?, ?, ?, CURDATE())";

    private static final String SQL_AUTENTICAR =
            "SELECT id, nombre, email, fecha_registro FROM usuario "
          + "WHERE email = ? AND contrasena_hash = ?";

    /*
     * Registra un usuario nuevo.
     *
     * @return el objeto con su id ya asignado por la base.
     * @throws SQLIntegrityConstraintViolationException si el email ya
     *         existe. Se deja SALIR esta excepcion concreta en vez de
     *         convertirla en un booleano: el codigo de error identifica
     *         la causa exacta y HiloCliente la traducira a la respuesta
     *         DUPLICADO del protocolo. Devolver false perderia esa
     *         informacion y obligaria a adivinar por que fallo.
     */
    public static Usuario registrar(Usuario usuario) throws SQLException {

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_INSERTAR,
                     Statement.RETURN_GENERATED_KEYS)) {

            // PreparedStatement con parametros: los valores viajan por un
            // canal separado del texto de la consulta. MySQL ya decidio
            // que esto es un INSERT con tres valores ANTES de recibirlos,
            // asi que ningun dato puede convertirse en instruccion.
            // No es escapar comillas: es separar codigo de datos de raiz.
            // OWASP A03:2021 - Injection / CWE-89.
            ps.setString(1, usuario.getNombre());
            ps.setString(2, usuario.getEmail());
            ps.setString(3, hashear(usuario.getContrasena()));

            ps.executeUpdate();

            // Recuperar el AUTO_INCREMENT que genero MySQL.
            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    usuario.setId(claves.getInt(1));
                }
            }
            return usuario;
        }
    }

    /*
     * Valida credenciales.
     *
     * @return el Usuario si coinciden, null si no.
     *
     * Se compara el hash contra el hash: la contrasena en claro nunca se
     * busca en la base porque en la base no existe. Notese que la
     * consulta filtra por email Y hash a la vez, de modo que un email
     * inexistente y una contrasena incorrecta producen el MISMO
     * resultado (null). Eso es deliberado: responder distinto permitiria
     * enumerar que cuentas existen sin adivinar ni una clave. Es el
     * mismo criterio del RSP_DENEGADO generico que ya usa el protocolo.
     */
    public static Usuario autenticar(String email, String contrasena) throws SQLException {

        try (Connection con = ConexionBD.obtener();
             PreparedStatement ps = con.prepareStatement(SQL_AUTENTICAR)) {

            ps.setString(1, email);
            ps.setString(2, hashear(contrasena));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                // La contrasena se pasa vacia al construir el objeto: una
                // vez autenticado, el sistema no necesita la credencial y
                // no tiene por que pasearla por la memoria del programa.
                Usuario u = new Usuario(
                        rs.getString("nombre"),
                        rs.getString("email"),
                        "",
                        String.valueOf(rs.getDate("fecha_registro")));
                u.setId(rs.getInt("id"));
                return u;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HASHING
    // ─────────────────────────────────────────────────────────────────

    /*
     * Convierte la contrasena en su huella SHA-256 hexadecimal (64
     * caracteres, que es justo lo que declara CHAR(64) en la tabla).
     *
     * Es PRIVADO a proposito: obliga a que la contrasena en claro entre
     * a esta clase y no salga nunca. Si fuera publico, cualquier parte
     * del programa podria empezar a mover hashes de un lado a otro y se
     * perderia el control de donde vive la credencial.
     *
     * ─── ADVERTENCIA DE SEGURIDAD (declararla en el README) ──────────
     * SHA-256 sin salt es el MINIMO aceptable en un trabajo academico y
     * NO es aceptable en produccion, por dos razones:
     *
     *  1. Es un hash RAPIDO. Fue disenado para verificar integridad de
     *     archivos, donde la velocidad es una virtud. Para contrasenas
     *     es exactamente el defecto: una GPU prueba miles de millones de
     *     candidatos por segundo.
     *  2. No lleva SALT. Dos usuarios con la misma contrasena producen
     *     el mismo hash, visible a simple vista en la tabla, y eso
     *     habilita ataques con tablas precalculadas (rainbow tables).
     *
     * Lo correcto en produccion es bcrypt, scrypt o Argon2id: algoritmos
     * deliberadamente LENTOS y con salt incorporado por diseno.
     * Ver: OWASP Password Storage Cheat Sheet.
     *
     * Limitacion adicional heredada de la PP4: la contrasena viaja en
     * texto plano por el socket. Solo TLS (SSLSocket) resuelve eso.
     */
    private static String hashear(String texto) throws SQLException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] resumen = md.digest(texto.getBytes(StandardCharsets.UTF_8));

            // Los bytes se pasan a hexadecimal para poder guardarlos como
            // texto. %02x = dos digitos hex con cero a la izquierda; sin
            // el "02", un byte como 0x0A se escribiria "a" y el hash
            // quedaria de menos de 64 caracteres.
            StringBuilder sb = new StringBuilder(64);
            for (byte b : resumen) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM: si falta, el entorno
            // esta roto. Se envuelve en SQLException para no obligar al
            // llamador a manejar dos tipos de excepcion no relacionados.
            throw new SQLException("Algoritmo de hash no disponible en esta JVM", e);
        }
    }
}
