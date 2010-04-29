/*
 * $Id$
 *
 * Copyright (C) 2010 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.dataio.netcdf4.convention.cf;

import org.esa.beam.dataio.netcdf4.Nc4AttributeMap;
import org.esa.beam.dataio.netcdf4.Nc4Constants;
import org.esa.beam.dataio.netcdf4.Nc4ReaderParameters;
import org.esa.beam.dataio.netcdf4.convention.HeaderDataWriter;
import org.esa.beam.dataio.netcdf4.convention.ModelPart;
import org.esa.beam.framework.dataio.ProductIOException;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.IndexCoding;
import org.esa.beam.framework.datamodel.Product;
import ucar.ma2.Array;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

import java.io.IOException;

public class CfIndexCodingPart implements ModelPart {

    public static final String FLAG_VALUES = "flag_values";
    public static final String FLAG_MEANINGS = "flag_meanings";


    @Override
    public void read(Product p, Nc4ReaderParameters rp) throws IOException {
        final Band[] bands = p.getBands();
        for (Band band : bands) {
            final IndexCoding indexCoding = readIndexCoding(band, rp);
            if (indexCoding != null) {
                p.getIndexCodingGroup().add(indexCoding);
                band.setSampleCoding(indexCoding);
            }
        }
    }

    @Override
    public void write(Product p, NetcdfFileWriteable ncFile, HeaderDataWriter hdw) throws IOException {
        final Band[] bands = p.getBands();
        for (Band band : bands) {
            writeIndexCoding(ncFile, band);
        }
    }

    public static void writeIndexCoding(NetcdfFileWriteable ncFile, Band band) {
        final IndexCoding indexCoding = band.getIndexCoding();
        if (indexCoding != null) {
            final String[] indexNames = indexCoding.getIndexNames();
            final int[] indexValues = new int[indexNames.length];
            final StringBuffer meanings = new StringBuffer();
            for (int i = 0; i < indexValues.length; i++) {
                String name = indexNames[i];
                meanings.append(name).append(" ");
                indexValues[i] = indexCoding.getIndexValue(name);
            }
            ncFile.addVariableAttribute(band.getName(), new Attribute(FLAG_MEANINGS, meanings.toString().trim()));
            ncFile.addVariableAttribute(band.getName(), new Attribute(FLAG_VALUES, Array.factory(indexValues)));
        }
    }

    public static IndexCoding readIndexCoding(Band band, Nc4ReaderParameters rp) throws ProductIOException {
        final Variable variable = rp.getGlobalVariablesMap().get(band.getName());
        final Nc4AttributeMap attMap = Nc4AttributeMap.create(variable);
        final String codingName = band.getName() + "_index_coding";
        return readIndexCoding(attMap, codingName);
    }

    private static IndexCoding readIndexCoding(final Nc4AttributeMap attMap, final String codingName)
            throws ProductIOException {
        final Attribute flagValuesAtt = attMap.get(FLAG_VALUES);
        final int[] flagValues;
        if (flagValuesAtt != null) {
            flagValues = new int[flagValuesAtt.getLength()];
            for (int i = 0; i < flagValues.length; i++) {
                flagValues[i] = flagValuesAtt.getNumericValue(i).intValue();
            }
        } else {
            flagValues = null;
        }

        final String flagMeanings = attMap.getStringValue(FLAG_MEANINGS);
        final String[] flagNames;
        if (flagMeanings != null) {
            flagNames = flagMeanings.split(" ");
        } else {
            flagNames = null;
        }

        return createIndexCoding(codingName, flagValues, flagNames);
    }

    private static IndexCoding createIndexCoding(String codingName, int[] flagValues, String[] flagNames)
            throws ProductIOException {
        if (flagValues != null && flagNames != null) {
            if (flagValues.length != flagNames.length) {
                throw new ProductIOException(Nc4Constants.EM_INVALID_INDEX_CODING);
            }
            final IndexCoding coding = new IndexCoding(codingName);
            for (int i = 0; i < flagValues.length; i++) {
                final String sampleName = flagNames[i];
                final int sampleValue = flagValues[i];
                coding.addSample(sampleName, sampleValue, "");
            }
            if (coding.getNumAttributes() > 0) {
                return coding;
            }
        }
        return null;
    }
}