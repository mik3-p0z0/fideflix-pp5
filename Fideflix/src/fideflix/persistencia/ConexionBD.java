package fideflix.persistencia;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/*
 * FABRICA DE CONEXIONES A MYSQL.
 *
 * Responsabilidad unica: entregar conexiones ya configuradas. No sabe
 * nada de usuarios ni de peliculas; eso es tarea de los DAO.
 *
 * ─── POR QUE LAS CREDENCIALES NO ESTAN EN EL CODIGO ─────────────────
 * El codigo y los secretos tienen ciclos de vida distintos: el codigo se
 * comparte, se publica y se versiona; los secretos se rotan y no salen
 * de la maquina. Si estuvieran mezclados habria que elegir entre
 * compartir el proyecto o proteger la clave. Por eso viven en
 * db.properties, que esta en .gitignore.
 *
 * ─── POR QUE UNA CONEXION NUEVA POR LLAMADA ─────────────────────────
 * java.sql.Connection NO es thread-safe. Este servidor atiende N
 * clientes en N hilos simultaneos (HiloCliente); si compartieran una
 * unica Connection estatica, dos hilos podrian mezclar sus transacciones
 * y producir errores erraticos e irreproducibles, que son los peores de
 * depurar. Cada operacion abre la suya y la cierra.
 *
 * LIMITACION CONOCIDA (declarada en el README): abrir una conexion tiene
 * costo real (handshake TCP + autenticacion). En produccion se usa un
 * POOL de conexiones (HikariCP, por ejemplo), que mantiene un conjunto
 * de conexiones vivas y las presta. Aqui se prefiere la version simple
 * porque hace visible el ciclo abrir-usar-cerrar, que es justo lo que
 * el pool esconde.
 */
public final class ConexionBD {

    /* Ruta relativa al directorio de trabajo de la aplicacion. Al
     * ejecutar desde NetBeans, es la carpeta del proyecto (Fideflix/). */
    private static final String ARCHIVO_CONFIG = "db.properties";

    private static final Properties CONFIG = new Properties();

    /* Bandera de "ya se leyo el archivo". Se lee una sola vez: el disco
     * es lento y la configuracion no cambia mientras corre el programa. */
    private static boolean cargado = false;

    /* Clase de utilidad: no se instancia. */
    private ConexionBD() {
    }

    /*
     * Carga perezosa ("lazy") de la configuracion.
     *
     * synchronized: dos HiloCliente podrian llamar a obtener() en el mismo
     * instante la primera vez. Sin el candado, ambos entrarian a leer el
     * archivo y CONFIG podria quedar a medio poblar. Aqui SI hace falta
     * sincronizar, a diferencia del acceso a las tablas: esto es estado
     * compartido en memoria de la JVM, no de MySQL.
     *
     * Se prefiere este metodo a un bloque static porque un static que
     * falla lanza ExceptionInInitializerError, un error opaco y dificil
     * de diagnosticar. Asi podemos lanzar una SQLException con un mensaje
     * que dice exactamente que hacer.
     */
    private static synchronized void cargarConfig() throws SQLException {
        if (cargado) {
            return;
        }
        try (InputStream in = new FileInputStream(ARCHIVO_CONFIG)) {
            CONFIG.load(in);
            cargado = true;
        } catch (IOException e) {
            throw new SQLException(
                    "No se pudo leer " + ARCHIVO_CONFIG
                    + ". Copie db.properties.example como db.properties "
                    + "y complete la contrasena de fideflix_app.", e);
        }
    }

    /*
     * Devuelve una conexion NUEVA, lista para usar.
     *
     * El llamador es responsable de cerrarla, idealmente con
     * try-with-resources:
     *
     *     try (Connection con = ConexionBD.obtener()) { ... }
     *
     * Dejar conexiones abiertas no solo consume memoria del programa:
     * cada una ocupa un hilo y recursos DEL SERVIDOR MySQL. Agotar el
     * limite max_connections tumba la aplicacion entera, y el sintoma
     * aparece lejos del codigo que causo la fuga.
     */
    public static Connection obtener() throws SQLException {
        cargarConfig();

        String url      = CONFIG.getProperty("db.url");
        String usuario  = CONFIG.getProperty("db.user");
        String password = CONFIG.getProperty("db.password");

        if (url == null || usuario == null || password == null) {
            throw new SQLException("db.properties incompleto: se requieren "
                    + "db.url, db.user y db.password.");
        }
        if (password.startsWith("REEMPLAZAR")) {
            throw new SQLException("La contrasena en db.properties sigue "
                    + "siendo el marcador de la plantilla.");
        }

        // Desde Connector/J 8 el driver se registra solo (ServiceLoader
        // de JDBC 4.0+). No hace falta Class.forName("com.mysql.cj.jdbc.Driver").
        return DriverManager.getConnection(url, usuario, password);
    }
}
