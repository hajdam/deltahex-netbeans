/*
 * Copyright (C) ExBin Project
 *
 * This application or library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This application or library is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along this application.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.exbin.framework.bined.panel;

import org.exbin.framework.bined.SearchCondition;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ResourceBundle;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import org.exbin.bined.swing.extended.ExtCodeArea;
import org.exbin.framework.bined.handler.CodeAreaPopupMenuHandler;
import org.exbin.framework.gui.utils.LanguageUtils;
import org.exbin.framework.gui.utils.WindowUtils;
import org.exbin.utils.binary_data.EditableBinaryData;

/**
 * Multiline search condition editor panel.
 *
 * @version 0.2.1 2019/06/17
 * @author ExBin Project (http://exbin.org)
 */
public class BinaryMultilinePanel extends javax.swing.JPanel {

    private static final String POPUP_MENU_POSTFIX = ".hexMultilineDialog";

    private final java.util.ResourceBundle resourceBundle = LanguageUtils.getResourceBundleByClass(BinaryMultilinePanel.class);
    private SearchCondition condition;

    private JTextArea textArea;
    private JScrollPane scrollPane;
    private ExtCodeArea codeArea;
    private CodeAreaPopupMenuHandler codeAreaPopupMenuHandler;

    public BinaryMultilinePanel() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setPreferredSize(new java.awt.Dimension(400, 300));
        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Test method for this panel.
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        WindowUtils.invokeDialog(new BinaryMultilinePanel());
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    public String getMultilineText() {
        return textArea.getText();
    }

    public SearchCondition getCondition() {
        if (condition.getSearchMode() == SearchCondition.SearchMode.TEXT) {
            condition.setSearchText(textArea.getText());
        } else {
            condition.setBinaryData((EditableBinaryData) codeArea.getContentData());
        }

        return condition;
    }

    public void setCondition(SearchCondition condition) {
        this.condition = condition;
        if (condition.getSearchMode() == SearchCondition.SearchMode.TEXT) {
            scrollPane = new javax.swing.JScrollPane();
            textArea = new javax.swing.JTextArea();
            textArea.setColumns(20);
            textArea.setRows(5);
            textArea.setName("textArea"); // NOI18N
            scrollPane.setViewportView(textArea);

            textArea.setText(condition.getSearchText());
            add(scrollPane, BorderLayout.CENTER);
        } else {
            codeArea = new ExtCodeArea();
            codeArea.setContentData(condition.getBinaryData());
            add(codeArea, BorderLayout.CENTER);
            if (codeAreaPopupMenuHandler != null) {
                codeArea.setComponentPopupMenu(new JPopupMenu() {
                    @Override
                    public void show(Component invoker, int x, int y) {
                        int clickedX = x;
                        int clickedY = y;
                        if (invoker instanceof JViewport) {
                            clickedX += ((JViewport) invoker).getParent().getX();
                            clickedY += ((JViewport) invoker).getParent().getY();
                        }
                        JPopupMenu popupMenu = codeAreaPopupMenuHandler.createPopupMenu(codeArea, POPUP_MENU_POSTFIX, clickedX, clickedY);
                        popupMenu.show(invoker, x, y);
                    }
                });
            }
        }
        revalidate();
    }

    public void setCodeAreaPopupMenuHandler(CodeAreaPopupMenuHandler codeAreaPopupMenuHandler) {
        this.codeAreaPopupMenuHandler = codeAreaPopupMenuHandler;
    }

    public void detachMenu() {
        if (condition.getSearchMode() == SearchCondition.SearchMode.BINARY) {
            codeAreaPopupMenuHandler.dropPopupMenu(POPUP_MENU_POSTFIX);
        }
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
}
