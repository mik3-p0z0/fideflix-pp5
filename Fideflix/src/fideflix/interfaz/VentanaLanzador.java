package fideflix.interfaz;

import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/*
 * LANZADOR DE CLIENTES.
 *
 * Sustituye al antiguo dialogo modal "¿Servidor o Cliente?". ¿Por que una
 * ventana y no un JOptionPane en bucle? Porque JOptionPane es MODAL:
 * mientras esta visible bloquea la interaccion con TODAS las demas
 * ventanas de la aplicacion. Con varios clientes abiertos en la misma JVM
 * eso congelaria la demo de concurrencia. Un JFrame normal (no modal)
 * convive con el resto de ventanas sin estorbar.
 *
 * Cada clic en [Nuevo cliente] abre una ventana de inicio de sesion
 * independiente. Asi se pueden abrir N clientes contra el mismo servidor
 * y demostrar el hilo-por-conexion sin volver a ejecutar el programa.
 *
 * DISPOSE_ON_CLOSE: cerrar el lanzador NO termina la aplicacion
 * (el servidor y los clientes abiertos siguen vivos). La aplicacion
 * completa termina al cerrar la VentanaServidor (EXIT_ON_CLOSE), que es
 * la "columna vertebral" del sistema.
 */
public class VentanaLanzador extends JFrame {

    private int contadorClientes = 0;
    private final JLabel lblContador = new JLabel("Clientes abiertos: 0");

    public VentanaLanzador() {
        super("Fideflix - Lanzador de clientes");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new FlowLayout(FlowLayout.CENTER, 20, 15));

        JLabel instruccion = new JLabel(
                "El servidor ya está en ejecución. Abra los clientes que necesite:");
        instruccion.setFont(new Font("Arial", Font.PLAIN, 14));

        JButton btnNuevoCliente = new JButton("Nuevo cliente");
        btnNuevoCliente.setFont(new Font("Arial", Font.BOLD, 16));
        btnNuevoCliente.addActionListener(e -> abrirCliente());

        lblContador.setFont(new Font("Arial", Font.PLAIN, 13));

        add(instruccion);
        add(btnNuevoCliente);
        add(lblContador);

        setSize(480, 140);
        // Colocarlo arriba-izquierda para que no tape al servidor (centrado).
        setLocation(40, 40);
    }

    private void abrirCliente() {
        contadorClientes++;
        lblContador.setText("Clientes abiertos: " + contadorClientes);

        ventanaInicioSesion cliente = new ventanaInicioSesion();
        // Desplazar cada cliente un poco para que no queden apilados
        // exactamente uno encima del otro.
        cliente.setLocation(120 + contadorClientes * 40, 120 + contadorClientes * 40);
        cliente.setVisible(true);
    }
}
