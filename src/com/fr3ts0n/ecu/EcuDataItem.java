/*
 * (C) Copyright 2015 by fr3ts0n <erwin.scheuch-heilig@gmx.at>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

package com.fr3ts0n.ecu;

import com.fr3ts0n.prot.ProtUtils;
import com.fr3ts0n.prot.ProtoHeader;

import org.apache.log4j.Logger;

/**
 * Definition of a single ECU Data item (EcuDataItem)
 *
 * @author erwin
 */
public class EcuDataItem
	implements Cloneable
{
	/** conversion systems METRIC and IMPERIAL */
	public static final int SYSTEM_METRIC = 0;
	public static final int SYSTEM_IMPERIAL = 1;
	public static final int SYSTEM_TYPES = 2;
	/** names of conversion system types */
	public static final String[] cnvSystems =
	{
		"METRIC",
		"IMPERIAL",
	};
	// current conversion system
	public static int cnvSystem = SYSTEM_METRIC;
	public int pid;        // pid
	public int bytes;        // number of data bytes expected from vehicle
	public int ofs;        // Offset within message
	public Conversion[] cnv;        // type of conversion
	public String fmt;    // Format for text output
	public String label;        // text label
	public EcuDataPv pv;        // the process variable for displaying
	public boolean enabled = true; // if not enabled, do not process

	// Logger object
	public static final Logger log = Logger.getLogger("data.ecu");

	public static int[] byteValues =
	{
		    0xFFFF, // fake default max value for length 0
				  0xFF,
		    0xFFFF,
		  0xFFFFFF,
		0xFFFFFFFF
	};

	/**
	 * Creates a new instance of EcuDataItem
	 */
	public EcuDataItem()
	{
	}

	/**
	 * Creates a new instance of EcuDataItem
	 *
	 * @param newPid PID of data item
	 * @param offset offset within PID data (in bytes)
	 * @param numBytes length of parameter in bytes
	 * @param conversions data conversion to be used with this item
	 * @param format formatting string for text representation
	 * @param minValue minimum physical value to display/scale
	 * @param maxValue maximum physical value to display/scale
	 * @param labelText descriptive text label
	 */
	public EcuDataItem( int newPid,
	                    int offset,
	                    int numBytes,
	                    Conversion[] conversions,
	                    String format,
	                    Number minValue,
	                    Number maxValue,
	                    String labelText)
	{
		pid = newPid;
		ofs = offset;
		bytes = numBytes;
		cnv = conversions;
		fmt = format;
		label = labelText;
		pv = new EcuDataPv();
		Number minVal = minValue;
		Number maxVal = maxValue;

		// initialize new PID with current data
		pv.put(EcuDataPv.FID_PID, Integer.valueOf(pid));
		pv.put(EcuDataPv.FID_OFS, Integer.valueOf(ofs));
		pv.put(EcuDataPv.FID_DESCRIPT, label);
		pv.put(EcuDataPv.FID_UNITS,
		       (cnv != null && cnv[cnvSystem] != null)
		       ? cnv[cnvSystem].getUnits()
		       : "");
		pv.put(EcuDataPv.FID_VALUE, Float.valueOf(0));
		pv.put(EcuDataPv.FID_FORMAT, fmt);
		pv.put(EcuDataPv.FID_CNVID, cnv);
		if(cnv != null && cnv[cnvSystem] != null)
		{
			if(minVal == null) minVal = cnv[cnvSystem].memToPhys(0);
			if(maxVal == null) maxVal = cnv[cnvSystem].memToPhys(byteValues[bytes]);
		}
		pv.put(EcuDataPv.FID_MIN, minVal);
		pv.put(EcuDataPv.FID_MAX, maxVal);
	}

	@Override
	public String toString()
	{
		return (String.format("%02X.%d", pid, ofs));
	}

	/**
	 * get physical value from buffer
	 *
	 * @param buffer communication buffer content
	 * @return physical value
	 */
	Object physFromBuffer(char[] buffer)
	{
		Object result;
		try
		{
			if (cnv != null && cnv[cnvSystem] != null)
			{
				result = cnv[cnvSystem].memToPhys(ProtoHeader.getParamInt(ofs, bytes, buffer).longValue());
			}
			else
			{
				result = String.copyValueOf(buffer, ofs, bytes);
			}
		} catch(Exception ex)
		{
			result = String.format("%s:%s", ProtUtils.hexDumpBuffer(buffer), ex.getMessage());
			log.warn(String.format("%s: %s", toString(), result));
			enabled = false;
		}
		return (result);
	}

	/**
	 * Update process var from Buffer value
	 *
	 * @param buffer communication buffer content
	 */
	public void updatePvFomBuffer(char[] buffer)
	{
		if(enabled)
		{
			try
			{
				// get physical value
				Object result = physFromBuffer(buffer);
				pv.put(EcuDataPv.FID_VALUE, result);
				log.debug(String.format("%02X %-30s %16s %s",
				                        pid,
				                        label,
				                        pv.get(EcuDataPv.FID_VALUE),
				                        pv.get(EcuDataPv.FID_UNITS)));
			}
			catch(Exception ex)
			{
				log.error(ex);
			}
		}
	}

	@Override
	public Object clone()
	{
		EcuDataItem result = null;
		try
		{
			result = (EcuDataItem) super.clone();
			result.pv = (EcuDataPv) pv.clone();
		} catch (CloneNotSupportedException ex)
		{
			ex.printStackTrace();
		}

		return (result);
	}

}
