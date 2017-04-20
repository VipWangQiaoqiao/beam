/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.beam.visat.toolviews.stat;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.TextFieldContainer;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.application.ToolView;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.util.StringUtils;
import org.esa.beam.visat.VisatApp;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XIntervalSeries;
import org.jfree.data.xy.XIntervalSeriesCollection;
import org.jfree.ui.RectangleInsets;

import javax.media.jai.Histogram;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * A general pane within the statistics window.
 *
 * @author Norman Fomferra
 * @author Marco Peters
 * @author Daniel Knowles
 */
class StatisticsPanel extends PagePanel implements MultipleRoiComputePanel.ComputeMasks, StatisticsDataProvider {

    final VisatApp visatApp = VisatApp.getApp();
    private PropertyMap configuration = null;

    ProgressMonitorSwingWorker swingWorker;


    private StatisticsCriteriaPanel statisticsCriteriaPanel;


    private static final String DEFAULT_STATISTICS_TEXT = "No statistics computed";  /*I18N*/
    private static final String TITLE_PREFIX = "Statistics";


    private double spreadsheetHeightWeight = 0.3;
    private int spreadsheetMinRowsBeforeWeight = 10;

    boolean invertPercentile = true;


    int numRows = 0;

    boolean fixedHistDomainAllPlots = false;
    boolean fixedHistDomainAllPlotsInitialized = false;
    double[] histDomainBoundsAllPlots = {0, 0};

    boolean fixedPercentileDomainAllPlots = true;
    boolean fixedPercentileDomainAllPlotsInitialized = false;
    double[] histRangeBoundsAllPlots = {0, 0};
    double[] percentileDomainBoundsAllPlots = {0, 0};
    double[] percentileRangeBoundsAllPlots = {0, 0};


    private MultipleRoiComputePanel computePanel;
    private JPanel backgroundPanel;
    private AbstractButton hideAndShowButton;
    private AbstractButton exportButton;
    private JPanel contentPanel;
    private JPanel accuracyPanel;
    private JPanel spreadsheetPanel;
    JScrollPane spreadsheetScrollPane;
    JScrollPane contentScrollPane;
    JPanel leftPanel;

    private final StatisticsPanel.PopupHandler popupHandler;
    private final StringBuilder resultText;

    private boolean init;
    private Histogram[] histograms;
    private ExportStatisticsAsCsvAction exportAsCsvAction;
    private PutStatisticsIntoVectorDataAction putStatisticsIntoVectorDataAction;
    //   private AccuracyModel accuracyModel;




    private boolean exportButtonEnabled = false;
    private boolean exportButtonVisible = false;






    private Object[][] statsSpreadsheet;
    private int currRow = 1;

    private int numStatisticFields = 0;
    private int numProductRegions = 0;





    private int plotMinHeight = 300;
    private int plotMinWidth = 300;


    ToolView parentDialog;
    String helpID;



    private TextFieldContainer spreadsheetColWidthTextfieldContainer = null;


    //   private HistDisplayHighThreshTextfield plotsThreshDomainHighTextfieldContainer = new HistDisplayHighThreshTextfield();


    public StatisticsPanel(final ToolView parentDialog, String helpID) {
        super(parentDialog, helpID, TITLE_PREFIX);
        setMinimumSize(new Dimension(1000, 390));
        resultText = new StringBuilder();
        popupHandler = new PopupHandler();
        if (visatApp != null) {
            this.configuration = visatApp.getPreferences();
        }

        this.parentDialog = parentDialog;
        this.helpID = helpID;

    }

    @Override
    protected void initComponents() {
        init = true;



        statsSpreadsheet = null;


        statisticsCriteriaPanel = new StatisticsCriteriaPanel(getParentDialogContentPane());















        final JPanel rightPanel = getRightPanel();

        final ImageIcon collapseIcon = UIUtils.loadImageIcon("icons/PanelRight12.png");
        final ImageIcon collapseRolloverIcon = ToolButtonFactory.createRolloverIcon(collapseIcon);
        final ImageIcon expandIcon = UIUtils.loadImageIcon("icons/PanelLeft12.png");
        final ImageIcon expandRolloverIcon = ToolButtonFactory.createRolloverIcon(expandIcon);

        hideAndShowButton = ToolButtonFactory.createButton(collapseIcon, false);
        hideAndShowButton.setToolTipText("Collapse Options Panel");
        hideAndShowButton.setName("switchToChartButton");
        hideAndShowButton.addActionListener(new ActionListener() {

            public boolean rightPanelShown;

            @Override
            public void actionPerformed(ActionEvent e) {
                rightPanel.setVisible(rightPanelShown);
                if (rightPanelShown) {
                    hideAndShowButton.setIcon(collapseIcon);
                    hideAndShowButton.setRolloverIcon(collapseRolloverIcon);
                    hideAndShowButton.setVisible(true);
                    hideAndShowButton.setToolTipText("Collapse Options Panel");
                } else {
                    hideAndShowButton.setIcon(expandIcon);
                    hideAndShowButton.setRolloverIcon(expandRolloverIcon);
                    hideAndShowButton.setVisible(true);
                    hideAndShowButton.setToolTipText("Expand Options Panel");
                }
                rightPanelShown = !rightPanelShown;
            }
        });

        hideAndShowButton.setVisible(true);


        contentPanel = new JPanel(new GridLayout(-1, 1));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.addMouseListener(popupHandler);

        contentScrollPane = new JScrollPane(contentPanel);
        contentScrollPane.setBorder(null);
        contentScrollPane.setBackground(Color.WHITE);


        spreadsheetPanel = new JPanel(new GridLayout(-1, 1));
        //    spreadsheetPanel.setBackground(Color.WHITE);
        spreadsheetPanel.addMouseListener(popupHandler);

        spreadsheetScrollPane = new JScrollPane(spreadsheetPanel);


        spreadsheetScrollPane.setBorder(null);
        //    spreadsheetScrollPane.setBackground(Color.WHITE);
        spreadsheetScrollPane.setMinimumSize(new Dimension(100, 100));
        spreadsheetScrollPane.setBorder(UIUtils.createGroupBorder("Statistics Spreadsheet"));
        spreadsheetScrollPane.setVisible(statisticsCriteriaPanel.showStatsSpreadSheet());


        leftPanel = new JPanel(new GridBagLayout());
//        GridBagConstraints gbcLeftPanel = new GridBagConstraints();
//        GridBagUtils.addToPanel(leftPanel, contentScrollPane, gbcLeftPanel, "fill=BOTH, weightx=1.0, weighty=1.0, anchor=NORTHWEST");
//        GridBagUtils.addToPanel(leftPanel, spreadsheetScrollPane, gbcLeftPanel, "fill=BOTH, weightx=1.0, weighty=0.0, anchor=NORTHWEST, gridy=1,insets.top=5");

        initLeftPanel();

        backgroundPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        GridBagUtils.addToPanel(backgroundPanel, leftPanel, gbc, "fill=BOTH, weightx=1.0, weighty=1.0, anchor=NORTHWEST,insets.right=5");
        //   GridBagUtils.addToPanel(backgroundPanel, contentScrollPane, gbc, "fill=BOTH, weightx=1.0, weighty=1.0, anchor=NORTHWEST");
        //  GridBagUtils.addToPanel(backgroundPanel, spreadsheetScrollPane, gbc, "fill=BOTH, weightx=1.0, weighty=1.0, anchor=NORTHWEST, gridy=1");
        GridBagUtils.addToPanel(backgroundPanel, rightPanel, gbc, "gridx=1,gridy=0, fill=BOTH, weightx=0.0,anchor=NORTHEAST,insets.left=5");


        //   GridBagUtils.addToPanel(backgroundPanel, spreadsheetScrollPane, gbcLeftPanel, "fill=HORIZONTAL, weightx=1.0, weighty=1.0, anchor=NORTHWEST, gridy=1,gridx=0,gridwidth=2,insets.top=5");
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.add(backgroundPanel, new Integer(0));
        layeredPane.add(hideAndShowButton, new Integer(1));
        add(layeredPane);


        int minWidth = leftPanel.getMinimumSize().width + rightPanel.getMinimumSize().width;
        int minHeight = Math.max(leftPanel.getMinimumSize().height, rightPanel.getMinimumSize().height);
        setMinimumSize(new Dimension(minWidth, minHeight));


    }




















    private JPanel getRightPanel() {


        computePanel = new MultipleRoiComputePanel(this, getRaster());


        final JPanel rightPanel = GridBagUtils.createPanel();

        JTabbedPane tabbedPane = new JTabbedPane();


        final JPanel mainPane = GridBagUtils.createPanel();

        //  GridBagConstraints extendedOptionsPanelConstraints = GridBagUtils.createConstraints("anchor=NORTHWEST,fill=HORIZONTAL,insets.top=2,weightx=1,insets.right=-2");
        GridBagConstraints extendedOptionsPanelConstraints = GridBagUtils.createConstraints("anchor=NORTHWEST,fill=HORIZONTAL,insets.top=2,weightx=1");


        GridBagUtils.addToPanel(rightPanel, computePanel, extendedOptionsPanelConstraints, "gridy=0,fill=NONE,weighty=1,weightx=1");


        JPanel optionsPanel = statisticsCriteriaPanel.getTextOptionsPanel();
        JPanel fieldsPanel = statisticsCriteriaPanel.getFieldOptionsPanel();
        JPanel binningCriteriaPanel = statisticsCriteriaPanel.getBinningCriteriaPanel();
        JPanel plotOptionsPanel = statisticsCriteriaPanel.getPlotsOptionsPanel();
        JPanel viewPanel = statisticsCriteriaPanel.getViewPanel();

        //    GridBagUtils.addToPanel(rightPanel, optionsPanel, extendedOptionsPanelConstraints, "gridy=1,fill=BOTH,weighty=0");

        //   GridBagUtils.addToPanel(rightPanel, binningCriteriaPanel, extendedOptionsPanelConstraints, "gridy=2,fill=BOTH,weighty=0");

        //  GridBagUtils.addToPanel(mainPane, computePanel, extendedOptionsPanelConstraints, "gridy=0,fill=NONE,weighty=1,weightx=1");
        GridBagUtils.addToPanel(mainPane, binningCriteriaPanel, extendedOptionsPanelConstraints, "gridy=1,fill=BOTH,weighty=0");


        tabbedPane.addTab("Bins", binningCriteriaPanel);
        tabbedPane.setToolTipTextAt(0, "Histogram statistics binning criteria");

        tabbedPane.addTab("Fields", fieldsPanel);
        tabbedPane.setToolTipTextAt(1, "Statistic fields to display within text and spreadsheet");

        tabbedPane.addTab("Text", optionsPanel);
        tabbedPane.setToolTipTextAt(2, "Text and spreadsheet formatting");

        tabbedPane.addTab("Plots", plotOptionsPanel);
        tabbedPane.setToolTipTextAt(3, "Plot formatting");

        tabbedPane.addTab("View", viewPanel);
        tabbedPane.setToolTipTextAt(4, "View options");

        GridBagUtils.addToPanel(rightPanel, tabbedPane, extendedOptionsPanelConstraints, "gridy=1,fill=BOTH,weighty=0, insets.top=10");


        exportButton = getExportButton();
        exportButton.setToolTipText("Export: This only exports the binning portion of the statistics");
        exportButton.setVisible(exportButtonVisible);

        final JPanel exportAndHelpPanel = GridBagUtils.createPanel();
        GridBagConstraints helpPanelConstraints = GridBagUtils.createConstraints("anchor=NORTHWEST,fill=HORIZONTAL,insets.top=2,weightx=1,ipadx=0");
        GridBagUtils.addToPanel(exportAndHelpPanel, new JSeparator(), helpPanelConstraints, "fill=HORIZONTAL,gridwidth=2,insets.left=5,insets.right=5");
        GridBagUtils.addToPanel(exportAndHelpPanel, exportButton, helpPanelConstraints, "gridy=1,anchor=WEST,fill=NONE");
        GridBagUtils.addToPanel(exportAndHelpPanel, getHelpButton(), helpPanelConstraints, "gridx=1,gridy=1,anchor=EAST,fill=NONE");

        GridBagUtils.addToPanel(rightPanel, exportAndHelpPanel, extendedOptionsPanelConstraints, "gridy=2,anchor=SOUTHWEST,fill=HORIZONTAL,weighty=0,insets.top=0");


        rightPanel.setMinimumSize(rightPanel.getPreferredSize());

        return rightPanel;
    }


    @Override
    protected void updateComponents() {
        if (computePanel.isRunning()) {
            if (swingWorker != null) {
                swingWorker.cancel(true);
            }
            computePanel.setRunning(false);
        }


        if (!init) {
            initComponents();
        }




        statisticsCriteriaPanel.reset();






        statsSpreadsheet = null;



        final RasterDataNode raster = getRaster();
        computePanel.setRaster(raster);
        contentPanel.removeAll();
        spreadsheetPanel.removeAll();
        resultText.setLength(0);


        if (raster != null && raster.isStxSet() && raster.getStx().getResolutionLevel() == 0) {

        //    percentThresholdsList = statisticsCriteriaPanel.getPercentThresholdsList();
         //   resultText.append(createText(raster.getStx(), null));
            contentPanel.add(createStatPanel(raster.getStx(), null, 1, getRaster()));

            PagePanel pagePanel = new StatisticsSpreadsheetPagePanel(parentDialog, helpID,statisticsCriteriaPanel, statsSpreadsheet, this);
            pagePanel.initComponents();
            spreadsheetPanel.add(pagePanel);

         //   spreadsheetPanel.add(statsSpreadsheetPanel());
            histograms = new Histogram[]{raster.getStx().getHistogram()};
            exportAsCsvAction = new ExportStatisticsAsCsvAction(this);
            putStatisticsIntoVectorDataAction = new PutStatisticsIntoVectorDataAction(this);
            exportButton.setEnabled(exportButtonEnabled);

        } else {
            contentPanel.add(new JLabel(DEFAULT_STATISTICS_TEXT));
            exportButton.setEnabled(false);
        }





        contentPanel.revalidate();
        contentPanel.repaint();
        spreadsheetScrollPane.setVisible(statisticsCriteriaPanel.showStatsSpreadSheet());
        spreadsheetPanel.revalidate();
        spreadsheetPanel.repaint();
        backgroundPanel.revalidate();
        backgroundPanel.repaint();

        if (raster != null) {
            exportButton.setEnabled(false);
        }
    }

    @Override
    public Histogram[] getHistograms() {
        return histograms;
    }

    private static class ComputeResult {

        final Stx stx;
        final Mask mask;

        ComputeResult(Stx stx, Mask mask) {
            this.stx = stx;
            this.mask = mask;
        }
    }

    @Override
    public void compute(final Mask[] selectedMasks, final Band[] selectedBands) {

        computePanel.setRunning(true);
        spreadsheetPanel.removeAll();
        fixedHistDomainAllPlotsInitialized = false;


        //todo Danny this is start of coding ability to select multiple bands
        int numMaskRows = selectedMasks.length * selectedBands.length;
        int numUnMaskedRows = (computePanel.isIncludeUnmasked()) ? selectedBands.length : 0;
        numRows = 1 + numMaskRows + numUnMaskedRows;
        numProductRegions = numRows - 1;

        this.histograms = new Histogram[numRows -1];
        final String title = "Computing Statistics";


        if (statisticsCriteriaPanel.getPercentThresholdsList() == null) {
            abortRun();
            return;
        }

        statsSpreadsheet = null;  // reset this

        if (!retrieveValidateTextFields(true)) {
            abortRun();
            return;
        }

        // just in case: should not get here as it should have been caught earlier
        if (!validFields()) {
            JOptionPane.showMessageDialog(getParentDialogContentPane(),
                    "Failed to compute statistics due to invalid fields",
                    "Invalid Input", /*I18N*/
                    JOptionPane.ERROR_MESSAGE);
            computePanel.setRunning(false);
            return;
        }



         swingWorker = new ProgressMonitorSwingWorker(this, title) {

            //   SwingWorker<Object, ComputeResult> swingWorker = new ProgressMonitorSwingWorker<Object, ComputeResult>(this, title) {

            @Override
            protected Object doInBackground(ProgressMonitor pm) {
                pm.beginTask(title, selectedMasks.length);
                try {
                    RasterDataNode raster = getRaster();






                    final int binCount = statisticsCriteriaPanel.getNumBins();
                    int histogramIdx = 0;

                   // for (int rasterIdx = 0; rasterIdx < bandNames.length; rasterIdx++) {
                    for (int rasterIdx = 0; rasterIdx < selectedBands.length; rasterIdx++) {
                        final Band band = selectedBands[rasterIdx];

                    //    raster = getProduct().getRasterDataNode(bandNames[rasterIdx]);
                        raster = getProduct().getRasterDataNode(band.getName());

                        if (computePanel.isIncludeUnmasked()) {
                            final Stx stx1;
                            ProgressMonitor subPm1 = SubProgressMonitor.create(pm, 1);

                            stx1 = new StxFactory()
                                    .withHistogramBinCount(binCount)
                                    .withLogHistogram(statisticsCriteriaPanel.isLogMode())
                                    .withMedian(statisticsCriteriaPanel.includeMedian())
                                    .withBinMin(statisticsCriteriaPanel.getBinMin())
                                    .withBinMax(statisticsCriteriaPanel.getBinMax())
                                    .withBinWidth(statisticsCriteriaPanel.getBinWidth())
                                    .create(raster, subPm1);

                            histograms[0] = stx1.getHistogram();

                            // publish(new ComputeResult(stx1, null));

                            JPanel statPanel = createStatPanel(stx1, null, currRow, raster);
                            contentPanel.add(statPanel);

                            updateLeftPanel();
                            currRow++;
                            histogramIdx++;
                        }


                        if (selectedMasks != null) {
                            for (int i = 0; i < selectedMasks.length; i++) {
                                final Mask mask = selectedMasks[i];

                                if (mask != null) {
                                    final Stx stx;
                                    ProgressMonitor subPm = SubProgressMonitor.create(pm, 1);

                                    stx = new StxFactory()
                                            .withHistogramBinCount(binCount)
                                            .withLogHistogram(statisticsCriteriaPanel.isLogMode())
                                            .withMedian(statisticsCriteriaPanel.includeMedian())
                                            .withBinMin(statisticsCriteriaPanel.getBinMin())
                                            .withBinMax(statisticsCriteriaPanel.getBinMax())
                                            .withBinWidth(statisticsCriteriaPanel.getBinWidth())
                                            .withRoiMask(mask)
                                            .create(raster, subPm);


                                    histograms[histogramIdx] = stx.getHistogram();

                                    // publish(new ComputeResult(stx, mask));

                                    JPanel statPanel = createStatPanel(stx, mask, currRow, raster);
                                    contentPanel.add(statPanel);
                                    updateLeftPanel();
                                    currRow++;
                                    histogramIdx++;
                                }
                            }
                        }
                    }


                } finally {
                    pm.done();
                    computePanel.setRunning(false);

                }
                return null;
            }

            // todo Danny Testing this
//            @Override
//            protected void process(List<ComputeResult> chunks) {
//
//
//
//                for (ComputeResult result : chunks) {
//
//                    final Stx stx = result.stx;
//                    final Mask mask = result.mask;
//
//                    if (resultText.length() > 0) {
//                        resultText.append("\n");
//                    }
//                    resultText.append(createText(stx, mask));
//
//                    JPanel statPanel = createStatPanel(stx, mask, currRow);
//                    contentPanel.add(statPanel);
//                    contentPanel.revalidate();
//                    contentPanel.repaint();
//                    backgroundPanel.revalidate();
//                    backgroundPanel.repaint();
//                    currRow++;
//                }
//
//
//            }

            @Override
            protected void done() {
                try {

                    double numRows = 1.0 + selectedMasks.length;

                    updateLeftPanel();

                    resultText.setLength(0);
                    resultText.append(createText());

//                    get();
//                    if (exportAsCsvAction == null) {
//                        exportAsCsvAction = new ExportStatisticsAsCsvAction(StatisticsPanel.this);
//                    }
//                    exportAsCsvAction.setSelectedMasks(selectedMasks);
//                    if (putStatisticsIntoVectorDataAction == null) {
//                        putStatisticsIntoVectorDataAction = new PutStatisticsIntoVectorDataAction(StatisticsPanel.this);
//                    }
//                    putStatisticsIntoVectorDataAction.setSelectedMasks(selectedMasks);
//                    //       exportButton.setEnabled(exportButtonEnabled);

                    currRow = 1;

                    computePanel.setRunning(false);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(getParentDialogContentPane(),
                            "Failed to compute statistics.\nAn error occurred:" + e.getMessage(),
                                                  /*I18N*/
                            "Statistics", /*I18N*/
                            JOptionPane.ERROR_MESSAGE);
                    computePanel.setRunning(false);
                }
            }
        };

        resultText.setLength(0);
        contentPanel.removeAll();

        // swingWorker.execute();
        swingWorker.executeWithBlocking();
    }


    private void abortRun() {
        initLeftPanel();
        fixedHistDomainAllPlotsInitialized = false;
        computePanel.setRunning(false);
    }


    private void initLeftPanel() {
        leftPanel.removeAll();
        leftPanel.add(new JLabel(DEFAULT_STATISTICS_TEXT));
        leftPanel.revalidate();
        leftPanel.repaint();
        // leftPanel.setBackground(Color.WHITE);
        fixedHistDomainAllPlotsInitialized = false;
    }

    private void updateLeftPanel() {

        PagePanel pagePanel = new StatisticsSpreadsheetPagePanel(parentDialog, helpID,statisticsCriteriaPanel, statsSpreadsheet, this);
        pagePanel.initComponents();

        spreadsheetPanel.removeAll();
      //  JPanel statsSpeadPanel = statsSpreadsheetPanel();
      //  spreadsheetPanel.add(statsSpeadPanel);
        spreadsheetPanel.add(pagePanel);
        //   spreadsheetPanel.setBackground(Color.WHITE);
        spreadsheetScrollPane.setVisible(statisticsCriteriaPanel.showStatsSpreadSheet());
        spreadsheetScrollPane.setMinimumSize(new Dimension(100, 100));


        leftPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

       // histogramPanel.setVisible(showHistogramPlots);

        if (statisticsCriteriaPanel.showPercentPlots() || statisticsCriteriaPanel.showHistogramPlots() || statisticsCriteriaPanel.showStatsList()) {

            if (numRows > spreadsheetMinRowsBeforeWeight) {
                gbc.weighty = 1.0 - spreadsheetHeightWeight;
                leftPanel.add(contentScrollPane, gbc);

                gbc.fill = GridBagConstraints.BOTH;
                gbc.weighty = spreadsheetHeightWeight;

            } else {
                leftPanel.add(contentScrollPane, gbc);

                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.weighty = 0;

                int buffer = 50;
                int minHeight = spreadsheetPanel.getPreferredSize().height + buffer;
                spreadsheetScrollPane.setMinimumSize(new Dimension(100, minHeight));
            }

            gbc.gridy += 1;
            gbc.insets.top = 10;


        } else {
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 1.0;
        }

        if (statisticsCriteriaPanel.showStatsSpreadSheet()) {
            leftPanel.add(spreadsheetScrollPane, gbc);
        }


//        gbc.gridy += 1;
//        leftPanel.add(pagePanel, gbc);

        leftPanel.revalidate();
        leftPanel.repaint();

        contentPanel.revalidate();
        contentPanel.repaint();
        backgroundPanel.revalidate();
        backgroundPanel.repaint();

    }


    private JPanel createStatPanel(Stx stx, final Mask mask, int row, RasterDataNode raster) {

        final Histogram histogram = stx.getHistogram();

        XIntervalSeries histogramSeries = new XIntervalSeries("Histogram");
        double histDomainBounds[] = {histogram.getLowValue(0), histogram.getHighValue(0)};
        double histRangeBounds[] = {Double.NaN, Double.NaN};

        if (!fixedHistDomainAllPlots || (fixedHistDomainAllPlots && !fixedHistDomainAllPlotsInitialized)) {
            if (!statisticsCriteriaPanel.isLogMode()) {
                if (statisticsCriteriaPanel.plotsThreshDomainSpan()) {

                    if (statisticsCriteriaPanel.plotsThreshDomainLow() >= 0.1) {
                        histDomainBounds[0] = histogram.getPTileThreshold((statisticsCriteriaPanel.plotsThreshDomainLow()) / 100)[0];
                    }

                    if (statisticsCriteriaPanel.plotsThreshDomainHigh() <= 99.9) {
                        histDomainBounds[1] = histogram.getPTileThreshold(statisticsCriteriaPanel.plotsThreshDomainHigh() / 100)[0];
                    }

                } else if (statisticsCriteriaPanel.plotsDomainSpan()) {
                    if (!Double.isNaN(statisticsCriteriaPanel.plotsDomainLow())) {
                        histDomainBounds[0] = statisticsCriteriaPanel.plotsDomainLow();
                    }
                    if (!Double.isNaN(statisticsCriteriaPanel.plotsDomainHigh())) {
                        histDomainBounds[1] = statisticsCriteriaPanel.plotsDomainHigh();
                    }
                }

            } else {
                histDomainBounds[0] = histogram.getBinLowValue(0, 0);
                histDomainBounds[1] = histogram.getHighValue(0);
            }


//            if (!LogMode && plotsThreshDomainSpan && plotsThreshDomainLow >= 0.1 && plotsThreshDomainHigh <= 99.9) {
//                histDomainBounds[0] = histogram.getPTileThreshold((plotsThreshDomainLow) / 100)[0];
//                histDomainBounds[1] = histogram.getPTileThreshold(plotsThreshDomainHigh / 100)[0];
//
//            } else {
//                histDomainBounds[0] = histogram.getBinLowValue(0, 0);
//                histDomainBounds[1] = histogram.getHighValue(0);
//            }

            if (fixedHistDomainAllPlots && !fixedHistDomainAllPlotsInitialized) {
                histDomainBoundsAllPlots[0] = histDomainBounds[0];
                histDomainBoundsAllPlots[1] = histDomainBounds[1];
                fixedHistDomainAllPlotsInitialized = true;
            }
        } else {
            histDomainBounds[0] = histDomainBoundsAllPlots[0];
            histDomainBounds[1] = histDomainBoundsAllPlots[1];
        }


        int[] bins = histogram.getBins(0);
        for (int j = 0; j < bins.length; j++) {

            histogramSeries.add(histogram.getBinLowValue(0, j),
                    histogram.getBinLowValue(0, j),
                    j < bins.length - 1 ? histogram.getBinLowValue(0, j + 1) : histogram.getHighValue(0),
                    bins[j]);
        }


        String logTitle = (statisticsCriteriaPanel.isLogMode()) ? "Log10 of " : "";

        ChartPanel histogramPanel = createChartPanel(histogramSeries,
                logTitle + raster.getName() + " (" + raster.getUnit() + ")",
                "Frequency in #Pixels",
                new Color(0, 0, 127),
                histDomainBounds, histRangeBounds);


        //  histogramPanel.setPreferredSize(new Dimension(300, 200));


        if (statisticsCriteriaPanel.exactPlotSize()) {
            histogramPanel.setMinimumSize(new Dimension(statisticsCriteriaPanel.plotSizeWidth(), statisticsCriteriaPanel.plotSizeHeight()));
            histogramPanel.setPreferredSize(new Dimension(statisticsCriteriaPanel.plotSizeWidth(), statisticsCriteriaPanel.plotSizeHeight()));
            histogramPanel.setMaximumSize(new Dimension(statisticsCriteriaPanel.plotSizeWidth(), statisticsCriteriaPanel.plotSizeHeight()));
        } else {
            histogramPanel.setMinimumSize(new Dimension(plotMinWidth, plotMinHeight));
            histogramPanel.setPreferredSize(new Dimension(plotMinWidth, plotMinHeight));
        }

        XIntervalSeries percentileSeries = new XIntervalSeries("Percentile");

//        if (1 == 2 && LogMode) {
//            percentileSeries.add(0,
//                    0,
//                    1,
//                    Math.pow(10, histogram.getLowValue(0)));
//            for (int j = 1; j < 99; j++) {
//                percentileSeries.add(j,
//                        j,
//                        j + 1,
//                        Math.pow(10, histogram.getPTileThreshold(j / 100.0)[0]));
//            }
//            percentileSeries.add(99,
//                    99,
//                    100,
//                    Math.pow(10, histogram.getHighValue(0)));
//
//        } else {
//            percentileSeries.add(0,
//                    0,
//                    0.25,
//                    histogram.getLowValue(0));
//
//            for (double j = 0.25; j < 99.75; j += .25) {
//                percentileSeries.add(j,
//                        j,
//                        j + 1,
//                        histogram.getPTileThreshold(j / 100.0)[0]);
//            }
//            percentileSeries.add(99.75,
//                    99.75,
//                    100,
//                    histogram.getHighValue(0));
//        }


//
//        double fraction = 0;
//        for (int j = 0; j < bins.length; j++) {
//
//             fraction = (1.0) * j / bins.length;
//
//            if (fraction > 0 && fraction < 1) {
//                percentileSeries.add(histogram.getBinLowValue(0, j),
//                        histogram.getBinLowValue(0, j),
//                        j < bins.length - 1 ? histogram.getBinLowValue(0, j + 1) : histogram.getHighValue(0),
//                        histogram.getPTileThreshold(fraction)[0]);
//            }
//
//
//        }
//
//        double test = fraction;


        double[] percentileDomainBounds = {Double.NaN, Double.NaN};
        double[] percentileRangeBounds = {Double.NaN, Double.NaN};
        ChartPanel percentilePanel = null;

        if (invertPercentile) {

            double increment = .01;
            for (double j = 0; j < 100; j += increment) {
                double fraction = j / 100.0;
                double nextFraction = (j + increment) / 100.0;

                if (fraction > 0.0 && fraction < 1.0 && nextFraction > 0.0 && nextFraction < 1.0) {
                    double thresh = histogram.getPTileThreshold(fraction)[0];
                    double nextThresh = histogram.getPTileThreshold(nextFraction)[0];

                    percentileSeries.add(thresh,
                            thresh,
                            nextThresh,
                            j);
                }
            }


            if (!statisticsCriteriaPanel.isLogMode()) {
                percentileDomainBounds[0] = histDomainBounds[0];
                percentileDomainBounds[1] = histDomainBounds[1];
            }
            percentileRangeBounds[0] = 0;
            percentileRangeBounds[1] = 100;

            percentilePanel = createScatterChartPanel(percentileSeries, logTitle + raster.getName() + " (" + raster.getUnit() + ")", "Percent Threshold", new Color(0, 0, 0), percentileDomainBounds, percentileRangeBounds);

        } else {
            percentileSeries.add(0,
                    0,
                    0.25,
                    histogram.getLowValue(0));

            for (double j = 0.25; j < 99.75; j += .25) {
                percentileSeries.add(j,
                        j,
                        j + 1,
                        histogram.getPTileThreshold(j / 100.0)[0]);
            }
            percentileSeries.add(99.75,
                    99.75,
                    100,
                    histogram.getHighValue(0));


            percentileDomainBounds[0] = 0;
            percentileDomainBounds[1] = 100;
            percentileRangeBounds[0] = histDomainBounds[0];
            percentileRangeBounds[1] = histDomainBounds[1];

            percentilePanel = createScatterChartPanel(percentileSeries, "Percent Threshold", logTitle + raster.getName() + " (" + raster.getUnit() + ")", new Color(0, 0, 0), percentileDomainBounds, percentileRangeBounds);


        }

        //   percentilePanel.setPreferredSize(new Dimension(300, 200));
        if (statisticsCriteriaPanel.exactPlotSize()) {
            percentilePanel.setMinimumSize(new Dimension(statisticsCriteriaPanel.plotSizeWidth(), statisticsCriteriaPanel.plotSizeHeight()));
            percentilePanel.setPreferredSize(new Dimension(statisticsCriteriaPanel.plotSizeWidth(), statisticsCriteriaPanel.plotSizeHeight()));
            percentilePanel.setMaximumSize(new Dimension(statisticsCriteriaPanel.plotSizeWidth(), statisticsCriteriaPanel.plotSizeHeight()));
        } else {
            percentilePanel.setMinimumSize(new Dimension(plotMinWidth, plotMinHeight));
            percentilePanel.setPreferredSize(new Dimension(plotMinWidth, plotMinHeight));
        }

        int size = raster.getRasterHeight() * raster.getRasterWidth();


        int dataRows = 0;

//                new Object[]{"RasterSize(Pixels)", size},
//                new Object[]{"SampleSize(Pixels)", histogram.getTotals()[0]},
        Object[][] firstData =
                new Object[][]{
                        new Object[]{"Pixels", histogram.getTotals()[0]},
                        new Object[]{"Minimum", stx.getMinimum()},
                        new Object[]{"Maximum", stx.getMaximum()},
                        new Object[]{"Mean", stx.getMean()}
                };
        dataRows += firstData.length;


        Object[] medianObject = null;

        if (statisticsCriteriaPanel.includeMedian()) {
            medianObject = new Object[]{"Median", stx.getMedianRaster()};

            dataRows++;
        }


        Object[][] secondData =
                new Object[][]{
                        new Object[]{"StandardDeviation", stx.getStandardDeviation()},
                        new Object[]{"CoefficientOfVariation", getCoefficientOfVariation(stx)},
                        //     new Object[]{"", ""},
                        new Object[]{"TotalBins", histogram.getNumBins()[0]},
                        new Object[]{"BinWidth", getBinSize(histogram)},
                        new Object[]{"BinMin", histogram.getLowValue(0)},
                        new Object[]{"BinMax", histogram.getHighValue(0)}
                };
        dataRows += secondData.length;


        Object[][] histogramStats = null;
        if (statisticsCriteriaPanel.includeHistogramStats()) {
            if (statisticsCriteriaPanel.isLogMode()) {
                histogramStats = new Object[][]{
                        new Object[]{"Mean(LogBinned)", Math.pow(10, histogram.getMean()[0])},
                        new Object[]{"Median(LogBinned)", Math.pow(10, stx.getMedian())},
                        new Object[]{"StandardDeviation(LogBinned)", Math.pow(10, histogram.getStandardDeviation()[0])}
                };
            } else {
                histogramStats = new Object[][]{
                        new Object[]{"Mean(Binned)", histogram.getMean()[0]},
                        new Object[]{"Median(Binned)", stx.getMedian()},
                        new Object[]{"StandardDeviation(Binned)", histogram.getStandardDeviation()[0]}
                };
            }
            dataRows += histogramStats.length;
        }


        Object[][] percentData = new Object[statisticsCriteriaPanel.getPercentThresholdsList().size()][];
        for (int i = 0; i < statisticsCriteriaPanel.getPercentThresholdsList().size(); i++) {
            int value = statisticsCriteriaPanel.getPercentThresholdsList().get(i);
            double percent = value / 100.0;
            String percentString = Integer.toString(value);

            Object[] pTileThreshold;
            if (statisticsCriteriaPanel.isLogMode()) {
                pTileThreshold = new Object[]{percentString + "%Threshold(Log)", Math.pow(10, histogram.getPTileThreshold(percent)[0])};
            } else {
                pTileThreshold = new Object[]{percentString + "%Threshold", histogram.getPTileThreshold(percent)[0]};
            }
            percentData[i] = pTileThreshold;
        }
        dataRows += percentData.length;


        Object[][] tableData = new Object[dataRows][];
        int tableDataIdx = 0;

        if (firstData != null) {
            for (int i = 0; i < firstData.length; i++) {
                tableData[tableDataIdx] = firstData[i];
                tableDataIdx++;
            }
        }

        if (medianObject != null) {
            tableData[tableDataIdx] = medianObject;
            tableDataIdx++;
        }

        if (secondData != null) {
            for (int i = 0; i < secondData.length; i++) {
                tableData[tableDataIdx] = secondData[i];
                tableDataIdx++;
            }
        }


        if (histogramStats != null) {
            for (int i = 0; i < histogramStats.length; i++) {
                tableData[tableDataIdx] = histogramStats[i];
                tableDataIdx++;
            }
        }

        if (percentData != null) {
            for (int i = 0; i < percentData.length; i++) {
                //  int tableDataIndex = i + firstData.length;
                tableData[tableDataIdx] = percentData[i];
                tableDataIdx++;
            }
        }


        if (statsSpreadsheet == null) {
            numStatisticFields = tableData.length;

            int numCols = numStatisticFields + 3; // Add 3 cols  "File", "Band" and "Mask(ROI)"
          //  int numRows = numProductRegions + 1; // Add header row

            statsSpreadsheet = new Object[numRows][numCols];
        }


        // Add Headers
        if (row <= 1) {
            statsSpreadsheet[0][0] = "File";
            statsSpreadsheet[0][1] = "Band";
            statsSpreadsheet[0][2] = "Mask(ROI)";


            for (int i = 0; i < tableData.length; i++) {
                Object value = tableData[i][0];

                int k = i + 3;
                if (k < statsSpreadsheet[0].length) {
                    statsSpreadsheet[0][k] = value;
                }
            }
        }


        if (row < statsSpreadsheet.length) {
            statsSpreadsheet[row][0] = getProduct().getName();
            statsSpreadsheet[row][1] = raster.getName();
            statsSpreadsheet[row][2] = (mask != null) ? mask.getName() : "";
            for (int i = 0; i < tableData.length; i++) {
                Object value = tableData[i][1];

                int k = i + 3;
                if (k < statsSpreadsheet[row].length) {
                    statsSpreadsheet[row][k] = value;
                }
            }
        }

        // todo Danny stats spreadsheet too big

        int numPlots = 0;
        if (statisticsCriteriaPanel.showPercentPlots()) {
            numPlots++;
        }

        if (statisticsCriteriaPanel.showHistogramPlots()) {
            numPlots++;
        }

        JPanel plotContainerPanel = null;

        if (numPlots > 0) {
            plotContainerPanel = new JPanel(new GridLayout(1, numPlots));

            if (statisticsCriteriaPanel.showHistogramPlots()) {
                plotContainerPanel.add(histogramPanel);
            }

            if (statisticsCriteriaPanel.showPercentPlots()) {
                plotContainerPanel.add(percentilePanel);
            }
        }


        TableModel tableModel = new DefaultTableModel(tableData, new String[]{"Name", "Value"}) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? String.class : Number.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };


        final JTable table = new JTable(tableModel);
        table.setDefaultRenderer(Number.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                final Component label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Float || value instanceof Double) {
                    setHorizontalTextPosition(RIGHT);
                    setText(getFormattedValue((Number) value));
                }
                return label;
            }

            private String getFormattedValue(Number value) {
                if (value.doubleValue() < 0.001 && value.doubleValue() > -0.001 && value.doubleValue() != 0.0) {
                    return new DecimalFormat("0.####E0").format(value.doubleValue());
                }
                String format = "%." + Integer.toString(statisticsCriteriaPanel.decimalPlaces()) + "f";

                return String.format(format, value.doubleValue());
            }
        });
        table.addMouseListener(popupHandler);


        // TEST CODE generically preferred size of each column based on longest expected entry
        // fails a bit because decimal formatting is not captured
        // stub of code commented out in case we want to make it work
        // meanwhile longest entry is being used SEE below

//        int column0Length = 0;
//        int column1Length = 0;
//        FontMetrics fm = table.getFontMetrics(table.getFont());
//        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
//            String test = table.getValueAt(rowIndex,0).toString();
//            int currColumn0Length = fm.stringWidth(table.getValueAt(rowIndex,0).toString());
//            if (currColumn0Length > column0Length) {
//                column0Length = currColumn0Length;
//            }
//
//            String test2 = table.getValueAt(rowIndex,1).toString();
//            int currColumn1Length = fm.stringWidth(table.getValueAt(rowIndex,1).toString());
//            if (currColumn1Length > column1Length) {
//                column1Length = currColumn1Length;
//            }
//        }


        // Set preferred size of each column based on longest expected entry
        FontMetrics fm = table.getFontMetrics(table.getFont());
        TableColumn column = null;
        int col1PreferredWidth = -1;
        if (statisticsCriteriaPanel.isLogMode()) {
            col1PreferredWidth = fm.stringWidth("StandardDeviation(LogBinned):") + 10;
        } else {
            col1PreferredWidth = fm.stringWidth("StandardDeviation(Binned):") + 10;
        }


        // int col1PreferredWidth = fm.stringWidth("wwwwwwwwwwwwwwwwwwwwwwwwww");
        int col2PreferredWidth = fm.stringWidth("1234567890") + 10;
        int tablePreferredWidth = col1PreferredWidth + col2PreferredWidth;
        for (int i = 0; i < 2; i++) {
            column = table.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(col1PreferredWidth);
                column.setMaxWidth(col1PreferredWidth);
            } else {
                column.setPreferredWidth(col2PreferredWidth);
            }
        }


        JPanel textContainerPanel = new JPanel(new BorderLayout(2, 2));
        //   textContainerPanel.setBackground(Color.WHITE);
        textContainerPanel.add(table, BorderLayout.CENTER);
        textContainerPanel.addMouseListener(popupHandler);

        JPanel statsPane = GridBagUtils.createPanel();
        GridBagConstraints gbc = GridBagUtils.createConstraints("");
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.weightx = 1;
        gbc.weighty = 1;

        Dimension dim = table.getPreferredSize();
        table.setPreferredSize(new Dimension(tablePreferredWidth, dim.height));
        statsPane.add(table, gbc);
        statsPane.setPreferredSize(new Dimension(tablePreferredWidth, dim.height));

        JPanel plotsPane = null;

        if (plotContainerPanel != null) {
            plotsPane = GridBagUtils.createPanel();
            plotsPane.setBackground(Color.WHITE);
            //    plotsPane.setBorder(UIUtils.createGroupBorder(" ")); /*I18N*/
            GridBagConstraints gbcPlots = GridBagUtils.createConstraints("");
            gbcPlots.gridy = 0;
            if (statisticsCriteriaPanel.exactPlotSize()) {
                gbcPlots.fill = GridBagConstraints.NONE;
            } else {
                gbcPlots.fill = GridBagConstraints.BOTH;
            }

            gbcPlots.anchor = GridBagConstraints.NORTHWEST;
            gbcPlots.weightx = 0.5;
            gbcPlots.weighty = 1;
            plotsPane.add(plotContainerPanel, gbcPlots);
        }


        JPanel mainPane = GridBagUtils.createPanel();
        mainPane.setBorder(UIUtils.createGroupBorder(getSubPanelTitle(mask, raster))); /*I18N*/
        GridBagConstraints gbcMain = GridBagUtils.createConstraints("");
        gbcMain.gridx = 0;
        gbcMain.gridy = 0;
        gbcMain.anchor = GridBagConstraints.NORTHWEST;
        if (plotsPane != null) {
            gbcMain.fill = GridBagConstraints.VERTICAL;
            gbcMain.weightx = 0;
        } else {
            gbcMain.fill = GridBagConstraints.BOTH;
            gbcMain.weightx = 1;
        }

        if (statisticsCriteriaPanel.showStatsList()) {
            gbcMain.weighty = 1;
            mainPane.add(statsPane, gbcMain);
            gbcMain.gridx++;
        }


        gbcMain.weightx = 1;
        gbcMain.weighty = 1;
        gbcMain.fill = GridBagConstraints.BOTH;


        if (plotsPane != null) {
            mainPane.add(plotsPane, gbcMain);
        }


        return mainPane;
    }

    static double getBinSize(Histogram histogram) {
        return (histogram.getHighValue(0) - histogram.getLowValue(0)) / histogram.getNumBins(0);
    }

    static double getBinSizeLogMode(Histogram histogram) {
        return (Math.pow(10, histogram.getHighValue(0)) - Math.pow(10, histogram.getLowValue(0))) / histogram.getNumBins(0);
    }

    private String getSubPanelTitle(Mask mask, RasterDataNode raster) {
        final String title;
        if (mask != null) {
            title = String.format("<html><b>%s</b> with ROI-mask <b>%s</b></html>", raster.getName(), mask.getName());
        } else {
            title = String.format("<html><b>%s</b></html>", raster.getName());
        }
        return title;
    }

    @Override
    protected String getDataAsText() {
        return resultText.toString();
    }


    // todo This part has not been updated by Danny
    private String createText(final Stx stx, final Mask mask) {

        if (stx.getSampleCount() == 0) {
            if (mask != null) {
                return "The ROI-Mask '" + mask.getName() + "' is empty.";
            } else {
                return "The scene contains no valid pixels.";
            }
        }

        RasterDataNode raster = getRaster();
        boolean maskUsed = mask != null;
        final String unit = (StringUtils.isNotNullAndNotEmpty(raster.getUnit()) ? raster.getUnit() : "1");
        final long numPixelTotal = (long) raster.getSceneRasterWidth() * (long) raster.getSceneRasterHeight();
        final StringBuilder sb = new StringBuilder(1024);

        sb.append("Only ROI-mask pixels considered:\t");
        sb.append(maskUsed ? "Yes" : "No");
        sb.append("\n");

        if (maskUsed) {
            sb.append("ROI-mask name:\t");
            sb.append(mask.getName());
            sb.append("\n");
        }

        sb.append("Number of pixels total:\t");
        sb.append(numPixelTotal);
        sb.append("\n");

        sb.append("Number of considered pixels:\t");
        sb.append(stx.getSampleCount());
        sb.append("\n");

        sb.append("Ratio of considered pixels:\t");
        sb.append(100.0 * stx.getSampleCount() / numPixelTotal);
        sb.append("\t");
        sb.append("%");
        sb.append("\n");

        sb.append("Minimum:\t");
        sb.append(stx.getMinimum());
        sb.append("\t");
        sb.append(unit);
        sb.append("\n");

        sb.append("Maximum:\t");
        sb.append(stx.getMaximum());
        sb.append("\t");
        sb.append(unit);
        sb.append("\n");

        sb.append("Mean:\t");
        sb.append(stx.getMean());
        sb.append("\t");
        sb.append(unit);
        sb.append("\n");

        sb.append("Standard deviation:\t");
        sb.append(stx.getStandardDeviation());
        sb.append("\t");
        sb.append(unit);
        sb.append("\n");

        sb.append("Coefficient of variation:\t");
        sb.append(getCoefficientOfVariation(stx));
        sb.append("\t");
        sb.append("");
        sb.append("\n");

        sb.append("Bin Median:\t");
        sb.append(stx.getMedianRaster());
        sb.append("\t ");
        sb.append(unit);
        sb.append("\n");

        for (int percentile = 5; percentile <= 95; percentile += 5) {
            sb.append("P").append(percentile).append(" threshold:\t");
            sb.append(stx.getHistogram().getPTileThreshold(percentile / 100.0)[0]);
            sb.append("\t");
            sb.append(unit);
            sb.append("\n");
        }

        sb.append("Threshold max error:\t");
        sb.append(getBinSize(stx.getHistogram()));
        sb.append("\t");
        sb.append(unit);
        sb.append("\n");

        return sb.toString();
    }


    private String createText() {

        if (statsSpreadsheet == null || statsSpreadsheet.length == 0 || statsSpreadsheet[0].length == 0) {
            return "No Statistics Processed";
        }

        final StringBuilder sb = new StringBuilder();



        int numRows = statsSpreadsheet.length;
        int numCols = statsSpreadsheet[0].length;

        for (int rowIdx = 1; rowIdx < statsSpreadsheet.length; rowIdx++) {

            for (int colIdx = 0; colIdx < statsSpreadsheet[0].length; colIdx++) {
                Object valueObject = statsSpreadsheet[rowIdx][colIdx];
                Object fieldObject = statsSpreadsheet[0][colIdx];

                if (valueObject == null || fieldObject == null) {
                    sb.append("");
                } else {
//                    if (colIdx == 0) {
//                       String filename = getProduct().getName();
//                        sb.append("File: " + filename + "\n");
//                    }


                    String field = fieldObject.toString();
                    sb.append(field + ": ");

                    if (valueObject instanceof Float || valueObject instanceof Double) {
                        String valueFormatted = getFormattedValue((Number) valueObject);
                        sb.append(valueFormatted);
                    } else {

                        sb.append(valueObject.toString());
                    }
                }

                if (colIdx < statsSpreadsheet[0].length - 1) {
                    sb.append("\n");
                }

            }

            sb.append("\n\n");
        }

        return sb.toString();
    }



    private double getCoefficientOfVariation(Stx stx) {
        return stx.getStandardDeviation() / stx.getMean();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        backgroundPanel.setBounds(0, 0, getWidth() - 8, getHeight() - 8);
        hideAndShowButton.setBounds(getWidth() - hideAndShowButton.getWidth() - 12, 6, 24, 24);
    }


    private static ChartPanel createChartPanel(XIntervalSeries percentileSeries, String xAxisLabel, String yAxisLabel, Color color, double domainBounds[], double rangeBounds[]) {
        XIntervalSeriesCollection percentileDataset = new XIntervalSeriesCollection();
        percentileDataset.addSeries(percentileSeries);
        return getHistogramPlotPanel(percentileDataset, xAxisLabel, yAxisLabel, color, domainBounds, rangeBounds);
    }

    private static ChartPanel createScatterChartPanel(XIntervalSeries percentileSeries, String xAxisLabel, String yAxisLabel, Color color, double domainBounds[], double rangeBounds[]) {
        XIntervalSeriesCollection percentileDataset = new XIntervalSeriesCollection();
        percentileDataset.addSeries(percentileSeries);
        return getScatterPlotPanel(percentileDataset, xAxisLabel, yAxisLabel, color, domainBounds, rangeBounds);
    }

    private static ChartPanel getHistogramPlotPanel(XIntervalSeriesCollection dataset, String xAxisLabel, String yAxisLabel, Color color, double domainBounds[], double rangeBounds[]) {
        JFreeChart chart = ChartFactory.createHistogram(
                null,
                xAxisLabel,
                yAxisLabel,
                dataset,
                PlotOrientation.VERTICAL,
                false,  // Legend?
                true,   // tooltips
                false   // url
        );
        final XYPlot xyPlot = chart.getXYPlot();
        //xyPlot.setForegroundAlpha(0.85f);
        xyPlot.setNoDataMessage("No data");
        xyPlot.setAxisOffset(new RectangleInsets(5, 5, 5, 10));
        // xyPlot.setInsets(new RectangleInsets(0,0,0,0));

        // todo Danny set bounds here

//        if (domainBounds[0] != domainBounds[1]) {
//            xyPlot.getDomainAxis().setLowerBound(domainBounds[0]);
//            xyPlot.getDomainAxis().setUpperBound(domainBounds[1]);
//        }
//
//        if (rangeBounds[0] != rangeBounds[1]) {
//            xyPlot.getRangeAxis().setLowerBound(rangeBounds[0]);
//            xyPlot.getRangeAxis().setUpperBound(rangeBounds[1]);
//        }


        if (!Double.isNaN(domainBounds[0])) {
            xyPlot.getDomainAxis().setLowerBound(domainBounds[0]);
        }

        if (!Double.isNaN(domainBounds[1])) {
            xyPlot.getDomainAxis().setUpperBound(domainBounds[1]);
        }

        if (!Double.isNaN(rangeBounds[0])) {
            xyPlot.getRangeAxis().setLowerBound(rangeBounds[0]);
        }

        if (!Double.isNaN(rangeBounds[1])) {
            xyPlot.getRangeAxis().setUpperBound(rangeBounds[1]);
        }


        final XYBarRenderer renderer = (XYBarRenderer) xyPlot.getRenderer();
        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, color);
        StandardXYBarPainter painter = new StandardXYBarPainter();
        renderer.setBarPainter(painter);

        ChartPanel chartPanel = new ChartPanel(chart);
//// todo Danny testing out height/width ratio preservation
//        double histChartHeightWidthRatio = chartPanel.getPreferredSize().height / chartPanel.getPreferredSize().width;
//        double plotSizeReduction = 1;
//        Number preferredHeight = chartPanel.getPreferredSize().height * plotSizeReduction;
//        Number preferredWidth = chartPanel.getPreferredSize().width * plotSizeReduction;
//
//        chartPanel.setPreferredSize(new Dimension(preferredWidth.intValue(), preferredHeight.intValue()));

        //  chartPanel.setPreferredSize(new Dimension(300, 200));

//        chartPanel.getPopupMenu().add(createCopyDataToClipboardMenuItem());
        return chartPanel;
    }

    private static ChartPanel getScatterPlotPanel(XIntervalSeriesCollection dataset, String xAxisLabel, String yAxisLabel, Color color, double domainBounds[], double rangeBounds[]) {
        //  JFreeChart chart = ChartFactory.createScatterPlot(
        JFreeChart chart = ChartFactory.createXYLineChart(
                null,
                xAxisLabel,
                yAxisLabel,
                dataset,
                PlotOrientation.VERTICAL,
                false,  // Legend?
                true,   // tooltips
                false   // url
        );
        final XYPlot xyPlot = chart.getXYPlot();
        //   xyPlot.setForegroundAlpha(0.85f);
        xyPlot.setBackgroundAlpha(0.0f);
        xyPlot.setNoDataMessage("No data");
        xyPlot.setAxisOffset(new RectangleInsets(5, 5, 5, 10));


        // todo Danny set bounds here

//        if (domainBounds[0] != domainBounds[1]) {
//            xyPlot.getDomainAxis().setLowerBound(domainBounds[0]);
//            xyPlot.getDomainAxis().setUpperBound(domainBounds[1]);
//        }
//
//        if (rangeBounds[0] != rangeBounds[1]) {
//            xyPlot.getRangeAxis().setLowerBound(rangeBounds[0]);
//            xyPlot.getRangeAxis().setUpperBound(rangeBounds[1]);
//        }

        if (!Double.isNaN(domainBounds[0])) {
            xyPlot.getDomainAxis().setLowerBound(domainBounds[0]);
        }

        if (!Double.isNaN(domainBounds[1])) {
            xyPlot.getDomainAxis().setUpperBound(domainBounds[1]);
        }

        if (!Double.isNaN(rangeBounds[0])) {
            xyPlot.getRangeAxis().setLowerBound(rangeBounds[0]);
        }

        if (!Double.isNaN(rangeBounds[1])) {
            xyPlot.getRangeAxis().setUpperBound(rangeBounds[1]);
        }



        final XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) xyPlot.getRenderer();
        renderer.setSeriesPaint(0, color);
        renderer.setUseFillPaint(true);
        renderer.setDrawOutlines(true);
        renderer.setSeriesShapesFilled(0, true);
        renderer.setSeriesFillPaint(0, color);


        ChartPanel chartPanel = new ChartPanel(chart);
        //    chartPanel.setPreferredSize(new Dimension(300, 200));

        return chartPanel;
    }


    private AbstractButton getExportButton() {
        final AbstractButton export = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Export24.gif"),
                false);
        export.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPopupMenu viewPopup = new JPopupMenu("Export");
                viewPopup.add(exportAsCsvAction);
                viewPopup.add(putStatisticsIntoVectorDataAction);
                final Rectangle buttonBounds = export.getBounds();
                viewPopup.show(export, 1, buttonBounds.height + 1);
            }
        });
        export.setEnabled(false);
        return export;
    }

    @Override
    public RasterDataNode getRasterDataNode() {
        return getRaster();
    }

    @Override
    public ProductNodeGroup<VectorDataNode> getVectorDataNodeGroup() {
        return getRasterDataNode().getProduct().getVectorDataGroup();
    }

    private class PopupHandler extends MouseAdapter {

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == 2 || e.isPopupTrigger()) {
                final JPopupMenu menu = new JPopupMenu();
                menu.add(createCopyDataToClipboardMenuItem());
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    // The fields of this class are used by the binding framework
    @SuppressWarnings("UnusedDeclaration")
    static class AccuracyModel {

        private int accuracy = 3;
        private boolean useAutoAccuracy = true;
    }



    private boolean retrieveValidateTextFields(boolean showDialog) {

       if (!statisticsCriteriaPanel.validatePrepare()) {
           return false;
       }

        return true;
    }




    private String getFormattedValue(Number value) {
        if (value.doubleValue() < 0.001 && value.doubleValue() > -0.001 && value.doubleValue() != 0.0) {
            return new DecimalFormat("0.####E0").format(value.doubleValue());
        }
        String format = "%." + Integer.toString(statisticsCriteriaPanel.decimalPlaces()) + "f";

        return String.format(format, value.doubleValue());
    }


    private boolean validFields() {
//        if (!validNumBins()) {
//            return false;
//        }

        return true;
    }



}




