/* *********************************************************************** *
 * project: org.matsim.*
 * DgPsoConfig
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.contrib.signals.controller.pso;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

/**
 * @author dgrether, tthunig
 */
public final class PsoConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUP_NAME = "actuatedPsoSignals";
	private static final String USE_FIXED_TIME_CYCLE_CMT = "limits the total amount of available extension time to the cycle time "
			+ "of the corresponding fixed-time signal plan";
	private static final String USE_FIXED_TIME_CYCLE = "useFixedTimeCycleAsMaximalExtension";
	private static final String MAX_GREEN_SCALE_CMT = "The maximal green time for each signal group is determined by multiplying "
			+ "this parameter with the green time of the signal group in the fixed-time signal plan that belongs to the PSO signal plan. "
			+ "The default scale is unbounded.";
	private static final String MAX_GREEN_SCALE = "maxGreenScale";
	private boolean useFixedTimeCycleAsMaximalExtension = true;
	private double signalGroupMaxGreenScale = Double.MAX_VALUE;
	/**
	 * The distance from the downstream node where sensors are located on a link.
	 */
	private double sensorDistanceMeter = 10.0;
	/**
	 * extends the phase only if downstream links are empty.
	 * except forced extension points that are extended anyway. i.e. switch off useFixedTimeCycle... too to get meaningful results
	 */
	private boolean checkDownstream = false;

	public PsoConfigGroup() {
		super(GROUP_NAME);
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> map = super.getComments();

		map.put(USE_FIXED_TIME_CYCLE, USE_FIXED_TIME_CYCLE_CMT);
		map.put(MAX_GREEN_SCALE, MAX_GREEN_SCALE_CMT);

		return map;
	}

	@StringGetter(USE_FIXED_TIME_CYCLE)
	public boolean isUseFixedTimeCycleAsMaximalExtension() {
		return useFixedTimeCycleAsMaximalExtension;
	}

	/**
	 * @param useMaximalExtension -- {@value #USE_FIXED_TIME_CYCLE_CMT}
	 */
	@StringSetter(USE_FIXED_TIME_CYCLE)
	public void setUseFixedTimeCycleAsMaximalExtension(boolean useMaximalExtension) {
		this.useFixedTimeCycleAsMaximalExtension = useMaximalExtension;
	}

	@StringGetter(MAX_GREEN_SCALE)
	public double getSignalGroupMaxGreenScale() {
		return signalGroupMaxGreenScale;
	}

	/**
	 * @param maxGreenScale -- {@value #MAX_GREEN_SCALE_CMT}
	 */
	@StringSetter(MAX_GREEN_SCALE)
	public void setSignalGroupMaxGreenScale(double maxGreenScale) {
		this.signalGroupMaxGreenScale = maxGreenScale;
	}

	public double getSensorDistanceMeter() {
		return sensorDistanceMeter;
	}

	public void setSensorDistanceMeter(double sensorDistanceMeter) {
		this.sensorDistanceMeter = sensorDistanceMeter;
	}

	public boolean isCheckDownstream() {
		return checkDownstream;
	}

	public void setCheckDownstream(boolean checkDownstream) {
		this.checkDownstream = checkDownstream;
	}

}
