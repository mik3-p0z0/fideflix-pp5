package fideflix.interfaz;

import fideflix.red.Protocolo;
import fideflix.red.ServidorFideflix;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/*
 * VENTANA DEL SERVIDOR: consola visual para iniciar/detener el servidor
 * y ver la bitacora de peticiones en tiempo real.
 *
 * Esta ventana esta escrita "a mano" (sin el editor de formularios de
 * NetBeans) para mostrar que un JFrame es solo codigo Java: componentes
 * + layout + listeners. No necesita archivo .form.
 *
 * REGLA DE ORO DE SWING: los componentes solo se tocan desde el hilo de
 * eventos (EDT). Los mensajes de log llegan desde el hilo aceptador y los
 * hilos de cliente, por eso agregarLog() los reenvia al EDT con
 * SwingUtilities.invokeLater(). Sin eso habria condiciones de carrera
 * dentro del propio JTextArea.
 */
public class VentanaServidor extends JFrame {

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(VentanaServidor.class.getName());

    private final JTextArea areaLog = new JTextArea();
    private final JButton btnIniciar = new JButton("Iniciar servidor");
    private final JButton btnDetener = new JButton("Detener servidor");
    private final JLabel lblEstado = new JLabel("Estado: detenido");

    /* La logica de red vive en ServidorFideflix; la ventana solo le pasa
     * el "cable" del log (this::agregarLog) y aprieta botones. */
    private final ServidorFideflix servidor = new ServidorFideflix(this::agregarLog);

    public VentanaServidor() {
        super("Fideflix - Servidor (puerto " + Protocolo.PUERTO + ")");
        construirInterfaz();

        // AUTO-ARRANQUE (cambio pedido en la revision): el servidor empieza
        // a escuchar apenas se crea la ventana, sin esperar el clic en
        // "Iniciar". Los botones siguen funcionando para detener/reiniciar
        // la escucha durante la demo. Si el puerto ya esta ocupado (otra
        // instancia del servidor), iniciarServidor() muestra el error de
        // forma controlada y la ventana queda en estado "detenido".
        iniciarServidor();
    }

    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // ── Zona superior: titulo + estado ──────────────────────────────
        JLabel titulo = new JLabel("Consola del servidor Fideflix");
        titulo.setFont(new Font("Arial", Font.BOLD, 18));
        lblEstado.setFont(new Font("Arial", Font.PLAIN, 14));

        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panelSuperior.add(titulo);
        panelSuperior.add(lblEstado);
        add(panelSuperior, BorderLayout.NORTH);

        // ── Centro: bitacora con scroll ─────────────────────────────────
        areaLog.setEditable(false);
        areaLog.setFont(new Font("Monospaced", Font.PLAIN, 13));
        add(new JScrollPane(areaLog), BorderLayout.CENTER);

        // ── Zona inferior: botones ──────────────────────────────────────
        btnDetener.setEnabled(false);
        btnIniciar.addActionListener(e -> iniciarServidor());
        btnDetener.addActionListener(e -> detenerServidor());

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10));
        panelBotones.add(btnIniciar);
        panelBotones.add(btnDetener);
        add(panelBotones, BorderLayout.SOUTH);

        setSize(700, 450);
        setLocationRelativeTo(null); // centrar en pantalla
    }

    private void iniciarServidor() {
        try {
            servidor.iniciar();
            btnIniciar.setEnabled(false);
            btnDetener.setEnabled(true);
            lblEstado.setText("Estado: escuchando en el puerto " + Protocolo.PUERTO);
        } catch (IOException ex) {
            // Caso tipico: el puerto ya esta en uso (otro servidor abierto).
            JOptionPane.showMessageDialog(this,
                    "No se pudo iniciar el servidor: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void detenerServidor() {
        servidor.detener();
        btnIniciar.setEnabled(true);
        btnDetener.setEnabled(false);
        lblEstado.setText("Estado: detenido");
    }

    /*
     * Punto de entrada del log. Puede ser invocado desde CUALQUIER hilo,
     * por eso salta al EDT antes de tocar el JTextArea.
     */
    private void agregarLog(String mensaje) {
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            areaLog.append("[" + hora + "] " + mensaje + "\n");
            // Auto-scroll al final.
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });
    }

    /* Main propio: permite ejecutar el servidor con "Run File" en NetBeans. */
    public static void main(String[] args) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info
                    : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException
                | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new VentanaServidor().setVisible(true));
    }
}
