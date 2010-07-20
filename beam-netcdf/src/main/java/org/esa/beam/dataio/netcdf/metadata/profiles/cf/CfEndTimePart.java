/*
 * $Id$
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
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
package org.esa.beam.dataio.netcdf.metadata.profiles.cf;

import org.esa.beam.dataio.netcdf.metadata.ProfilePart;
import org.esa.beam.dataio.netcdf.metadata.ProfileReadContext;
import org.esa.beam.dataio.netcdf.metadata.ProfileWriteContext;
import org.esa.beam.dataio.netcdf.util.Constants;
import org.esa.beam.dataio.netcdf.util.ReaderUtils;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import ucar.nc2.Attribute;

import java.io.IOException;

public class CfEndTimePart extends ProfilePart {

    @Override
    public void read(ProfileReadContext ctx, Product p) throws IOException {
        p.setEndTime(ReaderUtils.getSceneRasterTime(ctx.getNetcdfFile(),
                                                    Constants.STOP_DATE_ATT_NAME,
                                                    Constants.STOP_TIME_ATT_NAME));
    }

    @Override
    public void define(ProfileWriteContext ctx, Product p) throws IOException {
        final ProductData.UTC utc = p.getEndTime();
        if (utc != null) {
            ctx.getNetcdfFileWriteable().addAttribute(null, new Attribute(Constants.STOP_DATE_ATT_NAME, utc.format()));
        }
    }

}