/*
 * Placeholder JTextField for Gene ID input
 * Provides dynamic placeholder text that changes based on selected species
 */
package simplegenomehub.gui;

import javax.swing.JTextField;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.Color;
import java.awt.Font;

/**
 * Custom JTextField with placeholder functionality
 *
 * @author SimpleGenomeHub
 */
public class PlaceholderTextField extends JTextField implements FocusListener {

    private String placeholder;
    private boolean showingPlaceholder;
    private Color placeholderColor = Color.GRAY;
    private Color foregroundColor;

    /**
     * Constructor
     */
    public PlaceholderTextField(int columns) {
        super(columns);
        this.foregroundColor = getForeground();
        addFocusListener(this);
    }

    /**
     * Set placeholder text
     */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        if (showingPlaceholder || getText().isEmpty()) {
            showPlaceholder();
        }
    }

    /**
     * Get placeholder text
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Check if currently showing placeholder
     */
    public boolean isShowingPlaceholder() {
        return showingPlaceholder;
    }

    @Override
    public void setText(String text) {
        if (text == null || text.isEmpty()) {
            showPlaceholder();
        } else {
            super.setText(text);
            setForeground(foregroundColor);
            showingPlaceholder = false;
        }
    }

    @Override
    public String getText() {
        if (showingPlaceholder) {
            return "";
        }
        return super.getText();
    }

    /**
     * Show placeholder text
     */
    private void showPlaceholder() {
        if (placeholder != null && !placeholder.isEmpty()) {
            super.setText(placeholder);
            setForeground(placeholderColor);
            showingPlaceholder = true;
        }
    }

    /**
     * Hide placeholder text
     */
    private void hidePlaceholder() {
        if (showingPlaceholder) {
            super.setText("");
            setForeground(foregroundColor);
            showingPlaceholder = false;
        }
    }

    @Override
    public void focusGained(FocusEvent e) {
        hidePlaceholder();
    }

    @Override
    public void focusLost(FocusEvent e) {
        if (getText().isEmpty()) {
            showPlaceholder();
        }
    }

    /**
     * Set placeholder color
     */
    public void setPlaceholderColor(Color color) {
        this.placeholderColor = color;
    }

    /**
     * Get placeholder color
     */
    public Color getPlaceholderColor() {
        return placeholderColor;
    }
}