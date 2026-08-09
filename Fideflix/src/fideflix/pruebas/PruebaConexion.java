package fideflix.pruebas;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/*
 * PRUEBA DE HUMO ("smoke test") DE LA CONEXION A MYSQL.
 *
 * Por que existe: antes de escribir los DAO conviene aislar UNA sola
 * variable, la conectividad. Si algo falla mas adelante, ya sabras que
 * el problema no esta aqui. Depurar es reducir sospechosos, y este
 * archivo elimina al primero de la lista.
 *
 * Se ejecuta con clic derecho sobre el archivo -> Run File (Shift+F6).
 * No forma parte del flujo de la aplicacion: no lo llama nadie.
 *
 * NOTA SOBRE EL DRIVER: desde Connector/J 8 no hace falta el viejo
 * Class.forName("com.mysql.cj.jdbc.Driver"). El driver se registra solo
 * gracias al mecanismo ServiceLoader de JDBC 4.0+ (el .jar declara su
 * clase en META-INF/services). Si ves Class.forName en tutoriales
 * viejos, no es que este mal: es que sobra desde hace mas de una decada.
 */
public class PruebaConexion {

    /* Ruta relativa al directorio de trabajo. Al ejecutar desde NetBeans,
     * ese directorio es la carpeta del proyecto (la misma donde vivia
     * usuarios.dat en la PP4). */
    private static final String ARCHIVO_CONFIG = "db.properties";

    public static void main(String[] args) {

        Properties config = new Properties();

        // ── 1. Cargar la configuracion ───────────────────────────────
        // try-with-resources: el stream se cierra siempre, incluso si
        // ocurre una excepcion. Un descriptor de archivo sin cerrar es
        // una fuga de recursos del sistema operativo.
        try (InputStream in = new FileInputStream(ARCHIVO_CONFIG)) {
            config.load(in);
        } catch (IOException e) {
            System.err.println("No se pudo leer " + ARCHIVO_CONFIG + ": " + e.getMessage());
            System.err.println("Copia db.properties.example como db.properties "
                    + "y completa la contrasena.");
            return;
        }

        String url      = config.getProperty("db.url");
        String usuario  = config.getProperty("db.user");
        String password = config.getProperty("db.password");

        // Guarda contra el error mas comun: olvidar reemplazar el marcador.
        if (password == null || password.startsWith("REEMPLAZAR")) {
            System.err.println("La contrasena en db.properties sigue siendo el marcador. "
                    + "Reemplazala por la clave real de fideflix_app.");
            return;
        }

        // ── 2. Conectar y consultar ──────────────────────────────────
        // La Connection tambien va en try-with-resources: una conexion
        // abierta consume un hilo y memoria DEL SERVIDOR MySQL, no solo
        // de tu programa. Dejarlas abiertas agota el limite de
        // conexiones del motor y tumba la aplicacion entera.
        //
        // ADVERTENCIA: aqui usamos Statement porque la consulta es fija y
        // no recibe NINGUN dato del usuario. En cuanto un valor venga de
        // afuera, la unica opcion valida es PreparedStatement con
        // parametros (?), nunca concatenacion de texto.
        // Ver OWASP A03:2021 - Injection / CWE-89.
        try (Connection con = DriverManager.getConnection(url, usuario, password);
             Statement st = con.createStatement()) {

            System.out.println("== CONEXION ESTABLECIDA ==");
            System.out.println("Motor    : " + con.getMetaData().getDatabaseProductName()
                             + " " + con.getMetaData().getDatabaseProductVersion());
            System.out.println("Driver   : " + con.getMetaData().getDriverName()
                             + " " + con.getMetaData().getDriverVersion());
            System.out.println("Usuario  : " + con.getMetaData().getUserName());
            System.out.println("Catalogo : " + con.getCatalog());
            System.out.println();

            // Conteo por tipo: valida que el esquema y los datos de
            // prueba esten donde deben.
            try (ResultSet rs = st.executeQuery(
                    "SELECT tipo, COUNT(*) AS total FROM audiovisual GROUP BY tipo")) {
                System.out.println("== CATALOGO EN LA BASE ==");
                boolean hayFilas = false;
                while (rs.next()) {
                    hayFilas = true;
                    System.out.printf("  %-12s %d%n", rs.getString("tipo"),
                                                      rs.getInt("total"));
                }
                if (!hayFilas) {
                    System.out.println("  (vacio) Ejecuta 02_datos_iniciales.sql");
                }
            }

            // Verificacion de la jerarquia: la consulta con LEFT JOIN que
            // usara AudiovisualDAO en la Fase 4, reducida a lo esencial.
            try (ResultSet rs = st.executeQuery(
                    "SELECT a.titulo, a.tipo, g.nombre AS genero "
                  + "FROM audiovisual a "
                  + "LEFT JOIN genero g ON g.id = a.genero_id "
                  + "ORDER BY a.titulo")) {
                System.out.println();
                System.out.println("== OBRAS ==");
                while (rs.next()) {
                    System.out.printf("  [%-10s] %-20s %s%n",
                            rs.getString("tipo"),
                            rs.getString("titulo"),
                            rs.getString("genero"));
                }
            }

            System.out.println();
            System.out.println("== PRUEBA SUPERADA ==");

        } catch (SQLException e) {
            // Diagnostico orientado: traduce los errores tipicos a la
            // causa real, para no perder tiempo buscando en el lugar
            // equivocado.
            System.err.println("FALLO LA CONEXION");
            System.err.println("  Mensaje    : " + e.getMessage());
            System.err.println("  SQLState   : " + e.getSQLState());
            System.err.println("  Codigo     : " + e.getErrorCode());
            System.err.println();
            System.err.println("  Pistas segun el error:");
            System.err.println("   - 'No suitable driver'      -> el .jar del Connector/J");
            System.err.println("                                  no esta en el classpath");
            System.err.println("   - 'Communications link'     -> el servicio MySQL no corre");
            System.err.println("   - 'Access denied'           -> usuario o contrasena mal");
            System.err.println("   - 'Unknown database'        -> falta ejecutar 01_schema.sql");
        }
    }
}
