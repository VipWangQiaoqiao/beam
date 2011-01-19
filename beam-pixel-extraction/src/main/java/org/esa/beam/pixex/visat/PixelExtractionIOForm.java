/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.pixex.visat;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.jidesoft.swing.FolderChooser;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.util.Debug;
import org.esa.beam.util.SystemUtils;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

class PixelExtractionIOForm {

    static final String LAST_OPEN_INPUT_DIR = "beam.petOp.lastOpenInputDir";
    static final String LAST_OPEN_OUTPUT_DIR = "beam.petOp.lastOpenOutputDir";
    static final String LAST_OPEN_FORMAT = "beam.petOp.lastOpenFormat";

    private final AppContext appContext;

    private ChangeListener inputChangeListener;

    private final JPanel panel;
    private final JLabel outputDirLabel;
    private final JList inputPathsList;
    private final JTextField outputDirTextField;
    private final PropertyContainer container;
    private final AbstractButton outputDirChooserButton;
    private final JLabel filePrefixLabel;
    private final JTextField filePrefixField;
    private InputListModel listModel;
    private BindingContext context;

    PixelExtractionIOForm(final AppContext appContext, PropertyContainer container) {
        this.appContext = appContext;
        this.container = container;
        context = new BindingContext(container);

        final TableLayout tableLayout = new TableLayout(3);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTablePadding(4, 4);
        tableLayout.setTableWeightX(0.0);
        tableLayout.setTableWeightY(0.0);
        tableLayout.setColumnWeightX(1, 1.0);
        tableLayout.setCellWeightY(0, 1, 1.0);
        tableLayout.setCellFill(0, 1, TableLayout.Fill.BOTH);
        tableLayout.setCellColspan(1, 0, 3);
        tableLayout.setCellColspan(2, 0, 3);
        tableLayout.setCellWeightX(3, 1, 0.0); // output dir path field
        panel = new JPanel(tableLayout);

        listModel = new InputListModel(container.getProperty("inputPaths"));
        listModel.addListDataListener(new MyListDataListener());
        inputPathsList = createInputPathsList(listModel);
        panel.add(new JLabel("Input paths:"));
        final JScrollPane scrollPane = new JScrollPane(inputPathsList);
        scrollPane.setPreferredSize(new Dimension(100, 50));
        panel.add(scrollPane);
        final JPanel addRemoveButtonPanel = new JPanel();
        final BoxLayout layout = new BoxLayout(addRemoveButtonPanel, BoxLayout.Y_AXIS);
        addRemoveButtonPanel.setLayout(layout);
        addRemoveButtonPanel.add(createAddInputButton());
        addRemoveButtonPanel.add(createRemoveInputButton());
        panel.add(addRemoveButtonPanel);

        panel.add(createClipboardCheckbox());
        final JCheckBox outputDirCheckBox = createOutputCheckBox();
        panel.add(outputDirCheckBox);

        outputDirLabel = new JLabel("Output directory:");
        panel.add(outputDirLabel);
        outputDirTextField = new JTextField();
        outputDirTextField.setEditable(false);
        outputDirTextField.setPreferredSize(new Dimension(80, outputDirTextField.getPreferredSize().height));
        String path = getDefaultOutputPath(appContext);
        setOutputDirPath(path);
        panel.add(outputDirTextField);
        outputDirChooserButton = createOutputDirChooserButton(container.getProperty("outputDir"));
        panel.add(outputDirChooserButton);

        filePrefixLabel = new JLabel("File prefix:");
        filePrefixField = createFilePrefixField(container.getProperty("outputFilePrefix"));
        panel.add(filePrefixLabel);
        panel.add(filePrefixField);

        setOutputUiEnabled(outputDirCheckBox.isSelected());
    }

    JPanel getPanel() {
        return panel;
    }

    void clear() {
        listModel.clear();
        setOutputDirPath("");
    }

    void setSelectedProduct(Product selectedProduct) {
        if (selectedProduct != null) {
            try {
                listModel.addElements(selectedProduct);
            } catch (ValidationException ve) {
                Debug.trace(ve);
            }
        }
    }

    Product[] getSourceProducts() {
        return listModel.getSourceProducts();
    }

    private JComponent createClipboardCheckbox() {
        final JCheckBox clipboardButton = new JCheckBox("Copy output to system clipboard");
        context.bind("copyToClipboard", clipboardButton);
        return clipboardButton;
    }

    private JCheckBox createOutputCheckBox() {
        final JCheckBox exportButton = new JCheckBox("Export to output directory", true);

        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setOutputUiEnabled(exportButton.isSelected());
                final File dir = exportButton.isSelected() ? new File(outputDirTextField.getText()) : null;
                container.setValue("outputDir", dir);
            }
        });

        return exportButton;
    }

    private void setOutputUiEnabled(boolean enable) {
        outputDirLabel.setEnabled(enable);
        outputDirTextField.setEnabled(enable);
        outputDirChooserButton.setEnabled(enable);
        filePrefixField.setEnabled(enable);
        filePrefixLabel.setEnabled(enable);
    }

    private String getDefaultOutputPath(AppContext appContext) {
        final Property dirProperty = container.getProperty("outputDir");
        String lastDir = appContext.getPreferences().getPropertyString(LAST_OPEN_OUTPUT_DIR, ".");
        String path;
        try {
            path = new File(lastDir).getCanonicalPath();
        } catch (IOException ignored) {
            path = SystemUtils.getUserHomeDir().getPath();
        }
        try {
            dirProperty.setValue(new File(path));
        } catch (ValidationException ignore) {
        }
        return path;
    }

    private void setOutputDirPath(String path) {
        outputDirTextField.setText(path);
        outputDirTextField.setToolTipText(path);
    }

    private AbstractButton createOutputDirChooserButton(final Property outputFileProperty) {
        AbstractButton button = new JButton("...");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FolderChooser folderChooser = new FolderChooser();
                folderChooser.setCurrentDirectory(new File(getDefaultOutputPath(appContext)));
                folderChooser.setDialogTitle("Select output directory");
                folderChooser.setMultiSelectionEnabled(false);
                int result = folderChooser.showDialog(appContext.getApplicationWindow(), "Select");    /*I18N*/
                if (result != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                File selectedFile = folderChooser.getSelectedFile();
                setOutputDirPath(selectedFile.getAbsolutePath());
                try {
                    outputFileProperty.setValue(selectedFile);
                    appContext.getPreferences().setPropertyString(LAST_OPEN_OUTPUT_DIR,
                                                                  selectedFile.getAbsolutePath());

                } catch (ValidationException ve) {
                    // not expected to ever come here
                    appContext.handleError("Invalid input path", ve);
                }
            }
        });
        return button;
    }

    private JTextField createFilePrefixField(Property property) {
        final JTextField textField = new JTextField();
        context.bind(property.getName(), textField);
        return textField;
    }

    private JList createInputPathsList(InputListModel inputListModel) {
        JList list = new JList(inputListModel);
        list.setCellRenderer(new MyDefaultListCellRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        return list;
    }

    private AbstractButton createAddInputButton() {
        final AbstractButton addButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Plus24.gif"),
                                                                        false);
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JPopupMenu popup = new JPopupMenu("Add");
                final Rectangle buttonBounds = addButton.getBounds();
                popup.add(new AddProductAction(appContext, listModel));
                popup.add(new AddFileAction(appContext, listModel));
                popup.add(new AddDirectoryAction(appContext, listModel, false));
                popup.add(new AddDirectoryAction(appContext, listModel, true));
                popup.show(addButton, 1, buttonBounds.height + 1);
            }
        });
        return addButton;
    }

    private AbstractButton createRemoveInputButton() {
        final AbstractButton removeButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Minus24.gif"),
                                                                           false);
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listModel.removeElementsAt(inputPathsList.getSelectedIndices());
            }
        });
        return removeButton;
    }

    public void setInputChangeListener(ChangeListener changeListener) {
        inputChangeListener = changeListener;
    }

    private static class MyDefaultListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text;
            if (value instanceof File) {
                text = ((File) value).getAbsolutePath();
            } else {
                text = ((ProductNode) value).getDisplayName();
            }

            label.setText(text);
            label.setToolTipText(text);
            return label;
        }
    }

    private class MyListDataListener implements ListDataListener {

        @Override
        public void intervalAdded(ListDataEvent e) {
            delegateToChangeListener();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            delegateToChangeListener();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
        }

        private void delegateToChangeListener() {
            if (inputChangeListener != null) {
                inputChangeListener.stateChanged(new ChangeEvent(this));
            }
        }

    }
}