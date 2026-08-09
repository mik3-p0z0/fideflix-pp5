package fideflix.interfaz;

import fideflix.logica.Audiovisual;
import fideflix.logica.Documental;
import fideflix.logica.ItemCatalogo;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;
import fideflix.red.ClienteFideflix;
import fideflix.red.CodificadorAudiovisual;
import fideflix.red.Protocolo;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/*
 * FORMULARIO DE ALTA Y EDICION de una obra audiovisual.
 *
 * ─── UNA SOLA VENTANA PARA CREAR Y EDITAR ───────────────────────────
 * Si recibe null, crea; si recibe una obra, precarga sus datos y
 * actualiza. Dos ventanas casi identicas serian dos lugares donde
 * corregir el mismo bug, y solo haria falta olvidarse de uno.
 *
 * ─── CardLayout PARA LOS CAMPOS ESPECIFICOS ─────────────────────────
 * Pelicula, Documental y Serie comparten seis campos y difieren en
 * tres. En lugar de tres formularios, hay uno con un panel que
 * intercambia tres variantes segun el tipo elegido. CardLayout es
 * exactamente eso: un mazo de paneles del que se muestra uno.
 *
 * ─── VALIDACION EN DOS CAPAS ────────────────────────────────────────
 * Esta ventana valida para dar buena experiencia: avisar antes de
 * enviar. El SERVIDOR valida porque el cliente no es confiable:
 * cualquiera puede mandar una linea cruda al puerto 5000 con telnet.
 * Validacion en la interfaz = comodidad. Validacion en el servidor =
 * seguridad. Nunca son lo mismo, aunque el codigo se parezca.
 */
public class VentanaAudiovisualForm extends JFrame {

    /* null = alta; distinto de null = edicion. */
    private final Audiovisual original;

    /* Que ejecutar tras guardar con exito. Recibirlo como Runnable en
     * vez de una referencia a VentanaCatalogo mantiene bajo el
     * acoplamiento: este formulario no necesita saber quien lo abrio,
     * solo que hay algo que hacer al terminar. */
    private final Runnable alGuardar;

    // ── Campos comunes ───────────────────────────────────────────────
    private final JComboBox<String> cboTipo;
    private final JTextField txtTitulo    = new JTextField();
    private final JTextArea  txtDescripcion = new JTextArea(4, 20);
    private final JTextField txtAnio      = new JTextField();
    private final JTextField txtImdb      = new JTextField();
    private final JComboBox<ItemCatalogo> cboClasificacion = new JComboBox<>();
    private final JComboBox<ItemCatalogo> cboGenero        = new JComboBox<>();

    // ── Campos especificos (uno por tipo) ────────────────────────────
    private final JTextField txtPelDuracion = new JTextField();
    private final JTextField txtPelDirector = new JTextField();
    private final JTextField txtPelEstudio  = new JTextField();

    private final JTextField txtDocDuracion = new JTextField();
    private final JTextField txtDocDirector = new JTextField();
    private final JTextField txtDocTema     = new JTextField();

    private final JTextField txtSerTemporadas = new JTextField();
    private final JTextField txtSerEpisodios  = new JTextField();
    private final JTextField txtSerEstado     = new JTextField();

    private final JPanel panelEspecifico = new JPanel(new CardLayout());

    public VentanaAudiovisualForm(Audiovisual existente, Runnable alGuardar) {
        super(existente == null ? "Fideflix - Nueva obra"
                                : "Fideflix - Editar: " + existente.getTitulo());
        this.original = existente;
        this.alGuardar = alGuardar;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        cboTipo = new JComboBox<>(new String[]{
            Protocolo.TIPO_PELICULA, Protocolo.TIPO_DOCUMENTAL, Protocolo.TIPO_SERIE
        });

        add(construirFormulario(), BorderLayout.CENTER);
        add(construirBotones(), BorderLayout.SOUTH);

        cargarCatalogos();

        if (existente != null) {
            precargar(existente);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // CONSTRUCCION DE LA INTERFAZ
    // ═════════════════════════════════════════════════════════════════

    private JPanel construirFormulario() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));

        panel.add(fila("Tipo:", cboTipo));

        /* CardLayout: cambiar el combo muestra el panel de ese tipo. */
        cboTipo.addActionListener(e -> mostrarPanelDe(
                String.valueOf(cboTipo.getSelectedItem())));

        panel.add(fila("Título:", txtTitulo));

        txtDescripcion.setLineWrap(true);
        txtDescripcion.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(txtDescripcion);
        scroll.setPreferredSize(new Dimension(400, 80));
        panel.add(fila("Descripción:", scroll));

        panel.add(fila("Año de estreno:", txtAnio));
        panel.add(fila("Calificación IMDb (0-10):", txtImdb));
        panel.add(fila("Clasificación:", cboClasificacion));
        panel.add(fila("Género:", cboGenero));

        panel.add(Box.createVerticalStrut(10));

        // ── Los tres paneles del mazo ────────────────────────────────
        panelEspecifico.setBorder(
                BorderFactory.createTitledBorder("Datos específicos del tipo"));

        panelEspecifico.add(panelDe(
                "Duración (min):", txtPelDuracion,
                "Director:",       txtPelDirector,
                "Estudio:",        txtPelEstudio), Protocolo.TIPO_PELICULA);

        panelEspecifico.add(panelDe(
                "Duración (min):", txtDocDuracion,
                "Director:",       txtDocDirector,
                "Tema:",           txtDocTema), Protocolo.TIPO_DOCUMENTAL);

        panelEspecifico.add(panelDe(
                "Temporadas:", txtSerTemporadas,
                "Episodios:",  txtSerEpisodios,
                "Estado:",     txtSerEstado), Protocolo.TIPO_SERIE);

        panel.add(panelEspecifico);
        return panel;
    }

    private JPanel panelDe(String e1, JTextField c1,
                           String e2, JTextField c2,
                           String e3, JTextField c3) {
        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.add(new JLabel(e1)); p.add(c1);
        p.add(new JLabel(e2)); p.add(c2);
        p.add(new JLabel(e3)); p.add(c3);
        return p;
    }

    private JPanel fila(String etiqueta, java.awt.Component campo) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        JLabel lbl = new JLabel(etiqueta);
        lbl.setPreferredSize(new Dimension(170, 26));
        p.add(lbl, BorderLayout.WEST);
        p.add(campo, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return p;
    }

    private JPanel construirBotones() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));

        JButton btnGuardar = new JButton(original == null ? "Crear" : "Guardar cambios");
        JButton btnCancelar = new JButton("Cancelar");

        btnGuardar.addActionListener(e -> guardar());
        btnCancelar.addActionListener(e -> dispose());

        p.add(btnGuardar);
        p.add(btnCancelar);
        return p;
    }

    private void mostrarPanelDe(String tipo) {
        ((CardLayout) panelEspecifico.getLayout()).show(panelEspecifico, tipo);
    }

    // ═════════════════════════════════════════════════════════════════
    // CARGA DE DATOS
    // ═════════════════════════════════════════════════════════════════

    /*
     * Pide los catalogos al servidor y llena los combos.
     *
     * POR QUE LOS COMBOS SE LLENAN DESDE LA BASE Y NO CON UN ARREGLO
     * ESCRITO A MANO: si las opciones vinieran de un String[] en el
     * codigo, nada garantiza que coincidan con las filas de las tablas
     * 'genero' y 'clasificacion'. Trayendolas del servidor es IMPOSIBLE
     * elegir un valor que no exista, y la llave foranea nunca falla por
     * culpa de la interfaz. La restriccion del motor y las opciones de
     * la pantalla salen de la misma fuente de verdad.
     */
    private void cargarCatalogos() {
        try {
            List<String> respuesta = ClienteFideflix.enviarPeticionMultilinea(
                    Protocolo.CMD_CATALOGOS);

            List<ItemCatalogo> generos = new ArrayList<>();
            List<ItemCatalogo> clasifs = new ArrayList<>();

            for (int i = 1; i < respuesta.size(); i++) {
                String[] c = respuesta.get(i).split(Protocolo.SEPARADOR_REGEX, -1);
                ItemCatalogo item = new ItemCatalogo(
                        Integer.parseInt(c[1]), Protocolo.desescapar(c[2]));

                if ("GENERO".equals(c[0])) {
                    generos.add(item);
                } else {
                    clasifs.add(item);
                }
            }
            generos.forEach(cboGenero::addItem);
            clasifs.forEach(cboClasificacion::addItem);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudieron cargar los catálogos.\n"
                  + "Verifique que el servidor esté iniciado.\n\n"
                  + "Detalle: " + ex.getMessage(),
                    "Sin conexión", JOptionPane.ERROR_MESSAGE);
        }
    }

    /*
     * Precarga el formulario en modo edicion.
     *
     * El combo de tipo se DESHABILITA. Cambiar una pelicula por una
     * serie exigiria borrar la fila de 'pelicula' e insertar en 'serie'
     * dentro de la misma transaccion: es implementable, pero agrega un
     * camino de codigo que casi nunca se usa y que falla de formas
     * sutiles. Limitacion consciente, declarada en el README.
     * Marcar un limite y justificarlo demuestra mas criterio que
     * implementarlo a medias.
     */
    private void precargar(Audiovisual av) {
        txtTitulo.setText(av.getTitulo());
        txtDescripcion.setText(av.getDescripcion());
        txtAnio.setText(av.getEstreno() > 0 ? String.valueOf(av.getEstreno()) : "");
        txtImdb.setText(String.valueOf(av.getCalificacion_IMDb()));

        seleccionarPorNombre(cboClasificacion, av.getClasificacion());
        seleccionarPorNombre(cboGenero, av.getGenero());

        switch (av) {
            case Pelicula p -> {
                cboTipo.setSelectedItem(Protocolo.TIPO_PELICULA);
                txtPelDuracion.setText(String.valueOf(p.getDuracion()));
                txtPelDirector.setText(p.getDirector());
                txtPelEstudio.setText(p.getEstudio());
            }
            case Documental d -> {
                cboTipo.setSelectedItem(Protocolo.TIPO_DOCUMENTAL);
                txtDocDuracion.setText(String.valueOf(d.getDuracion()));
                txtDocDirector.setText(d.getDirector());
                txtDocTema.setText(d.getTema());
            }
            case Serie s -> {
                cboTipo.setSelectedItem(Protocolo.TIPO_SERIE);
                txtSerTemporadas.setText(String.valueOf(s.getNumTemporadas()));
                txtSerEpisodios.setText(String.valueOf(s.getNumEpisodios()));
                txtSerEstado.setText(s.getEstado());
            }
            default -> { }
        }
        cboTipo.setEnabled(false);
        cboTipo.setToolTipText("El tipo no se puede cambiar después de creada la obra.");
    }

    private void seleccionarPorNombre(JComboBox<ItemCatalogo> combo, String nombre) {
        if (nombre == null) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (nombre.equals(combo.getItemAt(i).nombre())) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // GUARDAR
    // ═════════════════════════════════════════════════════════════════

    private void guardar() {
        // ── Validacion de comodidad (el servidor revalida) ───────────
        String titulo = txtTitulo.getText().trim();
        if (titulo.isEmpty()) {
            aviso("El título es obligatorio.");
            txtTitulo.requestFocus();
            return;
        }

        Integer anio = enteroValido(txtAnio.getText(), "año de estreno");
        if (anio == null) {
            return;
        }
        if (anio != 0 && (anio < 1888 || anio > 2200)) {
            // El mismo rango que el CHECK ck_anio de la tabla. Avisarlo
            // aqui evita un viaje de red para recibir un rechazo.
            aviso("El año debe estar entre 1888 y 2200.");
            return;
        }

        Double imdb = decimalValido(txtImdb.getText());
        if (imdb == null) {
            return;
        }
        if (imdb < 0 || imdb > 10) {
            aviso("La calificación IMDb debe estar entre 0 y 10.");
            return;
        }

        ItemCatalogo clasif = (ItemCatalogo) cboClasificacion.getSelectedItem();
        ItemCatalogo genero = (ItemCatalogo) cboGenero.getSelectedItem();

        String tipo = String.valueOf(cboTipo.getSelectedItem());
        String descripcion = txtDescripcion.getText();

        // ── Construccion del objeto de dominio ───────────────────────
        Audiovisual av;
        try {
            av = switch (tipo) {
                case Protocolo.TIPO_PELICULA -> new Pelicula(titulo, descripcion,
                        anio, texto(clasif), imdb, texto(genero),
                        obligatorioEntero(txtPelDuracion, "duración"),
                        txtPelDirector.getText().trim(),
                        txtPelEstudio.getText().trim());

                case Protocolo.TIPO_DOCUMENTAL -> new Documental(titulo, descripcion,
                        anio, texto(clasif), imdb, texto(genero),
                        obligatorioEntero(txtDocDuracion, "duración"),
                        txtDocDirector.getText().trim(),
                        txtDocTema.getText().trim());

                default -> new Serie(titulo, descripcion,
                        anio, texto(clasif), imdb, texto(genero),
                        obligatorioEntero(txtSerTemporadas, "temporadas"),
                        obligatorioEntero(txtSerEpisodios, "episodios"),
                        txtSerEstado.getText().trim());
            };
        } catch (NumberFormatException ex) {
            aviso(ex.getMessage());
            return;
        }

        // ── Envio ────────────────────────────────────────────────────
        try {
            String peticion;
            if (original == null) {
                peticion = CodificadorAudiovisual.aPeticionCrear(av);
            } else {
                av.setId(original.getId());
                peticion = CodificadorAudiovisual.aPeticionActualizar(av);
            }

            String respuesta = ClienteFideflix.enviarPeticion(peticion);
            String[] partes = respuesta.split(Protocolo.SEPARADOR_REGEX, -1);

            switch (partes[0]) {
                case Protocolo.RSP_OK -> {
                    JOptionPane.showMessageDialog(this,
                            original == null
                                ? "Obra creada correctamente (código " + partes[1] + ")."
                                : "Cambios guardados.",
                            "Fideflix", JOptionPane.INFORMATION_MESSAGE);
                    if (alGuardar != null) {
                        alGuardar.run();      // refresca el catalogo
                    }
                    dispose();
                }
                case Protocolo.RSP_DUPLICADO ->
                    aviso("Ya existe una obra con ese título y año.");

                case Protocolo.RSP_NO_ENCONTRADO ->
                    // Otro cliente la borro mientras esta ventana estaba
                    // abierta. Es el estado compartido haciendose visible.
                    aviso("La obra ya no existe. Otro usuario pudo haberla eliminado.");

                default ->
                    JOptionPane.showMessageDialog(this,
                            "El servidor reportó un error:\n" + respuesta,
                            "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo conectar con el servidor.\n\nDetalle: " + ex.getMessage(),
                    "Sin conexión", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // VALIDACIONES
    // ═════════════════════════════════════════════════════════════════

    private String texto(ItemCatalogo item) {
        return item == null ? "" : item.nombre();
    }

    /* Campo opcional: vacio se acepta como 0 (que el DAO traduce a NULL). */
    private Integer enteroValido(String texto, String nombreCampo) {
        String t = texto.trim();
        if (t.isEmpty()) {
            return 0;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            aviso("El campo \"" + nombreCampo + "\" debe ser un número entero.");
            return null;
        }
    }

    private Double decimalValido(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) {
            return 0.0;
        }
        try {
            // Se acepta coma decimal: en Costa Rica es lo natural de
            // escribir, y rechazarlo seria pedirle al usuario que se
            // adapte al programa en vez de al reves.
            return Double.valueOf(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            aviso("La calificación IMDb debe ser un número (por ejemplo 8.5).");
            return null;
        }
    }

    private int obligatorioEntero(JTextField campo, String nombre) {
        String t = campo.getText().trim();
        if (t.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    "El campo \"" + nombre + "\" debe ser un número entero.");
        }
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos",
                JOptionPane.WARNING_MESSAGE);
    }
}
