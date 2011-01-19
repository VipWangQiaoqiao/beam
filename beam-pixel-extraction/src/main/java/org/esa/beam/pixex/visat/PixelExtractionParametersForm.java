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
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.jidesoft.combobox.DefaultDateModel;
import com.jidesoft.grid.DateCellEditor;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Placemark;
import org.esa.beam.framework.datamodel.PlacemarkGroup;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.DecimalTableCellRenderer;
import org.esa.beam.framework.ui.FloatCellEditor;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.product.ProductExpressionPane;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.pixex.Coordinate;
import org.jfree.ui.DateCellRenderer;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

class PixelExtractionParametersForm {

    private static final ImageIcon ADD_ICON = UIUtils.loadImageIcon("icons/Plus24.gif");
    private static final ImageIcon REMOVE_ICON = UIUtils.loadImageIcon("icons/Minus24.gif");

    private JPanel mainPanel;
    private JLabel windowLabel;
    private JSpinner windowSpinner;
    private AppContext appContext;

    private final CoordinateTableModel coordinateTableModel;
    private JButton editExpressionButton;
    private JCheckBox useExpressionCheckBox;
    private JTextArea expressionArea;
    private JRadioButton expressionAsFilterButton;
    private JRadioButton exportExpressionResultButton;
    private Product activeProduct;
    private JLabel expressionNoteLabel;
    private JSpinner timeSpinner;
    private JComboBox timeUnitComboBox;

    PixelExtractionParametersForm(AppContext appContext, PropertyContainer container) {
        this.appContext = appContext;
        coordinateTableModel = new CoordinateTableModel();
        createUi(container);
        updateUi();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public Coordinate[] getCoordinates() {
        Coordinate[] coordinates = new Coordinate[coordinateTableModel.getRowCount()];
        for (int i = 0; i < coordinateTableModel.getRowCount(); i++) {
            final Placemark placemark = coordinateTableModel.getPlacemarkAt(i);
            GeoPos geoPos = placemark.getGeoPos();
            final Date dateTime = (Date) placemark.getFeature().getAttribute(Placemark.PROPERTY_NAME_DATETIME);
            coordinates[i] = new Coordinate(placemark.getName(), geoPos.lat, geoPos.lon, dateTime);
        }
        return coordinates;
    }

    public String getExpression() {
        if (useExpressionCheckBox.isSelected()) {
            return expressionArea.getText();
        } else {
            return null;
        }
    }

    public String getAllowedTimeDifference() {
        return String.valueOf(timeSpinner.getValue()) + timeUnitComboBox.getSelectedItem().toString().charAt(0);
    }

    public boolean isExportExpressionResultSelected() {
        return exportExpressionResultButton.isSelected();
    }

    private void createUi(PropertyContainer container) {
        final TableLayout tableLayout = new TableLayout(3);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTablePadding(4, 4);
        tableLayout.setTableWeightX(0.0);
        tableLayout.setTableWeightY(0.0);
        tableLayout.setColumnWeightX(1, 1.0);
        tableLayout.setCellFill(0, 1, TableLayout.Fill.BOTH); // coordinate table
        tableLayout.setCellWeightY(0, 1, 7.0);
        tableLayout.setRowFill(3, TableLayout.Fill.BOTH); // windowSize
        tableLayout.setCellPadding(4, 0, new Insets(8, 4, 4, 4)); //expression label
        tableLayout.setCellFill(4, 1, TableLayout.Fill.BOTH); // expression panel
        tableLayout.setCellPadding(4, 1, new Insets(0, 0, 0, 0));
        tableLayout.setCellWeightX(4, 1, 1.0);
        tableLayout.setCellWeightY(4, 1, 3.0);

        mainPanel = new JPanel(tableLayout);
        mainPanel.add(new JLabel("Coordinates:"));
        final JComponent[] coordinatesComponents = createCoordinatesComponents();
        mainPanel.add(coordinatesComponents[0]);
        mainPanel.add(coordinatesComponents[1]);
        final JComponent[] timeDeltaComponents = createTimeDeltaComponents();
        mainPanel.add(timeDeltaComponents[0]);
        mainPanel.add(timeDeltaComponents[1]);
        mainPanel.add(timeDeltaComponents[2]);

        final BindingContext bindingContext = new BindingContext(container);

        mainPanel.add(new JLabel("Export:"));
        mainPanel.add(createExportPanel(bindingContext));
        mainPanel.add(new JLabel());

        mainPanel.add(new JLabel("Window size:"));
        windowSpinner = createWindowSizeEditor(bindingContext);
        windowSpinner.setPreferredSize(new Dimension(windowSpinner.getPreferredSize().width, 18));
        windowLabel = new JLabel();
        windowLabel.setHorizontalAlignment(SwingConstants.CENTER);
        windowSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateWindowLabel();
            }
        });
        mainPanel.add(windowSpinner);
        mainPanel.add(windowLabel);

        mainPanel.add(new JLabel("Expression:"));
        mainPanel.add(createExpressionPanel(bindingContext));
        mainPanel.add(new JLabel());
    }

    private JComponent[] createTimeDeltaComponents() {
        final JLabel label = new JLabel("Allowed time difference:");
        timeSpinner = new JSpinner(new SpinnerNumberModel(1, 1, null, 1));
        timeUnitComboBox = new JComboBox(new String[]{"Day(s)", "Hour(s)", "Minute(s)"});

        return new JComponent[]{label, timeSpinner, timeUnitComboBox};
    }

    private void updateUi() {
        updateWindowLabel();
        updateExpressionComponents();
    }

    private void updateExpressionComponents() {
        final boolean useExpressionSelected = useExpressionCheckBox.isSelected();
        editExpressionButton.setEnabled(useExpressionSelected && activeProduct != null);
        String toolTip = null;
        if (activeProduct == null) {
            toolTip = String.format("Editor can only be used with a product opened in %s.",
                                    appContext.getApplicationName());
        }
        editExpressionButton.setToolTipText(toolTip);
        expressionArea.setEnabled(useExpressionSelected);
        expressionNoteLabel.setEnabled(useExpressionSelected);
        expressionAsFilterButton.setEnabled(useExpressionSelected);
        exportExpressionResultButton.setEnabled(useExpressionSelected);
    }

    private void updateWindowLabel() {
        windowLabel.setText(String.format("%1$d x %1$d", (Integer) windowSpinner.getValue()));
    }

    private JPanel createExportPanel(BindingContext bindingContext) {
        final TableLayout tableLayout = new TableLayout(4);
        tableLayout.setTablePadding(4, 0);
        tableLayout.setTableFill(TableLayout.Fill.VERTICAL);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setColumnWeightX(3, 1.0);
        tableLayout.setTableWeightY(1.0);
        final JPanel exportPanel = new JPanel(tableLayout);

        exportPanel.add(createIncludeCheckbox(bindingContext, "Bands", "exportBands"));
        exportPanel.add(createIncludeCheckbox(bindingContext, "Tie-point grids", "exportTiePoints"));
        exportPanel.add(createIncludeCheckbox(bindingContext, "Masks", "exportMasks"));
        exportPanel.add(tableLayout.createHorizontalSpacer());
        return exportPanel;
    }


    private JCheckBox createIncludeCheckbox(BindingContext bindingContext, String labelText, String propertyName) {
        final Property windowProperty = bindingContext.getPropertySet().getProperty(propertyName);
        final Boolean defaultValue = (Boolean) windowProperty.getDescriptor().getDefaultValue();
        final JCheckBox checkbox = new JCheckBox(labelText, defaultValue);
        bindingContext.bind(propertyName, checkbox);
        return checkbox;
    }

    private JPanel createExpressionPanel(BindingContext bindingContext) {
        final TableLayout tableLayout = new TableLayout(2);
        tableLayout.setTablePadding(4, 4);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableWeightY(0.0);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setCellAnchor(0, 1, TableLayout.Anchor.NORTHEAST); // edit expression button
        tableLayout.setRowFill(0, TableLayout.Fill.VERTICAL);
        tableLayout.setCellFill(1, 0, TableLayout.Fill.BOTH); // expression text area
        tableLayout.setCellWeightY(1, 0, 1.0);
        tableLayout.setCellColspan(1, 0, 2);
        tableLayout.setCellColspan(2, 0, 2); // expression note line 1
        tableLayout.setCellColspan(3, 0, 2); // radio button group
        tableLayout.setCellFill(3, 0, TableLayout.Fill.BOTH);
        final JPanel panel = new JPanel(tableLayout);

        useExpressionCheckBox = new JCheckBox("Use band maths");
        useExpressionCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateExpressionComponents();
            }
        });
        editExpressionButton = new JButton("Edit Expression...");
        final Window parentWindow = SwingUtilities.getWindowAncestor(panel);
        editExpressionButton.addActionListener(new EditExpressionActionListener(parentWindow));
        panel.add(useExpressionCheckBox);
        panel.add(editExpressionButton);
        expressionArea = new JTextArea(3, 40);
        expressionArea.setLineWrap(true);
        panel.add(new JScrollPane(expressionArea));

        expressionNoteLabel = new JLabel("Note: The expression might not be applicable to all products.");
        panel.add(expressionNoteLabel);

        final ButtonGroup buttonGroup = new ButtonGroup();
        expressionAsFilterButton = new JRadioButton("Use expression as filter", true);
        buttonGroup.add(expressionAsFilterButton);
        exportExpressionResultButton = new JRadioButton("Export expression result");
        buttonGroup.add(exportExpressionResultButton);
        final Property exportResultProperty = bindingContext.getPropertySet().getProperty("exportExpressionResult");
        final Boolean defaultValue = (Boolean) exportResultProperty.getDescriptor().getDefaultValue();
        exportExpressionResultButton.setSelected(defaultValue);
        expressionAsFilterButton.setSelected(!defaultValue);

        final JPanel expressionButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        expressionButtonPanel.add(expressionAsFilterButton);
        expressionButtonPanel.add(exportExpressionResultButton);
        panel.add(expressionButtonPanel);
        return panel;
    }

    private JComponent[] createCoordinatesComponents() {
        Product selectedProduct = appContext.getSelectedProduct();
        if (selectedProduct != null) {
            final PlacemarkGroup pinGroup = selectedProduct.getPinGroup();
            for (int i = 0; i < pinGroup.getNodeCount(); i++) {
                coordinateTableModel.addPlacemark(pinGroup.get(i));
            }
        }

        final JTable coordinateTable = new JTable(coordinateTableModel);
        coordinateTable.setName("coordinateTable");
        coordinateTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        coordinateTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        coordinateTable.setRowSelectionAllowed(true);
        coordinateTable.getTableHeader().setReorderingAllowed(false);
        coordinateTable.setDefaultRenderer(Float.class, new DecimalTableCellRenderer(new DecimalFormat("0.0000")));
        coordinateTable.setPreferredScrollableViewportSize(new Dimension(150, 100));
        coordinateTable.getColumnModel().getColumn(1).setCellEditor(new FloatCellEditor(-90, 90));
        coordinateTable.getColumnModel().getColumn(2).setCellEditor(new FloatCellEditor(-180, 180));
        final DefaultDateModel dateModel = new DefaultDateModel();
        final String ISO8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
        final SimpleDateFormat dateFormat = new SimpleDateFormat(ISO8601_PATTERN, Locale.getDefault());
        dateModel.setDateFormat(dateFormat);
        final DateCellEditor dateCellEditor = new DateCellEditor(dateModel, true);
        dateCellEditor.setTimeDisplayed(true);
        coordinateTable.getColumnModel().getColumn(3).setCellEditor(dateCellEditor);
        coordinateTable.getColumnModel().getColumn(3).setPreferredWidth(200);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        final DateCellRenderer dateCellRenderer = new DateCellRenderer(dateFormat);
        dateCellRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        coordinateTable.getColumnModel().getColumn(3).setCellRenderer(dateCellRenderer);
        final JScrollPane rasterScrollPane = new JScrollPane(coordinateTable);

        final AbstractButton addButton = ToolButtonFactory.createButton(ADD_ICON, false);
        addButton.addActionListener(new AddPopupListener());
        final AbstractButton removeButton = ToolButtonFactory.createButton(REMOVE_ICON, false);
        removeButton.addActionListener(new RemovePlacemarksListener(coordinateTable, coordinateTableModel));
        final JPanel buttonPanel = new JPanel();
        final BoxLayout layout = new BoxLayout(buttonPanel, BoxLayout.Y_AXIS);
        buttonPanel.setLayout(layout);
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        return new JComponent[]{rasterScrollPane, buttonPanel};
    }

    private JSpinner createWindowSizeEditor(BindingContext bindingContext) {
        final Property windowSizeProperty = bindingContext.getPropertySet().getProperty("windowSize");
        final Number defaultValue = (Number) windowSizeProperty.getDescriptor().getDefaultValue();
        final JSpinner spinner = new JSpinner(new SpinnerNumberModel(defaultValue, 1, null, 2));
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final Object value = spinner.getValue();
                if (value instanceof Integer) {
                    int intValue = (Integer) value;
                    if (intValue % 2 == 0) {
                        spinner.setValue(intValue + 1);
                    }
                }
            }
        });
        bindingContext.bind("windowSize", spinner);
        return spinner;
    }

    public void setActiveProduct(Product product) {
        activeProduct = product;
        updateExpressionComponents();
    }

    private class AddPopupListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            final JPopupMenu popup = new JPopupMenu("Add");
            final Object source = e.getSource();
            if (source instanceof Component) {
                final Component component = (Component) source;
                final Rectangle buttonBounds = component.getBounds();
                popup.add(new AddCoordinateAction(coordinateTableModel));
                popup.add(new AddPlacemarkFileAction(appContext, coordinateTableModel, mainPanel));
                popup.show(component, 1, buttonBounds.height + 1);
            }
        }
    }

    private static class RemovePlacemarksListener implements ActionListener {

        private final JTable coordinateTable;
        private final CoordinateTableModel tableModel;

        private RemovePlacemarksListener(JTable coordinateTable, CoordinateTableModel tableModel) {
            this.coordinateTable = coordinateTable;
            this.tableModel = tableModel;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int[] selectedRows = coordinateTable.getSelectedRows();
            Placemark[] toRemove = new Placemark[selectedRows.length];
            for (int i = 0; i < selectedRows.length; i++) {
                toRemove[i] = tableModel.getPlacemarkAt(selectedRows[i]);
            }
            for (Placemark placemark : toRemove) {
                tableModel.removePlacemark(placemark);
            }
        }
    }

    private class EditExpressionActionListener implements ActionListener {

        private Window parentWindow;

        private EditExpressionActionListener(Window parentWindow) {
            this.parentWindow = parentWindow;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ProductExpressionPane pep = ProductExpressionPane.createBooleanExpressionPane(new Product[]{activeProduct},
                                                                                          activeProduct,
                                                                                          appContext.getPreferences());
            pep.setCode(expressionArea.getText());
            final int i = pep.showModalDialog(parentWindow, "Expression Editor");
            if (i == ModalDialog.ID_OK) {
                expressionArea.setText(pep.getCode());
            }

        }
    }
}