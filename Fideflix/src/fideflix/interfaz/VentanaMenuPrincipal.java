package fideflix.interfaz;

import fideflix.logica.Usuario;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/*
 * MENU PRINCIPAL del cliente, ya autenticado.
 *
 * ─── POR QUE AHORA GUARDA EL Usuario ────────────────────────────────
 * En la PP4 solo usaba el nombre para saludar. En la PP5 necesita el
 * OBJETO COMPLETO, y en particular su id: la tabla 'comentario' exige
 * un usuario_id (llave foranea), asi que sin el id no se puede atribuir
 * un comentario a nadie. El id viaja desde el servidor en la respuesta
 * del LOGIN.
 *
 * ─── POR QUE SE REESCRIBIO SIN ARCHIVO .form ────────────────────────
 * Los .form son XML generado por el disenador y son practicamente
 * imposibles de fusionar cuando dos personas editan la misma ventana.
 * En un proyecto grupal versionado eso cuesta horas. Escrita a mano, la
 * ventana se lee, se revisa y se fusiona como cualquier codigo.
 * El precio es perder el disenador visual para esta pantalla.
 */
public class VentanaMenuPrincipal extends JFrame {

    private final Usuario usuario;

    public VentanaMenuPrincipal(Usuario usuario) {
        super("Fideflix - Menú principal");
        this.usuario = usuario;

        /* Cerrar el menu de UN cliente no debe terminar la aplicacion
         * completa: el servidor y los demas clientes viven en la misma
         * JVM. La aplicacion entera termina al cerrar VentanaServidor. */
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 380);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        add(construirCabecera(), BorderLayout.NORTH);
        add(construirBotones(), BorderLayout.CENTER);
        add(construirPie(), BorderLayout.SOUTH);
    }

    private JPanel construirCabecera() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(25, 20, 10, 20));
        p.setBackground(new Color(245, 245, 245));

        JLabel lblBienvenida = new JLabel("Bienvenido(a) " + usuario.getNombre());
        lblBienvenida.setFont(new Font("Arial", Font.BOLD, 26));
        lblBienvenida.setAlignmentX(CENTER_ALIGNMENT);
        lblBienvenida.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel lblDatos = new JLabel(usuario.getEmail()
                + "   ·   miembro desde " + usuario.getFechaRegistro());
        lblDatos.setFont(new Font("Arial", Font.PLAIN, 12));
        lblDatos.setForeground(Color.DARK_GRAY);
        lblDatos.setAlignmentX(CENTER_ALIGNMENT);

        p.add(lblBienvenida);
        p.add(Box.createVerticalStrut(6));
        p.add(lblDatos);
        return p;
    }

    private JPanel construirBotones() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(10, 90, 10, 90));

        JButton btnCatalogo = boton("Ver catálogo de contenido");
        btnCatalogo.addActionListener(e -> new VentanaCatalogo(usuario).setVisible(true));

        p.add(Box.createVerticalGlue());
        p.add(btnCatalogo);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JButton boton(String texto) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Arial", Font.BOLD, 15));
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setPreferredSize(new Dimension(320, 48));
        return b;
    }

    private JPanel construirPie() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));

        JButton btnCerrarSesion = new JButton("Cerrar sesión");
        btnCerrarSesion.addActionListener(e -> {
            /* Se abre una ventana de login NUEVA y se descarta esta.
             * El objeto Usuario queda sin referencias y el recolector de
             * basura se lo lleva: la sesion no sobrevive al cierre. */
            new ventanaInicioSesion().setVisible(true);
            dispose();
        });

        p.add(btnCerrarSesion);
        return p;
    }
}
