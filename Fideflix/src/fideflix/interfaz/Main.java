package fideflix.interfaz;

import fideflix.interfaz.VentanaLanzador;
import fideflix.interfaz.VentanaServidor;

/*
 * PUNTO DE ENTRADA UNICO del proyecto (el "Main.java" que pide la consigna).
 *
 * Flujo (version revisada):
 *   1. Al ejecutar el programa se abre la VentanaServidor, que AUTO-INICIA
 *      la escucha en el puerto 5000 (ya no hay que elegir "Servidor" ni
 *      apretar Iniciar: sin servidor, el cliente no sirve de nada).
 *   2. Se abre tambien la VentanaLanzador: cada clic en [Nuevo cliente]
 *      abre una ventana de inicio de sesion independiente. Asi se pueden
 *      tener N clientes simultaneos contra el mismo servidor y demostrar
 *      la atencion multihilo sin volver a ejecutar el programa.
 *
 * Ciclo de vida:
 *   - Cerrar un cliente o el lanzador -> solo esa ventana (DISPOSE_ON_CLOSE).
 *   - Cerrar la VentanaServidor      -> termina toda la aplicacion
 *     (EXIT_ON_CLOSE): sin servidor no hay sistema.
 *
 * Nota didactica: aunque aqui todo corre en una JVM por comodidad de la
 * demo, la comunicacion sigue siendo por sockets TCP reales sobre
 * localhost:5000. Ejecutar el proyecto una segunda vez tambien funciona
 * como proceso separado: la segunda VentanaServidor reportara "puerto en
 * uso" y bastara con usar su lanzador para abrir clientes.
 */
public class Main {

    public static void main(String[] args) {
        // Toda creacion de ventanas Swing va en el EDT (regla de oro).
        java.awt.EventQueue.invokeLater(() -> {
            // 1) Servidor primero: al crearse ya queda escuchando.
            new VentanaServidor().setVisible(true);

            // 2) Lanzador de clientes, siempre disponible y no modal.
            new VentanaLanzador().setVisible(true);
        });
    }
}
