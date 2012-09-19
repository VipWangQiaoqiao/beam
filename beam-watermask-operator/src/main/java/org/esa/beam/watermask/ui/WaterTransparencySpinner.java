package org.esa.beam.watermask.ui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.text.DecimalFormat;

/**
 * Created with IntelliJ IDEA.
 * User: knowles
 * Date: 9/4/12
 * Time: 9:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class WaterTransparencySpinner {

    private AuxiliaryMasksData auxiliaryMasksData;

    private JLabel jLabel;
    private JSpinner jSpinner = new JSpinner();


    public WaterTransparencySpinner(AuxiliaryMasksData auxiliaryMasksData) {

        this.auxiliaryMasksData = auxiliaryMasksData;

        jLabel = new JLabel("Transparency");
        jLabel.setToolTipText("Water mask transparency");

        jSpinner.setModel(new SpinnerNumberModel(100, 0, 100, 100));

        jSpinner.setPreferredSize(jSpinner.getPreferredSize());
        jSpinner.setSize(jSpinner.getPreferredSize());

        jSpinner.setModel(new SpinnerNumberModel(auxiliaryMasksData.getWaterMaskTransparency(), 0.0, 1.0, 0.1));

        JSpinner.NumberEditor editor = (JSpinner.NumberEditor) jSpinner.getEditor();
        DecimalFormat format = editor.getFormat();
        format.setMinimumFractionDigits(1);

        addControlListeners();
    }


    private void addControlListeners() {
        jSpinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                auxiliaryMasksData.setWaterMaskTransparency((Double) jSpinner.getValue());
            }
        });
    }


    public JLabel getjLabel() {
        return jLabel;
    }

    public JSpinner getjSpinner() {
        return jSpinner;
    }
}
