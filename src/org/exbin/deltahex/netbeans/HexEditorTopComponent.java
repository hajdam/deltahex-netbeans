/*
 * Copyright (C) ExBin Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exbin.deltahex.netbeans;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.exbin.deltahex.CaretMovedListener;
import org.exbin.deltahex.CaretPosition;
import org.exbin.deltahex.CodeType;
import org.exbin.deltahex.DataChangedListener;
import org.exbin.deltahex.EditationAllowed;
import org.exbin.deltahex.EditationMode;
import org.exbin.deltahex.EditationModeChangedListener;
import org.exbin.deltahex.Section;
import org.exbin.deltahex.highlight.swing.HighlightCodeAreaPainter;
import org.exbin.deltahex.netbeans.panel.HexSearchPanel;
import org.exbin.deltahex.netbeans.panel.HexSearchPanelApi;
import org.exbin.deltahex.operation.swing.CodeCommandHandler;
import org.exbin.deltahex.swing.CodeArea;
import org.exbin.utils.binary_data.PagedData;
import org.exbin.deltahex.operation.BinaryDataCommand;
import org.exbin.deltahex.operation.undo.BinaryDataUndoUpdateListener;
import org.exbin.framework.deltahex.HexStatusApi;
import org.exbin.framework.deltahex.panel.HexStatusPanel;
import org.exbin.framework.deltahex.panel.ReplaceParameters;
import org.exbin.framework.deltahex.panel.SearchCondition;
import org.exbin.framework.deltahex.panel.SearchParameters;
import org.exbin.framework.editor.text.TextEncodingStatusApi;
import org.exbin.utils.binary_data.BinaryData;
import org.exbin.utils.binary_data.EditableBinaryData;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.UndoRedo;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Hexadecimal editor top component.
 *
 * @version 0.1.4 2016/12/31
 * @author ExBin Project (http://exbin.org)
 */
@ConvertAsProperties(dtd = "-//org.exbin.deltahex//HexEditor//EN", autostore = false)
@TopComponent.Description(preferredID = "HexEditorTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@TopComponent.OpenActionRegistration(displayName = "#CTL_HexEditorAction", preferredID = "HexEditorTopComponent")
public final class HexEditorTopComponent extends TopComponent implements UndoRedo.Provider {

    private final HexEditorNode node;
    private final CodeArea codeArea;
    private final UndoRedo.Manager undoRedo;
    private final HexUndoSwingHandler undoHandler;
    private final Savable savable;
    private final InstanceContent content = new InstanceContent();
    private final int metaMask;

    private HexStatusPanel statusPanel;
    private HexStatusApi hexStatus;
    private TextEncodingStatusApi encodingStatus;
    private CharsetChangeListener charsetChangeListener = null;
    private GoToHandler goToHandler;
    private EncodingsHandler encodingsHandler;
    private SearchHandler searchHandler;
    private boolean findTextPanelVisible = false;
    private HexSearchPanel hexSearchPanel = null;

    private boolean opened = false;
    private boolean modified = false;
    protected String displayName;
    private long documentOriginalSize;

    public HexEditorTopComponent() {
        initComponents();

        codeArea = new CodeArea();
        codeArea.setPainter(new HighlightCodeAreaPainter(codeArea));
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codeArea.getCaret().setBlinkRate(300);

        undoRedo = new UndoRedo.Manager();
        undoHandler = new HexUndoSwingHandler(codeArea, undoRedo);

        codeArea.setData(new PagedData());
        CodeCommandHandler commandHandler = new CodeCommandHandler(codeArea, undoHandler);
        codeArea.setCommandHandler(commandHandler);
        super.add(codeArea, BorderLayout.CENTER);
        statusPanel = new HexStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
        registerHexStatus(statusPanel);
        registerEncodingStatus(statusPanel);
        goToHandler = new GoToHandler(codeArea);
        encodingsHandler = new EncodingsHandler(encodingStatus);
        searchHandler = new SearchHandler();

        codeArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu popupMenu = createContextMenu();
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        node = new HexEditorNode(codeArea);
        content.add(node);
        savable = new Savable(this, codeArea);

        setActivatedNodes(new Node[]{node});

        undoHandler.addUndoUpdateListener(new BinaryDataUndoUpdateListener() {
            @Override
            public void undoCommandPositionChanged() {
                codeArea.repaint();
                updateCurrentDocumentSize();
                updateModified();
            }

            @Override
            public void undoCommandAdded(final BinaryDataCommand command) {
                updateCurrentDocumentSize();
                updateModified();
            }
        });

        codeArea.addDataChangedListener(new DataChangedListener() {
            @Override
            public void dataChanged() {
//                if (hexSearchPanel.isVisible()) {
//                    hexSearchPanel.dataChanged();
//                }
                updateCurrentDocumentSize();
            }
        });

        setName(NbBundle.getMessage(HexEditorTopComponent.class, "CTL_HexEditorTopComponent"));
        setToolTipText(NbBundle.getMessage(HexEditorTopComponent.class, "HINT_HexEditorTopComponent"));

        codeTypeComboBox.setSelectedIndex(codeArea.getCodeType().ordinal());

        getActionMap().put("copy-to-clipboard", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.copy();
            }
        });
        getActionMap().put("cut-to-clipboard", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.cut();
            }
        });
        getActionMap().put("paste-from-clipboard", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.paste();
            }
        });

        int metaMaskValue;
        try {
            metaMaskValue = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        } catch (java.awt.HeadlessException ex) {
            metaMaskValue = java.awt.Event.CTRL_MASK;
        }
        metaMask = metaMaskValue;

        associateLookup(new AbstractLookup(content));
    }

    public void registerHexStatus(HexStatusApi hexStatusApi) {
        this.hexStatus = hexStatusApi;
        codeArea.addCaretMovedListener(new CaretMovedListener() {
            @Override
            public void caretMoved(CaretPosition caretPosition, Section section) {
                String position = String.valueOf(caretPosition.getDataPosition());
                position += ":" + caretPosition.getCodeOffset();
                hexStatus.setCursorPosition(position);
            }
        });

        codeArea.addEditationModeChangedListener(new EditationModeChangedListener() {
            @Override
            public void editationModeChanged(EditationMode mode) {
                hexStatus.setEditationMode(mode);
            }
        });
        hexStatus.setEditationMode(codeArea.getEditationMode());

        hexStatus.setControlHandler(new HexStatusApi.StatusControlHandler() {
            @Override
            public void changeEditationMode(EditationMode editationMode) {
                codeArea.setEditationMode(editationMode);
            }

            @Override
            public void changeCursorPosition() {
                goToHandler.getGoToLineAction().actionPerformed(null);
            }

            @Override
            public void cycleEncodings() {
                if (encodingsHandler != null) {
                    encodingsHandler.cycleEncodings();
                }
            }

            @Override
            public void popupEncodingsMenu(MouseEvent mouseEvent) {
                if (encodingsHandler != null) {
                    encodingsHandler.popupEncodingsMenu(mouseEvent);
                }
            }
        });
    }

    public void registerEncodingStatus(TextEncodingStatusApi encodingStatusApi) {
        this.encodingStatus = encodingStatusApi;
        setCharsetChangeListener(new CharsetChangeListener() {
            @Override
            public void charsetChanged() {
                encodingStatus.setEncoding(codeArea.getCharset().name());
            }
        });
    }

    public void setCharsetChangeListener(CharsetChangeListener charsetChangeListener) {
        this.charsetChangeListener = charsetChangeListener;
    }

    @Override
    public boolean canClose() {
        if (!modified) {
            return true;
        }

        final Component parent = WindowManager.getDefault().getMainWindow();
        final Object[] options = new Object[]{"Save", "Discard", "Cancel"};
        final String message = "File " + displayName + " is modified. Save?";
        final int choice = JOptionPane.showOptionDialog(parent, message, "Question", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, JOptionPane.YES_OPTION);
        if (choice == JOptionPane.CANCEL_OPTION) {
            return false;
        }

        if (choice == JOptionPane.YES_OPTION) {
            try {
                savable.handleSave();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return true;
    }

    void setModified(boolean modified) {
        this.modified = modified;
        final String htmlDisplayName;
        if (modified && opened) {
            savable.activate();
            content.add(savable);
            htmlDisplayName = "<html><b>" + displayName + "</b></html>";
        } else {
            savable.deactivate();
            content.remove(savable);
            htmlDisplayName = displayName;
        }

        if (SwingUtilities.isEventDispatchThread()) {
            setHtmlDisplayName(htmlDisplayName);
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        setHtmlDisplayName(htmlDisplayName);
                    }
                });
            } catch (InterruptedException | InvocationTargetException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public void openDataObject(DataObject dataObject) {
        displayName = dataObject.getPrimaryFile().getNameExt();
        setHtmlDisplayName(displayName);
        node.openFile(dataObject);
        savable.setDataObject(dataObject);
        opened = true;
        documentOriginalSize = codeArea.getDataSize();
        updateCurrentDocumentSize();
        updateCurrentMemoryMode();

        final Charset charset = Charset.forName(FileEncodingQuery.getEncoding(dataObject.getPrimaryFile()).name());
        if (charsetChangeListener != null) {
            charsetChangeListener.charsetChanged();
        }
        codeArea.setCharset(charset);
    }

    public void saveDataObject(DataObject dataObject) throws IOException {
        OutputStream stream = dataObject.getPrimaryFile().getOutputStream();
        try {
            codeArea.getData().saveToStream(stream);
            stream.flush();
            setModified(false);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            if (null != stream) {
                stream.close();
            }
        }

        documentOriginalSize = codeArea.getDataSize();
        updateCurrentDocumentSize();
        updateCurrentMemoryMode();
    }

    @Override
    public UndoRedo getUndoRedo() {
        return undoRedo;
    }

    public void updatePosition() {
        // hexSearchPanel.updatePosition(codeArea.getCaretPosition().getDataPosition(), codeArea.getDataSize());
    }

    private void updateCurrentDocumentSize() {
        long dataSize = codeArea.getData().getDataSize();
        long difference = dataSize - documentOriginalSize;
        hexStatus.setCurrentDocumentSize(dataSize + " (" + (difference > 0 ? "+" + difference : difference) + ")");
    }

    private void updateCurrentMemoryMode() {
        String memoryMode = "M";
        if (codeArea.getEditationAllowed() == EditationAllowed.READ_ONLY) {
            memoryMode = "R";
        }
//        else if (segmentsRepository != null) {
//            memoryMode = "\u0394";
//        }

        hexStatus.setMemoryMode(memoryMode);
    }

    private void updateModified() {
        setModified(undoHandler.getSyncPoint() != undoHandler.getCommandPosition());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        infoToolbar = new javax.swing.JPanel();
        controlToolBar = new javax.swing.JToolBar();
        lineWrappingToggleButton = new javax.swing.JToggleButton();
        showUnprintablesToggleButton = new javax.swing.JToggleButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        codeTypeComboBox = new javax.swing.JComboBox<>();

        setLayout(new java.awt.BorderLayout());

        infoToolbar.setLayout(new java.awt.BorderLayout());

        controlToolBar.setBorder(null);
        controlToolBar.setFloatable(false);
        controlToolBar.setRollover(true);

        lineWrappingToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/exbin/deltahex/netbeans/resources/icons/deltahex-linewrap.png"))); // NOI18N
        lineWrappingToggleButton.setToolTipText(org.openide.util.NbBundle.getMessage(HexEditorTopComponent.class, "HexEditorTopComponent.lineWrappingToggleButton.toolTipText")); // NOI18N
        lineWrappingToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lineWrappingToggleButtonActionPerformed(evt);
            }
        });
        controlToolBar.add(lineWrappingToggleButton);

        showUnprintablesToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/exbin/deltahex/netbeans/resources/icons/insert-pilcrow.png"))); // NOI18N
        showUnprintablesToggleButton.setToolTipText(org.openide.util.NbBundle.getMessage(HexEditorTopComponent.class, "HexEditorTopComponent.showUnprintablesToggleButton.toolTipText")); // NOI18N
        showUnprintablesToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showUnprintablesToggleButtonActionPerformed(evt);
            }
        });
        controlToolBar.add(showUnprintablesToggleButton);
        controlToolBar.add(jSeparator1);

        codeTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "BIN", "OCT", "DEC", "HEX" }));
        codeTypeComboBox.setMaximumSize(new java.awt.Dimension(58, 25));
        codeTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeTypeComboBoxActionPerformed(evt);
            }
        });
        controlToolBar.add(codeTypeComboBox);

        infoToolbar.add(controlToolBar, java.awt.BorderLayout.CENTER);

        add(infoToolbar, java.awt.BorderLayout.NORTH);
    }// </editor-fold>//GEN-END:initComponents

    private void lineWrappingToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lineWrappingToggleButtonActionPerformed
        codeArea.setWrapMode(lineWrappingToggleButton.isSelected());
    }//GEN-LAST:event_lineWrappingToggleButtonActionPerformed

    private void showUnprintablesToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showUnprintablesToggleButtonActionPerformed
        codeArea.setShowUnprintableCharacters(showUnprintablesToggleButton.isSelected());
    }//GEN-LAST:event_showUnprintablesToggleButtonActionPerformed

    private void codeTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_codeTypeComboBoxActionPerformed
        codeArea.setCodeType(CodeType.values()[codeTypeComboBox.getSelectedIndex()]);
    }//GEN-LAST:event_codeTypeComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> codeTypeComboBox;
    private javax.swing.JToolBar controlToolBar;
    private javax.swing.JPanel infoToolbar;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToggleButton lineWrappingToggleButton;
    private javax.swing.JToggleButton showUnprintablesToggleButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        codeArea.requestFocus();
    }

    @Override
    public void componentClosed() {
        if (savable != null) {
            savable.deactivate();
        }
    }

    public void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    public void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

    private JPopupMenu createContextMenu() {
        final JPopupMenu result = new JPopupMenu();

        final JMenuItem cutMenuItem = new JMenuItem("Cut");
        cutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, metaMask));
        cutMenuItem.setEnabled(codeArea.hasSelection());
        cutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.cut();
                result.setVisible(false);
            }
        });
        result.add(cutMenuItem);

        final JMenuItem copyMenuItem = new JMenuItem("Copy");
        copyMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, metaMask));
        copyMenuItem.setEnabled(codeArea.hasSelection());
        copyMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.copy();
                result.setVisible(false);
            }
        });
        result.add(copyMenuItem);

        final JMenuItem copyAsCodeMenuItem = new JMenuItem("Copy as Code");
        copyAsCodeMenuItem.setEnabled(codeArea.hasSelection());
        copyAsCodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.copyAsCode();
                result.setVisible(false);
            }
        });
        result.add(copyAsCodeMenuItem);

        final JMenuItem pasteMenuItem = new JMenuItem("Paste");
        pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, metaMask));
        pasteMenuItem.setEnabled(codeArea.canPaste());
        pasteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.paste();
                result.setVisible(false);
            }
        });
        result.add(pasteMenuItem);

        final JMenuItem pasteFromCodeMenuItem = new JMenuItem("Paste from Code");
        pasteFromCodeMenuItem.setEnabled(codeArea.canPaste());
        pasteFromCodeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    codeArea.pasteFromCode();
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(codeArea, ex.getMessage(), "Unable to Paste Code", JOptionPane.ERROR_MESSAGE);
                }
                result.setVisible(false);
            }
        });
        result.add(pasteFromCodeMenuItem);

        final JMenuItem deleteMenuItem = new JMenuItem("Delete");
        deleteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteMenuItem.setEnabled(codeArea.hasSelection());
        deleteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.delete();
                result.setVisible(false);
            }
        });
        result.add(deleteMenuItem);
        result.addSeparator();

        final JMenuItem selectAllMenuItem = new JMenuItem("Select All");
        selectAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, metaMask));
        selectAllMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.selectAll();
                result.setVisible(false);
            }
        });
        result.add(selectAllMenuItem);
        result.addSeparator();

        final JMenuItem findMenuItem = new JMenuItem("Find...");
        findMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, metaMask));
        findMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSearchPanel(false);
            }
        });
        result.add(findMenuItem);

        final JMenuItem replaceMenuItem = new JMenuItem("Replace...");
        replaceMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, metaMask));
        replaceMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSearchPanel(true);
            }
        });
        result.add(replaceMenuItem);

        final JMenuItem goToMenuItem = new JMenuItem("Go To...");
        goToMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, metaMask));
        goToMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                goToHandler.getGoToLineAction().actionPerformed(null);
            }
        });
        result.add(goToMenuItem);

        return result;
    }

    public void showSearchPanel(boolean replace) {
        if (hexSearchPanel == null) {
            hexSearchPanel = new HexSearchPanel(new HexSearchPanelApi() {
                @Override
                public void performFind(SearchParameters searchParameters) {
                    HighlightCodeAreaPainter painter = (HighlightCodeAreaPainter) codeArea.getPainter();
                    SearchCondition condition = searchParameters.getCondition();
                    hexSearchPanel.clearStatus();
                    if (condition.isEmpty()) {
                        painter.clearMatches();
                        codeArea.repaint();
                        return;
                    }

                    long position;
                    if (searchParameters.isSearchFromCursor()) {
                        position = codeArea.getCaretPosition().getDataPosition();
                    } else {
                        switch (searchParameters.getSearchDirection()) {
                            case FORWARD: {
                                position = 0;
                                break;
                            }
                            case BACKWARD: {
                                position = codeArea.getDataSize() - 1;
                                break;
                            }
                            default:
                                throw new IllegalStateException("Illegal search type " + searchParameters.getSearchDirection().name());
                        }
                    }
                    searchParameters.setStartPosition(position);

                    switch (condition.getSearchMode()) {
                        case TEXT: {
                            searchForText(searchParameters);
                            break;
                        }
                        case BINARY: {
                            searchForBinaryData(searchParameters);
                            break;
                        }
                        default:
                            throw new IllegalStateException("Unexpected search mode " + condition.getSearchMode().name());
                    }
                }

                @Override
                public void setMatchPosition(int matchPosition) {
                    HighlightCodeAreaPainter painter = (HighlightCodeAreaPainter) codeArea.getPainter();
                    painter.setCurrentMatchIndex(matchPosition);
                    HighlightCodeAreaPainter.SearchMatch currentMatch = painter.getCurrentMatch();
                    codeArea.revealPosition(currentMatch.getPosition(), codeArea.getActiveSection());
                    codeArea.repaint();
                }

                @Override
                public void updatePosition() {
                    hexSearchPanel.updatePosition(codeArea.getCaretPosition().getDataPosition(), codeArea.getDataSize());
                }

                @Override
                public void performReplace(SearchParameters searchParameters, ReplaceParameters replaceParameters) {
                    SearchCondition replaceCondition = replaceParameters.getCondition();
                    HighlightCodeAreaPainter painter = (HighlightCodeAreaPainter) codeArea.getPainter();
                    HighlightCodeAreaPainter.SearchMatch currentMatch = painter.getCurrentMatch();
                    if (currentMatch != null) {
                        EditableBinaryData editableData = ((EditableBinaryData) codeArea.getData());
                        editableData.remove(currentMatch.getPosition(), currentMatch.getLength());
                        if (replaceCondition.getSearchMode() == SearchCondition.SearchMode.BINARY) {
                            editableData.insert(currentMatch.getPosition(), replaceCondition.getBinaryData());
                        } else {
                            editableData.insert(currentMatch.getPosition(), replaceCondition.getSearchText().getBytes(codeArea.getCharset()));
                        }
                        painter.getMatches().remove(currentMatch);
                        codeArea.repaint();
                    }
                }

                @Override
                public void clearMatches() {
                    HighlightCodeAreaPainter painter = (HighlightCodeAreaPainter) codeArea.getPainter();
                    painter.clearMatches();
                }
            });
        }

        if (!findTextPanelVisible) {
            add(hexSearchPanel, BorderLayout.SOUTH);
            revalidate();
            findTextPanelVisible = true;
            hexSearchPanel.requestSearchFocus();
        }
        hexSearchPanel.switchReplaceMode(replace);
    }

    public void hideSearchPanel() {
        if (findTextPanelVisible) {
            hexSearchPanel.cancelSearch();
            hexSearchPanel.clearSearch();
            remove(hexSearchPanel);
            revalidate();
            findTextPanelVisible = false;
        }
    }

    /**
     * Performs search by text/characters.
     */
    private void searchForText(SearchParameters searchParameters) {
        HighlightCodeAreaPainter painter = (HighlightCodeAreaPainter) codeArea.getPainter();
        SearchCondition condition = searchParameters.getCondition();

        long position = searchParameters.getStartPosition();
        String findText;
        if (searchParameters.isMatchCase()) {
            findText = condition.getSearchText();
        } else {
            findText = condition.getSearchText().toLowerCase();
        }
        BinaryData data = codeArea.getData();

        List<HighlightCodeAreaPainter.SearchMatch> foundMatches = new ArrayList<>();

        Charset charset = codeArea.getCharset();
        CharsetEncoder encoder = charset.newEncoder();
        int maxBytesPerChar = (int) encoder.maxBytesPerChar();
        byte[] charData = new byte[maxBytesPerChar];
        long dataSize = data.getDataSize();
        while (position <= dataSize - findText.length()) {
            int matchCharLength = 0;
            int matchLength = 0;
            while (matchCharLength < findText.length()) {
                long searchPosition = position + matchLength;
                int bytesToUse = maxBytesPerChar;
                if (position + bytesToUse > dataSize) {
                    bytesToUse = (int) (dataSize - position);
                }
                data.copyToArray(searchPosition, charData, 0, bytesToUse);
                char singleChar = new String(charData, charset).charAt(0);
                String singleCharString = String.valueOf(singleChar);
                int characterLength = singleCharString.getBytes(charset).length;

                if (searchParameters.isMatchCase()) {
                    if (singleChar != findText.charAt(matchCharLength)) {
                        break;
                    }
                } else if (singleCharString.toLowerCase().charAt(0) != findText.charAt(matchCharLength)) {
                    break;
                }
                matchCharLength++;
                matchLength += characterLength;
            }

            if (matchCharLength == findText.length()) {
                HighlightCodeAreaPainter.SearchMatch match = new HighlightCodeAreaPainter.SearchMatch();
                match.setPosition(position);
                match.setLength(matchLength);
                foundMatches.add(match);

                if (foundMatches.size() == 100 || !searchParameters.isMultipleMatches()) {
                    break;
                }
            }

            switch (searchParameters.getSearchDirection()) {
                case FORWARD: {
                    position++;
                    break;
                }
                case BACKWARD: {
                    position--;
                    break;
                }
                default:
                    throw new IllegalStateException("Illegal search type " + searchParameters.getSearchDirection().name());
            }
        }

        painter.setMatches(foundMatches);
        if (foundMatches.size() > 0) {
            painter.setCurrentMatchIndex(0);
            HighlightCodeAreaPainter.SearchMatch firstMatch = painter.getCurrentMatch();
            codeArea.revealPosition(firstMatch.getPosition(), codeArea.getActiveSection());
        }
        hexSearchPanel.setStatus(foundMatches.size(), 0);
        codeArea.repaint();
    }

    /**
     * Performs search by binary data.
     */
    private void searchForBinaryData(SearchParameters searchParameters) {
        HighlightCodeAreaPainter painter = (HighlightCodeAreaPainter) codeArea.getPainter();
        SearchCondition condition = searchParameters.getCondition();
        long position = codeArea.getCaretPosition().getDataPosition();
        HighlightCodeAreaPainter.SearchMatch currentMatch = painter.getCurrentMatch();

        if (currentMatch != null) {
            if (currentMatch.getPosition() == position) {
                position++;
            }
            painter.clearMatches();
        } else if (!searchParameters.isSearchFromCursor()) {
            position = 0;
        }

        BinaryData searchData = condition.getBinaryData();
        BinaryData data = codeArea.getData();

        List<HighlightCodeAreaPainter.SearchMatch> foundMatches = new ArrayList<>();

        long dataSize = data.getDataSize();
        while (position < dataSize - searchData.getDataSize()) {
            int matchLength = 0;
            while (matchLength < searchData.getDataSize()) {
                if (data.getByte(position + matchLength) != searchData.getByte(matchLength)) {
                    break;
                }
                matchLength++;
            }

            if (matchLength == searchData.getDataSize()) {
                HighlightCodeAreaPainter.SearchMatch match = new HighlightCodeAreaPainter.SearchMatch();
                match.setPosition(position);
                match.setLength(searchData.getDataSize());
                foundMatches.add(match);

                if (foundMatches.size() == 100 || !searchParameters.isMultipleMatches()) {
                    break;
                }
            }

            position++;
        }

        painter.setMatches(foundMatches);
        if (foundMatches.size() > 0) {
            painter.setCurrentMatchIndex(0);
            HighlightCodeAreaPainter.SearchMatch firstMatch = painter.getCurrentMatch();
            codeArea.revealPosition(firstMatch.getPosition(), codeArea.getActiveSection());
        }
        hexSearchPanel.setStatus(foundMatches.size(), 0);
        codeArea.repaint();
    }

    public static interface CharsetChangeListener {

        public void charsetChanged();
    }
}
