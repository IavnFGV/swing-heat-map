package io.drozda.coding.demo;

import javax.swing.*;
import java.awt.*;

public class InfoFrame extends JFrame {

    private final JTextArea textArea = new JTextArea();

    public InfoFrame() {
        super("Debug info");

        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setEditable(false);

        setContentPane(new JScrollPane(textArea));
        setSize(420, 500);
        setLocation(1250, 100);
    }

    public void updateText(String text) {
        textArea.setText(text);
    }
}