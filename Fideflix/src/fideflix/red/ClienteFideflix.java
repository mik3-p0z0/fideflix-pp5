package fideflix.red;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/*
 * LADO CLIENTE de la comunicacion.
 *
 * Patron de uso: "conexion por peticion" (igual que HTTP/1.0):
 *   1. Abrir socket hacia el servidor.
 *   2. Enviar UNA linea con la peticion.
 *   3. Leer UNA linea con la respuesta.
 *   4. Cerrar el socket.
 *
 * Es el esquema mas simple de razonar: no hay estado de sesion que mantener
 * y cada peticion es independiente (sin conexiones colgadas que administrar).
 *
 * Las ventanas (ventanaInicioSesion, VentanaCrearUsuario) llaman a
 * enviarPeticion() y ya NO tocan el archivo usuarios.dat: la persistencia
 * ahora es responsabilidad exclusiva del servidor.
 */
public final class ClienteFideflix {

    /* Milisegundos maximos de espera para conectar y para leer.
     * Sin timeouts, un servidor caido congelaria la interfaz para siempre. */
    private static final int TIMEOUT_MS = 5000;

    private ClienteFideflix() {
    }

    /*
     * Envia una peticion (una linea de texto del Protocolo) y devuelve
     * la linea de respuesta del servidor.
     *
     * try-with-resources: el socket se cierra SIEMPRE al salir del try,
     * incluso si hay excepcion. Un socket es un descriptor del sistema
     * operativo; no cerrarlo es una fuga de recursos.
     *
     * @throws IOException si no hay servidor escuchando, se agota el
     *         timeout, o la conexion se corta a mitad de camino.
     */
    public static String enviarPeticion(String peticion) throws IOException {
        try (Socket socket = new Socket()) {

            // connect() con timeout: si el servidor no existe o no responde
            // en TIMEOUT_MS, lanza IOException en vez de colgarse.
            socket.connect(new InetSocketAddress(Protocolo.HOST, Protocolo.PUERTO),
                           TIMEOUT_MS);

            // setSoTimeout limita cuanto esperamos una LECTURA (readLine).
            socket.setSoTimeout(TIMEOUT_MS);

            // Flujo de SALIDA: por aqui escribimos hacia el servidor.
            // autoFlush=true -> println() envia de inmediato (no se queda
            // en el buffer). UTF-8 explicito para tildes y enes.
            PrintWriter salida = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(),
                                           StandardCharsets.UTF_8), true);

            // Flujo de ENTRADA: por aqui leemos lo que responde el servidor.
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(),
                                          StandardCharsets.UTF_8));

            salida.println(peticion);          // 1 linea de peticion
            String respuesta = entrada.readLine(); // 1 linea de respuesta

            if (respuesta == null) {
                // readLine() devuelve null si el otro extremo cerro sin escribir.
                throw new IOException("El servidor cerro la conexion sin responder.");
            }
            return respuesta;
        }
        // Nota: no cerramos 'salida'/'entrada' por separado; al cerrar el
        // socket se cierran sus streams asociados.
    }
}
