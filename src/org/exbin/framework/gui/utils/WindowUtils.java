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
package org.exbin.framework.gui.utils;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.JTextComponent;
import org.exbin.framework.gui.utils.handler.OkCancelService;
import org.exbin.framework.gui.utils.panel.WindowHeaderPanel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;

/**
 * Utility static methods usable for windows and dialogs.
 *
 * @version 0.2.1 2019/07/14
 * @author ExBin Project (http://exbin.org)
 */
@ParametersAreNonnullByDefault
public class WindowUtils {

    private static final int BUTTON_CLICK_TIME = 150;
    private static LookAndFeel lookAndFeel = null;

    private WindowUtils() {
    }

    @Nonnull
    public static WindowHeaderPanel addHeaderPanel(Window window, Class<?> resourceClass, ResourceBundle resourceBundle) {
        URL iconUrl = resourceClass.getResource(resourceBundle.getString("header.icon"));
        Icon headerIcon = iconUrl != null ? new ImageIcon(iconUrl) : null;
        return  addHeaderPanel(window, resourceBundle.getString("header.title"), resourceBundle.getString("header.description"), headerIcon);
    }

    @Nonnull
    public static WindowHeaderPanel addHeaderPanel(Window window, String headerTitle, String headerDescription, @Nullable Icon headerIcon) {
        WindowHeaderPanel headerPanel = new WindowHeaderPanel();
        headerPanel.setTitle(headerTitle);
        headerPanel.setDescription(headerDescription);
        if (headerIcon != null) {
            headerPanel.setIcon(headerIcon);
        }
        if (window instanceof WindowHeaderPanel.WindowHeaderDecorationProvider) {
            ((WindowHeaderPanel.WindowHeaderDecorationProvider) window).setHeaderDecoration(headerPanel);
        } else {
            Frame frame = getFrame(window);
            if (frame instanceof WindowHeaderPanel.WindowHeaderDecorationProvider) {
                ((WindowHeaderPanel.WindowHeaderDecorationProvider) frame).setHeaderDecoration(headerPanel);
            }
        }
        int height = window.getHeight() + headerPanel.getPreferredSize().height;
        ((JDialog) window).getContentPane().add(headerPanel, java.awt.BorderLayout.PAGE_START);
        window.setSize(window.getWidth(), height);
        return headerPanel;
    }

    public static void invokeWindow(final Window window) {
        if (lookAndFeel != null) {
            try {
                javax.swing.UIManager.setLookAndFeel(lookAndFeel);
            } catch (UnsupportedLookAndFeelException ex) {
                Logger.getLogger(WindowUtils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        java.awt.EventQueue.invokeLater(() -> {
            if (window instanceof JDialog) {
                ((JDialog) window).setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
            }

            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    System.exit(0);
                }
            });
            window.setVisible(true);
        });
    }

    @Nonnull
    public static DialogWrapper createDialog(final JComponent component, Component parent, String dialogTitle, Dialog.ModalityType modalityType) {
        DialogDescriptor dialogDescriptor = new DialogDescriptor(component, dialogTitle, modalityType != Dialog.ModalityType.MODELESS, new Object[0], null, 0, null, null);
        final Dialog dialog = DialogDisplayer.getDefault().createDialog(dialogDescriptor);
        Dimension size = component.getPreferredSize();
        dialog.add(component);
        dialog.setSize(size.width + 8, size.height + 24);
        dialog.setTitle(dialogTitle);
        if (component instanceof OkCancelService) {
            assignGlobalKeyListener(dialog, ((OkCancelService) component).getOkCancelListener());
        }
        return new DialogWrapper() {
            @Override
            public void show() {
                dialog.setVisible(true);
            }

            @Override
            public void showCentered(@Nullable Component component) {
                center(component);
                show();
            }

            @Override
            public void close() {
                closeWindow(dialog);
            }

            @Override
            public void dispose() {
                dialog.dispose();
            }

            @Override
            public Window getWindow() {
                return dialog;
            }

            @Override
            public Container getParent() {
                return dialog.getParent();
            }

            @Override
            public void center(@Nullable Component component) {
                if (component == null) {
                    center();
                } else {
                    dialog.setLocationRelativeTo(component);
                }
            }

            @Override
            public void center() {
                dialog.setLocationByPlatform(true);
            }
        };
    }

    @Nonnull
    public static JDialog createDialog(final JComponent component) {
        JDialog dialog = new JDialog();
        Dimension size = component.getPreferredSize();
        dialog.add(component);
        dialog.setSize(size.width + 8, size.height + 24);
        if (component instanceof OkCancelService) {
            assignGlobalKeyListener(dialog, ((OkCancelService) component).getOkCancelListener());
        }
        return dialog;
    }

    public static void invokeDialog(final JComponent component) {
        JDialog dialog = createDialog(component);
        invokeWindow(dialog);
    }

    public static LookAndFeel getLookAndFeel() {
        return lookAndFeel;
    }

    public static void setLookAndFeel(LookAndFeel lookAndFeel) {
        WindowUtils.lookAndFeel = lookAndFeel;
    }

    public static void closeWindow(Window window) {
        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
    }

    @Nonnull
    public static JDialog createBasicDialog() {
        JDialog dialog = new JDialog(new javax.swing.JFrame(), true);
        dialog.setSize(640, 480);
        dialog.setLocationByPlatform(true);
        return dialog;
    }

    /**
     * Find frame component for given component.
     *
     * @param component instantiated component
     * @return frame instance if found
     */
    @Nullable
    public static Frame getFrame(Component component) {
        Window parentComponent = SwingUtilities.getWindowAncestor(component);
        while (!(parentComponent == null || parentComponent instanceof Frame)) {
            parentComponent = SwingUtilities.getWindowAncestor(parentComponent);
        }
        if (parentComponent == null) {
            parentComponent = JOptionPane.getRootFrame();
        }
        return (Frame) parentComponent;
    }

    @Nullable
    public static Window getWindow(Component component) {
        return SwingUtilities.getWindowAncestor(component);
    }

    /**
     * Assign ESCAPE/ENTER key for all focusable components recursively.
     *
     * @param component target component
     * @param closeButton button which will be used for closing operation
     */
    public static void assignGlobalKeyListener(Component component, final JButton closeButton) {
        assignGlobalKeyListener(component, closeButton, closeButton);
    }

    /**
     * Assign ESCAPE/ENTER key for all focusable components recursively.
     *
     * @param component target component
     * @param okButton button which will be used for default ENTER
     * @param cancelButton button which will be used for closing operation
     */
    public static void assignGlobalKeyListener(Component component, final JButton okButton, final JButton cancelButton) {
        assignGlobalKeyListener(component, new OkCancelListener() {
            @Override
            public void okEvent() {
                doButtonClick(okButton);
            }

            @Override
            public void cancelEvent() {
                doButtonClick(cancelButton);
            }
        });
    }

    /**
     * Assign ESCAPE/ENTER key for all focusable components recursively.
     *
     * @param component target component
     * @param listener ok and cancel event listener
     */
    public static void assignGlobalKeyListener(Component component, @Nullable final OkCancelListener listener) {
        JRootPane rootPane = SwingUtilities.getRootPane(component);
        final String ESC_CANCEL = "esc-cancel";
        final String ENTER_OK = "enter-ok";
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ESC_CANCEL);
        rootPane.getActionMap().put(ESC_CANCEL, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (listener == null) {
                    return;
                }

                boolean performCancelAction = true;

                Window window = SwingUtilities.getWindowAncestor(event.getSource() instanceof JRootPane ? (JRootPane) event.getSource() : rootPane);
                if (window != null) {
                    Component focusOwner = window.getFocusOwner();
                    if (focusOwner instanceof JComboBox) {
                        performCancelAction = !((JComboBox) focusOwner).isPopupVisible();
                    } else if (focusOwner instanceof JRootPane) {
                        // Ignore in popup menus
                        // performCancelAction = false;
                    }
                }

                if (performCancelAction) {
                    listener.cancelEvent();
                }
            }
        });

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_OK);
        rootPane.getActionMap().put(ENTER_OK, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (listener == null) {
                    return;
                }

                boolean performOkAction = true;

                Window window = SwingUtilities.getWindowAncestor(event.getSource() instanceof JRootPane ? (JRootPane) event.getSource() : rootPane);
                if (window != null) {
                    Component focusOwner = window.getFocusOwner();
                    if (focusOwner instanceof JTextArea || focusOwner instanceof JEditorPane) {
                        performOkAction = !((JTextComponent) focusOwner).isEditable();
                    }
                }

                if (performOkAction) {
                    listener.okEvent();
                }
            }
        });
    }

    /**
     * Performs visually visible click on the button component.
     *
     * @param button button component
     */
    public static void doButtonClick(JButton button) {
        button.doClick(BUTTON_CLICK_TIME);
    }

    @Nonnull
    public static WindowPosition getWindowPosition(Window window) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screenDevices = ge.getScreenDevices();
        int windowX = window.getX();
        int windowY = window.getY();
        int screenX = 0;
        int screenY = 0;
        int screenWidth = 0;
        int screenHeight = 0;
        int screenIndex = 0;
        for (GraphicsDevice screen : screenDevices) {
            Rectangle bounds = screen.getDefaultConfiguration().getBounds();
            if (bounds.contains(windowX, windowY)) {
                screenX = bounds.x;
                screenY = bounds.y;
                screenWidth = bounds.width;
                screenHeight = bounds.height;
                break;
            }
            screenIndex++;
        }
        WindowPosition position = new WindowPosition();
        position.setScreenIndex(screenIndex);
        position.setScreenWidth(screenWidth);
        position.setScreenHeight(screenHeight);
        position.setRelativeX(window.getX() - screenX);
        position.setRelativeY(window.getY() - screenY);
        position.setWidth(window.getWidth());
        position.setHeight(window.getHeight());
        position.setMaximized(window instanceof Frame ? (((Frame) window).getExtendedState() & JFrame.MAXIMIZED_BOTH) > 0 : false);
        return position;
    }

    public static void setWindowPosition(Window window, WindowPosition position) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screenDevices = ge.getScreenDevices();
        GraphicsDevice device;
        if (screenDevices.length > position.getScreenIndex()) {
            device = screenDevices[position.getScreenIndex()];
        } else {
            device = ge.getDefaultScreenDevice();
        }
        Rectangle screenBounds = device.getDefaultConfiguration().getBounds();
        double absoluteX = position.getScreenWidth() > 0
                ? screenBounds.x + position.getRelativeX() * screenBounds.width / position.getScreenWidth()
                : screenBounds.x + position.getRelativeX();
        double absoluteY = position.getScreenHeight() > 0
                ? screenBounds.y + position.getRelativeY() * screenBounds.height / position.getScreenHeight()
                : screenBounds.y + position.getRelativeY();
        double widthX = position.getScreenWidth() > 0
                ? position.getWidth() * screenBounds.width / position.getScreenWidth()
                : position.getWidth();
        double widthY = position.getScreenHeight() > 0
                ? position.getHeight() * screenBounds.height / position.getScreenHeight()
                : position.getHeight();
        if (position.isMaximized()) {
            window.setLocation((int) absoluteX, (int) absoluteY);
            if (window instanceof Frame) {
                ((Frame) window).setExtendedState(JFrame.MAXIMIZED_BOTH);
            } else {
                // TODO if (window instanceof JDialog) 
            }
        } else {
            window.setBounds((int) absoluteX, (int) absoluteY, (int) widthX, (int) widthY);
        }
    }

    /**
     * Creates panel for given main and control panel.
     *
     * @param mainPanel main panel
     * @param controlPanel control panel
     * @return panel
     */
    @Nonnull
    public static JPanel createDialogPanel(JPanel mainPanel, JPanel controlPanel) {
        JPanel dialogPanel;
        if (controlPanel instanceof OkCancelService) {
            dialogPanel = new DialogPanel((OkCancelService) controlPanel);
        } else {
            dialogPanel = new JPanel(new BorderLayout());
        }
        dialogPanel.add(mainPanel, BorderLayout.CENTER);
        dialogPanel.add(controlPanel, BorderLayout.SOUTH);
        Dimension mainPreferredSize = mainPanel.getPreferredSize();
        Dimension controlPreferredSize = controlPanel.getPreferredSize();
        dialogPanel.setPreferredSize(new Dimension(mainPreferredSize.width, mainPreferredSize.height + controlPreferredSize.height));
        return dialogPanel;
    }

    @ParametersAreNonnullByDefault
    private static final class DialogPanel extends JPanel implements OkCancelService {

        private final OkCancelService okCancelService;

        public DialogPanel(OkCancelService okCancelService) {
            super(new BorderLayout());
            this.okCancelService = okCancelService;
        }

        @Nonnull
        @Override
        public OkCancelListener getOkCancelListener() {
            return okCancelService.getOkCancelListener();
        }
    }

    @ParametersAreNonnullByDefault
    public interface DialogWrapper {

        void show();

        void showCentered(@Nullable Component window);

        void close();

        void dispose();

        @Nonnull
        Window getWindow();

        @Nonnull
        Container getParent();

        void center(@Nullable Component window);

        void center();
    }
}
