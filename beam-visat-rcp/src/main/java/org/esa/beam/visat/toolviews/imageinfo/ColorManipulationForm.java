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
package org.esa.beam.visat.toolviews.imageinfo;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerFilter;
import com.bc.ceres.glayer.support.LayerUtils;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.BeamUiActivator;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.SuppressibleOptionPane;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.application.PageComponentDescriptor;
import org.esa.beam.framework.ui.product.BandChooser;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.glayer.ColorBarLayerType;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.BeamFileChooser;
import org.esa.beam.util.io.BeamFileFilter;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


/**
 * The contrast stretch window.
 */
class ColorManipulationForm {

    private final static String PREFERENCES_KEY_IO_DIR = "visat.color_palettes.dir";

    private final static String FILE_EXTENSION_CPD = "cpd";
    private final static String FILE_EXTENSION_PAL = "pal";
    private final static String FILE_EXTENSION_CPT = "cpt";

    private VisatApp visatApp;
    private PropertyMap preferences;
    private PropertyMap configuration = null;
    private AbstractButton resetButton;
    private AbstractButton multiApplyButton;
    private AbstractButton importButton;
    private AbstractButton exportButton;
    private SuppressibleOptionPane suppressibleOptionPane;

    private ProductSceneView productSceneView;
    private Band[] bandsToBeModified;
    private BeamFileFilter beamFileFilter;
    private BeamFileFilter palFileFilter;
    private BeamFileFilter cptFileFilter;
    private final ProductNodeListener productNodeListener;
    private boolean defaultColorPalettesInstalled;
    private boolean defaultRgbProfilesInstalled;

    private JPanel contentPanel;
    private final ColorManipulationToolView toolView;
    private ColorManipulationChildForm childForm;
    private ColorManipulationChildForm continuous1BandSwitcherForm;
    private ColorManipulationChildForm discrete1BandTabularForm;
    private ColorManipulationChildForm continuous3BandGraphicalForm;
    private JPanel toolButtonsPanel;
    private AbstractButton helpButton;
    private File ioDir;
    private JPanel editorPanel;
    private ImageInfo imageInfo; // our model!
    private MoreOptionsPane moreOptionsPane;
    private SceneViewImageInfoChangeListener sceneViewChangeListener;
    private final String titlePrefix;

    public ColorManipulationForm(ColorManipulationToolView colorManipulationToolView) {
        this.toolView = colorManipulationToolView;
        visatApp = VisatApp.getApp();
        preferences = visatApp.getPreferences();
        productNodeListener = new ColorManipulationPNL();
        sceneViewChangeListener = new SceneViewImageInfoChangeListener();
        titlePrefix = getToolViewDescriptor().getTitle();
    }

    public ProductSceneView getProductSceneView() {
        return productSceneView;
    }

    public JPanel getContentPanel() {
        if (contentPanel == null) {
            initContentPanel();
        }
        if (!defaultColorPalettesInstalled) {
            installDefaultColorPalettes();
        }

        installColorPalettesExtras();

        if (!defaultRgbProfilesInstalled) {
            installDefaultRgbProfiles();
        }


        return contentPanel;
    }

    public void revalidateToolViewPaneControl() {
        getToolViewPaneControl().invalidate();
        getToolViewPaneControl().validate();
        getToolViewPaneControl().repaint();
        updateToolButtons();
    }

    public static AbstractButton createButton(final String iconPath) {
        return ToolButtonFactory.createButton(UIUtils.loadImageIcon(iconPath), false);
    }

    public static AbstractButton createToggleButton(final String iconPath) {
        return ToolButtonFactory.createButton(UIUtils.loadImageIcon(iconPath), true);
    }

    private Component getToolViewPaneControl() {
        return toolView.getPaneControl();
    }

    public ImageInfo getImageInfo() {
        return imageInfo;
    }

    private void setProductSceneView(final ProductSceneView productSceneView) {
        ProductSceneView productSceneViewOld = this.productSceneView;
        if (productSceneViewOld != null) {
            productSceneViewOld.getProduct().removeProductNodeListener(productNodeListener);
            productSceneViewOld.removePropertyChangeListener(sceneViewChangeListener);
        }
        this.productSceneView = productSceneView;
        if (this.productSceneView != null) {
            this.productSceneView.getProduct().addProductNodeListener(productNodeListener);
            this.productSceneView.addPropertyChangeListener(sceneViewChangeListener);
        }

        if (this.productSceneView != null) {
            boolean paletteInitialized = false;

            if (getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().isPaletteInitialized()) {
                paletteInitialized = true;
            } else {
                ColorPaletteDef colorPaletteDef = this.productSceneView.getImageInfo().getColorPaletteDef();

                if (colorPaletteDef != null && colorPaletteDef.getNumPoints() > 3)
                    paletteInitialized = true;
            }

            if (!paletteInitialized) {

                this.productSceneView.setToDefaultColorScheme(getSystemAuxdataDir(), createDefaultImageInfo());
                getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setPaletteInitialized(true);
            }


            // boolean profile = this.productSceneView.isProfile();

            setImageInfoCopy(this.productSceneView.getImageInfo());
        }

        installChildForm(productSceneViewOld);

        updateTitle();
        updateToolButtons();

        setApplyEnabled(false);

    }

    private void installChildForm(ProductSceneView productSceneViewOld) {
        final ColorManipulationChildForm oldForm = childForm;
        ColorManipulationChildForm newForm = EmptyImageInfoForm.INSTANCE;
        if (productSceneView != null) {
            if (isContinuous3BandImage()) {
                if (oldForm instanceof Continuous3BandGraphicalForm) {
                    newForm = oldForm;
                } else {/**/
                    newForm = getContinuous3BandGraphicalForm();
                }
            } else if (isContinuous1BandImage()) {
                if (oldForm instanceof Continuous1BandSwitcherForm) {
                    newForm = oldForm;
                } else {
                    newForm = getContinuous1BandSwitcherForm();
                }
            } else if (isDiscrete1BandImage()) {
                if (oldForm instanceof Discrete1BandTabularForm) {
                    newForm = oldForm;
                } else {
                    newForm = getDiscrete1BandTabularForm();
                }
            } else {
                if (oldForm instanceof Continuous1BandSwitcherForm) {
                    newForm = oldForm;
                } else {
                    newForm = getContinuous1BandSwitcherForm();
                }
            }
        }

        if (newForm != oldForm) {
            childForm = newForm;

            installToolButtons();
            installMoreOptions();

            editorPanel.removeAll();
            editorPanel.add(childForm.getContentPanel(), BorderLayout.CENTER);
            if (!(childForm instanceof EmptyImageInfoForm)) {
                editorPanel.add(moreOptionsPane.getContentPanel(), BorderLayout.SOUTH);
            }
            revalidateToolViewPaneControl();

            if (oldForm != null) {
                oldForm.handleFormHidden(productSceneViewOld);
            }
            childForm.handleFormShown(productSceneView);
        } else {
            childForm.updateFormModel(productSceneView);
        }
    }

    private void updateTitle() {
        String titlePostfix = "";
        if (productSceneView != null) {
            titlePostfix = " - " + productSceneView.getSceneName();
        }
        toolView.setTitle(titlePrefix + titlePostfix);
    }

    private void updateToolButtons() {
        final boolean hasSceneView = this.productSceneView != null;
        resetButton.setEnabled(hasSceneView);
        importButton.setEnabled(hasSceneView && !isRgbMode());
        exportButton.setEnabled(hasSceneView && !isRgbMode());
    }

    private ColorManipulationChildForm getContinuous3BandGraphicalForm() {
        if (continuous3BandGraphicalForm == null) {
            continuous3BandGraphicalForm = new Continuous3BandGraphicalForm(this);
        }
        return continuous3BandGraphicalForm;
    }

    private ColorManipulationChildForm getContinuous1BandSwitcherForm() {
        if (continuous1BandSwitcherForm == null) {
            continuous1BandSwitcherForm = new Continuous1BandSwitcherForm(this);
        }
        return continuous1BandSwitcherForm;
    }

    private ColorManipulationChildForm getDiscrete1BandTabularForm() {
        if (discrete1BandTabularForm == null) {
            discrete1BandTabularForm = new Discrete1BandTabularForm(this);
        }
        return discrete1BandTabularForm;
    }

    private boolean isContinuous3BandImage() {
        return productSceneView.isRGB();
    }

    private boolean isContinuous1BandImage() {
        return (productSceneView.getRaster() instanceof Band)
                && ((Band) productSceneView.getRaster()).getIndexCoding() == null;
    }

    private boolean isDiscrete1BandImage() {
        return (productSceneView.getRaster() instanceof Band)
                && ((Band) productSceneView.getRaster()).getIndexCoding() != null;
    }

    private PageComponentDescriptor getToolViewDescriptor() {
        return toolView.getDescriptor();
    }

    public ActionListener wrapWithAutoApplyActionListener(final ActionListener actionListener) {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionListener.actionPerformed(e);
                applyChanges();
            }
        };
    }

    private void initContentPanel() {

        moreOptionsPane = new MoreOptionsPane(this);

        resetButton = createButton("icons/Undo24.gif");
        resetButton.setName("ResetButton");
        resetButton.setToolTipText("Reset to color scheme defaults"); /*I18N*/
        resetButton.addActionListener(wrapWithAutoApplyActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                resetToDefaults();
            }
        }));

        multiApplyButton = createButton("icons/MultiAssignBands24.gif");
        multiApplyButton.setName("MultiApplyButton");
        multiApplyButton.setToolTipText("Apply to other bands"); /*I18N*/
        multiApplyButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                applyMultipleColorPaletteDef();
            }
        });
        // todo Made this invisible till bug is fixed
        multiApplyButton.setVisible(false);


        importButton = createButton("/com/bc/ceres/swing/actions/icons_16x16/document-open.png");
        importButton.setName("ImportButton");
        importButton.setToolTipText("Import colour palette from text file."); /*I18N*/
        importButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                importColorPaletteDef();
                applyChanges();
            }
        });
        importButton.setEnabled(true);
        importButton.setVisible(false);

        exportButton = createButton("/com/bc/ceres/swing/actions/icons_16x16/document-save.png");
        exportButton.setName("ExportButton");
        exportButton.setToolTipText("Save colour palette to text file."); /*I18N*/
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                exportColorPaletteDef();
                childForm.updateFormModel(getProductSceneView());
            }
        });
        exportButton.setEnabled(true);
        exportButton.setVisible(false);

        helpButton = createButton("icons/Help22.png");
        helpButton.setToolTipText("Help."); /*I18N*/
        helpButton.setName("helpButton");

        editorPanel = new JPanel(new BorderLayout(4, 4));
        toolButtonsPanel = GridBagUtils.createPanel();

        contentPanel = new JPanel(new BorderLayout(4, 4));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        contentPanel.setPreferredSize(new Dimension(320, 200));
        contentPanel.setMinimumSize(new Dimension(100, 200));
        contentPanel.add(editorPanel, BorderLayout.CENTER);
        contentPanel.add(toolButtonsPanel, BorderLayout.EAST);

        installHelp();
        suppressibleOptionPane = visatApp.getSuppressibleOptionPane();

        setProductSceneView(visatApp.getSelectedProductSceneView());

        // Add an internal frame listsner to VISAT so that we can update our
        // contrast stretch dialog with the information of the currently activated
        // product scene view.
        //
        visatApp.addInternalFrameListener(new ColorManipulationIFL());
    }


    public void setApplyEnabled(final boolean enabled) {
        final boolean canApply = productSceneView != null;
        multiApplyButton.setEnabled(canApply && (!enabled && (!isRgbMode() && visatApp != null)));
    }

//    void installToolButtons() {
//        toolButtonsPanel.removeAll();
//        GridBagConstraints gbc = new GridBagConstraints();
//        gbc.anchor = GridBagConstraints.CENTER;
//        gbc.fill = GridBagConstraints.NONE;
//        gbc.weightx = 1.0;
//        gbc.gridy = 0;
//        gbc.insets.bottom = 0;
//        gbc.gridwidth = 1;
//        gbc.gridy++;
//        toolButtonsPanel.add(resetButton, gbc);
//        toolButtonsPanel.add(multiApplyButton, gbc);
//        gbc.gridy++;
//        toolButtonsPanel.add(importButton, gbc);
//        toolButtonsPanel.add(exportButton, gbc);
//        gbc.gridy++;
//        AbstractButton[] additionalButtons = childForm.getToolButtons();
//        for (int i = 0; i < additionalButtons.length; i++) {
//            AbstractButton button = additionalButtons[i];
//            toolButtonsPanel.add(button, gbc);
//            if (i % 2 == 1) {
//                gbc.gridy++;
//            }
//        }
//
//        gbc.gridy++;
//        gbc.fill = GridBagConstraints.VERTICAL;
//        gbc.weighty = 1.0;
//        gbc.gridwidth = 2;
//        toolButtonsPanel.add(new JLabel(" "), gbc); // filler
//        gbc.fill = GridBagConstraints.NONE;
//        gbc.weighty = 0.0;
//        gbc.gridwidth = 1;
//        gbc.gridy++;
//        gbc.gridx = 1;
//        toolButtonsPanel.add(helpButton, gbc);
//
//        installToolButtonsNew();
//    }

    void installToolButtons() {
        toolButtonsPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;


        gbc.gridy = 0;
        gbc.gridx = 0;
        toolButtonsPanel.add(resetButton, gbc);

        gbc.gridy++;
        toolButtonsPanel.add(importButton, gbc);

        gbc.gridy++;
        toolButtonsPanel.add(exportButton, gbc);

        gbc.gridy++;
        toolButtonsPanel.add(multiApplyButton, gbc);


        gbc.gridy++;
        gbc.gridx = 0;
        AbstractButton[] additionalButtons = childForm.getToolButtons();
        for (int i = 0; i < additionalButtons.length; i++) {
            AbstractButton button = additionalButtons[i];
            toolButtonsPanel.add(button, gbc);
            //          if (i % 2 == 1) {
            gbc.gridy++;
            //          }
        }

        gbc.gridy++;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weighty = 1.0;
        gbc.gridwidth = 2;
        toolButtonsPanel.add(new JLabel(" "), gbc); // filler
        gbc.fill = GridBagConstraints.NONE;
        gbc.weighty = 0.0;
        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        toolButtonsPanel.add(helpButton, gbc);
    }

    void installMoreOptions() {
        final MoreOptionsForm moreOptionsForm = childForm.getMoreOptionsForm();
        if (moreOptionsForm != null) {
            moreOptionsForm.updateForm();
            moreOptionsPane.setComponent(moreOptionsForm.getContentPanel());
        }
    }

    // todo - code duplication in all tool views with help support!!! (nf 200905)
    private void installHelp() {
        if (getToolViewDescriptor().getHelpId() != null) {
            HelpSys.enableHelpOnButton(helpButton, getToolViewDescriptor().getHelpId());
            HelpSys.enableHelpKey(getToolViewPaneControl(), getToolViewDescriptor().getHelpId());
        }
    }

    public void showMessageDialog(String propertyName, String message, String title) {
        suppressibleOptionPane.showMessageDialog(propertyName,
                getToolViewPaneControl(),
                message,
                getToolViewDescriptor().getTitle() + title,
                JOptionPane.INFORMATION_MESSAGE);
    }


    void applyChanges() {
        setApplyEnabled(false);
        if (productSceneView != null) {
            try {
                getToolViewPaneControl().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                if (isRgbMode()) {
                    productSceneView.setRasters(childForm.getRasters());
                } else {
                    productSceneView.getRaster().setImageInfo(imageInfo);
                }
                productSceneView.setImageInfo(imageInfo);
            } finally {
                getToolViewPaneControl().setCursor(Cursor.getDefaultCursor());
                //added to remove the old color bar layer from the image when a new color scheme is applied
                Layer colorBarLayer = LayerUtils.getChildLayer(productSceneView.getRootLayer(), LayerUtils.SearchMode.DEEP, new LayerFilter() {
                    public boolean accept(Layer layer) {
                        return layer.getLayerType() instanceof ColorBarLayerType;
                    }
                });
                if (colorBarLayer != null) {
                    VisatApp.getApp().showMessageDialog("Removing current color bar layer", "You've changed the color schema. Current color bar layer will be " +
                            "removed from the image.", ModalDialog.ID_OK, null);
                    productSceneView.getRootLayer().getChildren().remove(colorBarLayer);
                }
            }
        }
        setApplyEnabled(false);
    }

    private void setImageInfoCopy(ImageInfo imageInfo) {
        this.imageInfo = imageInfo.createDeepCopy();
    }

    private void resetToDefaults() {
//        if (productSceneView != null) {
//            setImageInfoCopy(createDefaultImageInfo());
//            childForm.resetFormModel(getProductSceneView());
//        }

        if (this.productSceneView != null) {
            this.productSceneView.setToDefaultColorScheme(getSystemAuxdataDir(), createDefaultImageInfo());
            setImageInfoCopy(this.productSceneView.getImageInfo());
            childForm.resetFormModel(getProductSceneView());
        }
    }

// todo This applies palette to other bands which does not really work correctly due to the new color scheme log mode etc.
    // todo needs revamping: make sure log mode works, and loaded palette display info in GUI is correct.
    private void applyMultipleColorPaletteDef() {
        if (productSceneView == null) {
            return;
        }

        final Product selectedProduct = productSceneView.getProduct();
        final ProductManager productManager = selectedProduct.getProductManager();
        final RasterDataNode[] protectedRasters = productSceneView.getRasters();
        final ArrayList<Band> availableBandList = new ArrayList<>();
        for (int i = 0; i < productManager.getProductCount(); i++) {
            final Product product = productManager.getProduct(i);
            final Band[] bands = product.getBands();
            for (final Band band : bands) {
                boolean validBand = false;
                if (band.getImageInfo() != null) {
                    validBand = true;
                    for (RasterDataNode protectedRaster : protectedRasters) {
                        if (band == protectedRaster) {
                            validBand = false;
                        }
                    }
                }
                if (validBand) {
                    availableBandList.add(band);
                }
            }
        }
        final Band[] availableBands = new Band[availableBandList.size()];
        availableBandList.toArray(availableBands);
        availableBandList.clear();

        if (availableBands.length == 0) {
            JOptionPane.showMessageDialog(getToolViewPaneControl(),
                    "No other bands available.", /*I18N*/
                    getToolViewDescriptor().getTitle(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        final BandChooser bandChooser = new BandChooser(toolView.getPaneWindow(),
                "Apply to other bands", /*I18N*/
                getToolViewDescriptor().getHelpId(),
                availableBands,
                bandsToBeModified, false);
        final List<Band> modifiedRasterList = new ArrayList<>(availableBands.length);
        if (bandChooser.show() == BandChooser.ID_OK) {
            bandsToBeModified = bandChooser.getSelectedBands();
            for (final Band band : bandsToBeModified) {
                applyColorPaletteDef(getImageInfo().getColorPaletteDef(), band, band.getImageInfo());
                modifiedRasterList.add(band);
            }
        }

        final Band[] rasters = new Band[modifiedRasterList.size()];
        modifiedRasterList.toArray(rasters);
        modifiedRasterList.clear();
        visatApp.updateImages(rasters);
    }

    private void setIODir(final File dir) {
        // todo Danny commented out and added line of code to avoid changing the directory for cpd import/export
//        if (preferences != null) {
//            preferences.setPropertyString(PREFERENCES_KEY_IO_DIR, dir.getPath());
//        }
//        ioDir = dir;
        ioDir = getSystemAuxdataDir();
    }

    protected File getIODir() {
        if (ioDir == null) {
            if (preferences != null) {
                ioDir = new File(
                        preferences.getPropertyString(PREFERENCES_KEY_IO_DIR, getSystemAuxdataDir().getPath()));
            } else {
                ioDir = getSystemAuxdataDir();
            }
        }
        return ioDir;
    }

    private BeamFileFilter getOrCreateCpdFileFilter() {
        if (beamFileFilter == null) {
            final String formatName = "COLOR_PALETTE_DEFINITION_FILE";
            final String description = FILE_EXTENSION_CPD.toUpperCase() + " - SeaDAS color palette format (*."+FILE_EXTENSION_CPD + ")";  /*I18N*/
            beamFileFilter = new BeamFileFilter(formatName, "."+FILE_EXTENSION_CPD, description);
        }
        return beamFileFilter;
    }



    private BeamFileFilter getOrCreatePalFileFilter() {
        if (palFileFilter == null) {
            final String formatName = "GENERIC_COLOR_PALETTE_FILE";
            final String description = FILE_EXTENSION_PAL.toUpperCase() + " - Generic 256 point color palette format (*."+FILE_EXTENSION_PAL + ")";  /*I18N*/
            palFileFilter = new BeamFileFilter(formatName, "."+FILE_EXTENSION_PAL, description);
        }
        return palFileFilter;
    }

    private BeamFileFilter getOrCreateCptFileFilter() {
        if (cptFileFilter == null) {
            final String formatName = "CPT_COLOR_PALETTE_FILE";
            final String description = FILE_EXTENSION_CPT.toUpperCase() + " - Generic mapping tools color palette format (*."+FILE_EXTENSION_CPT + ")";  /*I18N*/
            cptFileFilter = new BeamFileFilter(formatName, "."+FILE_EXTENSION_CPT, description);
        }
        return cptFileFilter;
    }



    private void importColorPaletteDef() {
        final ImageInfo targetImageInfo = getImageInfo();
        if (targetImageInfo == null) {
            // Normaly this code is unreachable because, the export Button
            // is disabled if the _contrastStretchPane has no ImageInfo.
            return;
        }
        final BeamFileChooser fileChooser = new BeamFileChooser();
        fileChooser.setDialogTitle("Import Colour Palette"); /*I18N*/
        fileChooser.setFileFilter(getOrCreateCpdFileFilter());
        fileChooser.setCurrentDirectory(getIODir());
        final int result = fileChooser.showOpenDialog(getToolViewPaneControl());
        final File file = fileChooser.getSelectedFile();
        if (file != null && file.getParentFile() != null) {
            setIODir(file.getParentFile());
        }
        if (result == JFileChooser.APPROVE_OPTION) {
            if (file != null && file.canRead()) {
                try {
                    final ColorPaletteDef colorPaletteDef = ColorPaletteDef.loadColorPaletteDef(file);
                    colorPaletteDef.getFirstPoint().setLabel(file.getName());
                    applyColorPaletteDef(colorPaletteDef, getProductSceneView().getRaster(), targetImageInfo);
                    // todo this needs to read cpd file setting for logScaled but right now safest to set at false because
                    // a nonlogscaled cpd may contain min=0
                    getImageInfo().setLogScaled(false);
                    setImageInfoCopy(targetImageInfo);
                    childForm.updateFormModel(getProductSceneView());
                    setApplyEnabled(true);
                    getImageInfo().getColorPaletteSourcesInfo().setCpdFileName(file.getName());
                    getImageInfo().getColorPaletteSourcesInfo().setSchemeName(null);
                    getImageInfo().getColorPaletteSourcesInfo().setAlteredScheme(true);


                } catch (IOException e) {
                    showErrorDialog("Failed to import colour palette:\n" + e.getMessage());
                }
            }
        }
    }

    private void applyColorPaletteDef(ColorPaletteDef colorPaletteDef,
                                      RasterDataNode targetRaster,
                                      ImageInfo targetImageInfo) {
        if (isIndexCoded(targetRaster)) {
            targetImageInfo.setColors(colorPaletteDef.getColors());
        } else {
            Stx stx = targetRaster.getStx(false, ProgressMonitor.NULL);
            Boolean autoDistribute = getAutoDistribute(colorPaletteDef);
            if (autoDistribute == null) {
                return;
            }
            targetImageInfo.setColorPaletteDef(colorPaletteDef,
                    stx.getMinimum(),
                    stx.getMaximum(),
                    autoDistribute);
        }
    }

    private Boolean getAutoDistribute(ColorPaletteDef colorPaletteDef) {
        if (colorPaletteDef.isAutoDistribute()) {
            return Boolean.TRUE;
        }
        int answer = JOptionPane.showConfirmDialog(getToolViewPaneControl(),
                "Automatically distribute points of\n" +
                        "colour palette between min/max?",
                "Import Colour Palette",
                JOptionPane.YES_NO_CANCEL_OPTION
        );
        if (answer == JOptionPane.YES_OPTION) {
            return Boolean.TRUE;
        } else if (answer == JOptionPane.NO_OPTION) {
            return Boolean.FALSE;
        } else {
            return null;
        }
    }

    private boolean isIndexCoded(RasterDataNode targetRaster) {
        return targetRaster instanceof Band && ((Band) targetRaster).getIndexCoding() != null;
    }

    protected void exportColorPaletteDef() {
        final ImageInfo imageInfo = getImageInfo();
        if (imageInfo == null) {
            // Normaly this code is unreacable because, the export Button should be
            // disabled if the color manipulation form have no ImageInfo.
            return;
        }
        final BeamFileChooser fileChooser = new BeamFileChooser();
        fileChooser.setDialogTitle("Export Color Palette"); /*I18N*/

     //   fileChooser.setFileFilter(getOrCreateCpdFileFilter());
        fileChooser.addChoosableFileFilter(getOrCreateCpdFileFilter());
        fileChooser.addChoosableFileFilter(getOrCreatePalFileFilter());
        fileChooser.addChoosableFileFilter(getOrCreateCptFileFilter());
        fileChooser.setCurrentDirectory(getIODir());
        final int result = fileChooser.showSaveDialog(getToolViewPaneControl());
        File file = fileChooser.getSelectedFile();
        if (file != null && file.getParentFile() != null) {
            setIODir(file.getParentFile());
        }
        if (result == JFileChooser.APPROVE_OPTION) {
            if (file != null) {
                if (!visatApp.promptForOverwrite(file)) {
                    return;
                }

                try {
                    final ColorPaletteDef colorPaletteDef = imageInfo.getColorPaletteDef();

                    String path = file.getPath();
                    if (path.endsWith("."+FILE_EXTENSION_PAL)) {
                        ColorPaletteDef.storePal(colorPaletteDef, file);
                    } else if (path.endsWith("."+FILE_EXTENSION_CPT)) {
                        ColorPaletteDef.storeCpt(colorPaletteDef, file);
                    } else {
                        file = FileUtils.ensureExtension(file, "."+FILE_EXTENSION_CPD);
                        ColorPaletteDef.storeColorPaletteDef(colorPaletteDef, file);
                        imageInfo.getColorPaletteSourcesInfo().setCpdFileName(file.getName());
                        imageInfo.getColorPaletteSourcesInfo().setSchemeName(null);
                        imageInfo.getColorPaletteSourcesInfo().setAlteredCpd(false);
                    }


                } catch (IOException e) {
                    showErrorDialog("Failed to export color palette:\n" + e.getMessage());  /*I18N*/
                }
            }
        }
    }



    private boolean isRgbMode() {
        return productSceneView != null && isContinuous3BandImage();
    }

    private void showErrorDialog(final String message) {
        if (message != null && message.trim().length() > 0) {
            if (visatApp != null) {
                visatApp.showErrorDialog(message);
            } else {
                JOptionPane.showMessageDialog(getToolViewPaneControl(),
                        message,
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    protected void installColorPalettesExtras() {
        final URL codeSourceUrl = BeamUiActivator.class.getProtectionDomain().getCodeSource().getLocation();
        final File auxdataDir = getColorPalettesExtrasAuxdataDir();
        final ResourceInstaller resourceInstaller = new ResourceInstaller(codeSourceUrl, "auxdata/color_palettes_extras/",
                auxdataDir);
        ProgressMonitorSwingWorker swingWorker = new ProgressMonitorSwingWorker(toolView.getPaneControl(),
                "Installing Auxdata Extras ...") {
            @Override
            protected Object doInBackground(ProgressMonitor progressMonitor) throws Exception {

                resourceInstaller.install(".*.cpd", progressMonitor, false);
                resourceInstaller.install(".*.txt", progressMonitor, false);

                return Boolean.TRUE;
            }

            /**
             * Executed on the <i>Event Dispatch Thread</i> after the {@code doInBackground}
             * method is finished. The default
             * implementation does nothing. Subclasses may override this method to
             * perform completion actions on the <i>Event Dispatch Thread</i>. Note
             * that you can query status inside the implementation of this method to
             * determine the result of this task or whether this task has been cancelled.
             *
             * @see #doInBackground
             * @see #isCancelled()
             * @see #get
             */
            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    visatApp.getLogger().log(Level.SEVERE, "Could not install auxdata/color_palettes_extras", e);
                }
            }
        };
        swingWorker.executeWithBlocking();
    }





    protected void installDefaultColorPalettes() {
        final URL codeSourceUrl = BeamUiActivator.class.getProtectionDomain().getCodeSource().getLocation();
        final File auxdataDir = getSystemAuxdataDir();
        final ResourceInstaller resourceInstaller = new ResourceInstaller(codeSourceUrl, "auxdata/color_palettes/",
                auxdataDir);
        ProgressMonitorSwingWorker swingWorker = new ProgressMonitorSwingWorker(toolView.getPaneControl(),
                "Installing Auxdata...") {
            @Override
            protected Object doInBackground(ProgressMonitor progressMonitor) throws Exception {

                // this enables user to delete any palettes except for the oceancolor prefix ones.
                File cpdDefaultsFile = new File(auxdataDir, ColorPaletteSchemes.NEW_CPD_SCHEMES_FILENAME);
                if (!cpdDefaultsFile.exists()) {
                    resourceInstaller.install(".*.cpd", progressMonitor, false);

                } else {
                    resourceInstaller.install("gray_scale.cpd", progressMonitor, false);

                    File oceancolorStandardCpdFile = new File(auxdataDir, "oceancolor_standard.cpd");
                    if (oceancolorStandardCpdFile.exists()) {
                        resourceInstaller.install("oceancolor_standard.cpd", progressMonitor, true);
                    }

                    File oceancolorSssCpdFile = new File(auxdataDir, "oceancolor_sss.cpd");
                    if (oceancolorSssCpdFile.exists()) {
                        resourceInstaller.install("oceancolor_sss.cpd", progressMonitor, true);
                    }

                    File oceancolorSstCpdFile = new File(auxdataDir, "oceancolor_sst.cpd");
                    if (oceancolorSstCpdFile.exists()) {
                        resourceInstaller.install("oceancolor_sst.cpd", progressMonitor, true);
                    }

                    File oceancolorZphoticCpdFile = new File(auxdataDir, "oceancolor_zphotic.cpd");
                    if (oceancolorZphoticCpdFile.exists()) {
                        resourceInstaller.install("oceancolor_zphotic.cpd", progressMonitor, true);
                    }

                    File oceancolorNdviCpdFile = new File(auxdataDir, "oceancolor_ndvi.cpd");
                    if (oceancolorNdviCpdFile.exists()) {
                        resourceInstaller.install("oceancolor_ndvi.cpd", progressMonitor, true);
                    }

                }





//                // theses files have to exist


//                resourceInstaller.install(ColorPaletteSchemes.CPD_DEFAULTS_FILENAME, progressMonitor, false);
//                resourceInstaller.install(ColorPaletteSchemes.CPD_COLORBLIND_DEFAULTS_FILENAME, progressMonitor, false);
//                resourceInstaller.install(ColorPaletteSchemes.CPD_COLORBLIND_SELECTOR_FILENAME, progressMonitor, false);
//                resourceInstaller.install(ColorPaletteSchemes.USER_CPD_DEFAULTS_FILENAME, progressMonitor, false);
//                resourceInstaller.install(ColorPaletteSchemes.CPD_SCHEMES_FILENAME, progressMonitor, false);
//                resourceInstaller.install(ColorPaletteSchemes.USER_CPD_SCHEMES_FILENAME, progressMonitor, false);
                resourceInstaller.install(ColorPaletteSchemes.NEW_CPD_SCHEMES_FILENAME, progressMonitor, false);
                resourceInstaller.install(ColorPaletteSchemes.NEW_CPD_DEFAULTS_FILENAME, progressMonitor, false);
                resourceInstaller.install(ColorPaletteSchemes.NEW_CPD_SELECTOR_FILENAME, progressMonitor, false);


//                File userCpdDefaultsFile = new File(auxdataDir, ColorPaletteSchemes.USER_CPD_DEFAULTS_FILENAME);
//                if (!userCpdDefaultsFile.exists()) {
//                    resourceInstaller.install(userCpdDefaultsFile.getName(), progressMonitor);
//                }
//
//
//                File userCpdSchemesFile = new File(auxdataDir, ColorPaletteSchemes.USER_CPD_SCHEMES_FILENAME);
//                if (!userCpdSchemesFile.exists()) {
//                    resourceInstaller.install(userCpdSchemesFile.getName(), progressMonitor);
//                }

                defaultColorPalettesInstalled = true;
                return Boolean.TRUE;
            }

            /**
             * Executed on the <i>Event Dispatch Thread</i> after the {@code doInBackground}
             * method is finished. The default
             * implementation does nothing. Subclasses may override this method to
             * perform completion actions on the <i>Event Dispatch Thread</i>. Note
             * that you can query status inside the implementation of this method to
             * determine the result of this task or whether this task has been cancelled.
             *
             * @see #doInBackground
             * @see #isCancelled()
             * @see #get
             */
            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    visatApp.getLogger().log(Level.SEVERE, "Could not install auxdata", e);
                }
            }
        };
        swingWorker.executeWithBlocking();
    }




    private File getSystemAuxdataDir() {
        return new File(SystemUtils.getApplicationDataDir(), "beam-ui/auxdata/color-palettes");
    }

    private File getColorPalettesExtrasAuxdataDir() {
        return new File(SystemUtils.getApplicationDataDir(), "beam-ui/auxdata/color-palettes-extras");
    }

    private File getRGBAuxdataDir() {
        return new File(SystemUtils.getApplicationDataDir(), "beam-core/auxdata/rgb_profiles");
    }


    private void installDefaultRgbProfiles() {
        final URL codeSourceUrl = BeamUiActivator.class.getProtectionDomain().getCodeSource().getLocation();
        final File auxdataDir = getRGBAuxdataDir();
        final ResourceInstaller resourceInstaller = new ResourceInstaller(codeSourceUrl, "auxdata/rgb_profiles/",
                auxdataDir);
        ProgressMonitorSwingWorker swingWorker = new ProgressMonitorSwingWorker(toolView.getPaneControl(),
                "Installing RGB Auxdata...") {
            @Override
            protected Object doInBackground(ProgressMonitor progressMonitor) throws Exception {
                resourceInstaller.install(".*.rgb", progressMonitor, false);
                resourceInstaller.install(".*.txt", progressMonitor, false);
                resourceInstaller.install(".*.pl", progressMonitor, false);
                defaultRgbProfilesInstalled = true;
                return Boolean.TRUE;
            }

            /**
             * Executed on the <i>Event Dispatch Thread</i> after the {@code doInBackground}
             * method is finished. The default
             * implementation does nothing. Subclasses may override this method to
             * perform completion actions on the <i>Event Dispatch Thread</i>. Note
             * that you can query status inside the implementation of this method to
             * determine the result of this task or whether this task has been cancelled.
             *
             * @see #doInBackground
             * @see #isCancelled()
             * @see #get
             */
            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    visatApp.getLogger().log(Level.SEVERE, "Could not install RGB auxdata", e);
                }
            }
        };
        swingWorker.executeWithBlocking();
    }





    private ImageInfo createDefaultImageInfo() {
        try {
            RasterDataNode[] rasters = productSceneView.getRasters();

            if (configuration == null) {
                configuration = visatApp.getPreferences();
            }

            if (rasters != null && rasters.length == 3)
            {
                boolean manualMinMax = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MANUAL_MINMAX;
                double minRed = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MIN_RED;
                double minGreen = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MIN_GREEN;
                double minBlue = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MIN_BLUE;
                double maxRed = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MAX_RED;
                double maxGreen = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MAX_GREEN;
                double maxBlue = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MAX_BLUE;
                boolean setGamma = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_SET_GAMMA;
                double gammaRed = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_GAMMA_RED;
                double gammaGreen = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_GAMMA_GREEN;
                double gammaBlue = ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_GAMMA_BLUE;

                if (configuration != null) {
                    manualMinMax = configuration.getPropertyBool(ColorManipulationToolView.PREFERENCES_KEY_RGB_MANUAL_MINMAX, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MANUAL_MINMAX);
                    minRed = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_MIN_RED, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MIN_RED);
                    minGreen = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_MIN_GREEN, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MIN_GREEN);
                    minBlue = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_MIN_BLUE, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MIN_BLUE);
                    maxRed = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_MAX_RED, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MAX_RED);
                    maxGreen = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_MAX_GREEN, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MAX_GREEN);
                    maxBlue = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_MAX_BLUE, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_MAX_BLUE);
                    setGamma = configuration.getPropertyBool(ColorManipulationToolView.PREFERENCES_KEY_RGB_SET_GAMMA, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_SET_GAMMA);
                    gammaRed = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_GAMMA_RED, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_GAMMA_RED);
                    gammaGreen = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_GAMMA_GREEN, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_GAMMA_GREEN);
                    gammaBlue = configuration.getPropertyDouble(ColorManipulationToolView.PREFERENCES_KEY_RGB_GAMMA_BLUE, ColorManipulationToolView.PREFERENCES_DEFAULT_RGB_GAMMA_BLUE);
                }

                double[] minRgb = {minRed, minGreen, minBlue};
                double[] maxRgb = {maxRed, maxGreen, maxBlue};
                double[] gammaRgb = {gammaRed, gammaGreen, gammaBlue};

                return ProductUtils.createImageInfoRGB(productSceneView.getRasters(), false, manualMinMax, minRgb, maxRgb, setGamma, gammaRgb, ProgressMonitor.NULL);
            } else {
                return ProductUtils.createImageInfo(productSceneView.getRasters(), false, ProgressMonitor.NULL);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(getContentPanel(),
                    "Failed to create default image settings:\n" + e.getMessage(),
                    "I/O Error",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    Stx getStx(RasterDataNode raster) {
        return raster.getStx(false, ProgressMonitor.NULL); // todo - use PM
    }

    private class ColorManipulationPNL extends ProductNodeListenerAdapter {

        @Override
        public void nodeChanged(final ProductNodeEvent event) {
            final RasterDataNode[] rasters = childForm.getRasters();
            RasterDataNode raster = null;
            for (RasterDataNode dataNode : rasters) {
                if (event.getSourceNode() == dataNode) {
                    raster = (RasterDataNode) event.getSourceNode();
                }
            }
            if (raster != null) {
                final String propertyName = event.getPropertyName();
                if (ProductNode.PROPERTY_NAME_NAME.equalsIgnoreCase(propertyName)) {
                    updateTitle();
                    childForm.handleRasterPropertyChange(event, raster);
                } else if (RasterDataNode.PROPERTY_NAME_UNIT.equalsIgnoreCase(propertyName)) {
                    childForm.handleRasterPropertyChange(event, raster);
                } else if (RasterDataNode.PROPERTY_NAME_STX.equalsIgnoreCase(propertyName)) {
                    childForm.handleRasterPropertyChange(event, raster);
                } else if (RasterDataNode.isValidMaskProperty(propertyName)) {
                    getStx(raster);
                }
            }
        }
    }

    private class ColorManipulationIFL extends InternalFrameAdapter {

        @Override
        public void internalFrameActivated(final InternalFrameEvent e) {
            final ProductSceneView view = getProductSceneViewByFrame(e);
            setProductSceneView(view);
        }

        @Override
        public void internalFrameDeactivated(final InternalFrameEvent e) {
            if (getProductSceneView() == getProductSceneViewByFrame(e)) {
                setProductSceneView(null);
            }
        }

        private ProductSceneView getProductSceneViewByFrame(final InternalFrameEvent e) {
            final Container content = getContent(e);
            if (content instanceof ProductSceneView) {
                return (ProductSceneView) content;
            } else {
                return null;
            }
        }

        private Container getContent(InternalFrameEvent e) {
            return e.getInternalFrame().getContentPane();
        }
    }

    private class SceneViewImageInfoChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (ProductSceneView.PROPERTY_NAME_IMAGE_INFO.equals(evt.getPropertyName())) {
                setImageInfoCopy((ImageInfo) evt.getNewValue());
                childForm.updateFormModel(getProductSceneView());
            }
        }
    }
}
