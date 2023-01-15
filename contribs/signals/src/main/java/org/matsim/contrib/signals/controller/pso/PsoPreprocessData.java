/* *********************************************************************** *
 * project: org.matsim.*
 * DgGenerateBasePlans
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.signals.data.signalcontrol.v20.*;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;


/**
 * @author dgrether, tthunig
 */
public final class PsoPreprocessData {

	public static final String FIXED_TIME_PREFIX = "fixed_time_plan_";
	public static final String PSO_PREFIX = "pso_plan_";
	private static final Logger log = LogManager.getLogger(PsoPreprocessData.class);
	private static final int MIN_GREEN_SECONDS = 5; // RILSA pp. 28 says 5s


	private static void convertFixedTimePlansToPsoBasePlans(String signalControlInputFile, String signalControlOutputFile)
			throws JAXBException, SAXException, ParserConfigurationException, IOException {
		SignalControlData signalControl = new SignalControlDataImpl();
		SignalControlReader20 reader = new SignalControlReader20(signalControl);
		reader.readFile(signalControlInputFile);

		SignalControlDataImpl psoSignalControl = new SignalControlDataImpl();
		convertSignalControlData(signalControl, psoSignalControl);

		SignalControlWriter20 writer = new SignalControlWriter20(psoSignalControl);
		writer.write(signalControlOutputFile);
	}

	/**
	 * Convert old, fixed time signal control data into new PSO signal control data.
	 * The PSO signal control data will then contain the old fixed time signal control plan and the new shortened PSO signal control plan.
	 *
	 * @param controlData             old, fixed-time signal control
	 * @param psoSignalControlData signal control object, where the PSO signal control should be stored in
	 */
	public static void convertSignalControlData(final SignalControlData controlData, SignalControlData psoSignalControlData) {
		for (SignalSystemControllerData controllerData : controlData.getSignalSystemControllerDataBySystemId().values()) {
			SignalSystemControllerData newControllerData = psoSignalControlData.getFactory().createSignalSystemControllerData(controllerData.getSignalSystemId());
			psoSignalControlData.addSignalSystemControllerData(newControllerData);
			newControllerData.setControllerIdentifier(PsoSignalController.IDENTIFIER);
			for (SignalPlanData signalPlan : controllerData.getSignalPlanData().values()) {
				// add a copy of the old plan
				newControllerData.addSignalPlanData(SignalUtils.copySignalPlanData(signalPlan,
						Id.create(FIXED_TIME_PREFIX + signalPlan.getId().toString(), SignalPlan.class)));
				newControllerData.addSignalPlanData(convertSignalPlanData(signalPlan));
			}
		}
	}

	private static SignalPlanData convertSignalPlanData(final SignalPlanData fixedTimePlan) {
		SignalPlanData newPlan = SignalUtils.copySignalPlanData(fixedTimePlan, Id.create(PSO_PREFIX + fixedTimePlan.getId().toString(), SignalPlan.class));
		// shift plan such that longest green time starts at second zero
		shiftPlan(newPlan);
		/* create sorted list by onset and dropping (decreasing) */
		List<SignalGroupSettingsData> settingsSortedByDropping = new ArrayList<>();
		settingsSortedByDropping.addAll(newPlan.getSignalGroupSettingsDataByGroupId().values());
		Collections.sort(settingsSortedByDropping, new Comparator<SignalGroupSettingsData>() {

			@Override
			public int compare(SignalGroupSettingsData setting1, SignalGroupSettingsData setting2) {
				// shift 1 and 2 to get reverse order
				return Integer.compare(setting2.getDropping(), setting1.getDropping());
			}
		});
		List<SignalGroupSettingsData> settingsSortedByOnset = new ArrayList<>();
		settingsSortedByOnset.addAll(newPlan.getSignalGroupSettingsDataByGroupId().values());
		Collections.sort(settingsSortedByOnset, new Comparator<SignalGroupSettingsData>() {

			@Override
			public int compare(SignalGroupSettingsData setting1, SignalGroupSettingsData setting2) {
				// shift 1 and 2 to get reverse order
				return Integer.compare(setting2.getOnset(), setting1.getOnset());
			}
		});
		/* scan cycle time for time periods before droppings, where no signal setting is changed and shrink them to MIN_GREEN_SECONDS seconds */
		Iterator<SignalGroupSettingsData> droppingIterator = settingsSortedByDropping.iterator();
		Iterator<SignalGroupSettingsData> onsetIterator = settingsSortedByOnset.iterator();
		SignalGroupSettingsData thisDropping = droppingIterator.next();
		SignalGroupSettingsData nextDropping = droppingIterator.hasNext() ? droppingIterator.next() : null;
		while (nextDropping != null) {
			// look for the next dropping pair with a minimum of MIN_GREEN_SECONDS seconds inbetween
			while (nextDropping != null && thisDropping.getDropping() - nextDropping.getDropping() <= MIN_GREEN_SECONDS) {
				// between these two droppings we can not shrink the signal plan. check the next pair
				thisDropping = nextDropping;
				nextDropping = droppingIterator.hasNext() ? droppingIterator.next() : null;
			}
			// look for the next onset that is before the current dropping (note: there always is one, because no dropping is scheduled at second 0 but at least one offset is scheduled for second 0)
			SignalGroupSettingsData nextOnset = onsetIterator.next();
			while (nextOnset.getOnset() >= thisDropping.getDropping()) {
				nextOnset = onsetIterator.next();
			}
			if (thisDropping.getDropping() - nextOnset.getOnset() <= MIN_GREEN_SECONDS) {
				// we can not shrink the signal plan before the current dropping
				thisDropping = nextDropping;
				continue;
			}
			// we can shrink the signal plan
			int shrinkStart = Math.max(nextOnset.getOnset(), nextDropping == null ? 0 : nextDropping.getDropping()) + MIN_GREEN_SECONDS;
			int shrinkEnd = thisDropping.getDropping();
			// shift all settings behind shrinkStart by shrinkEnd - shrinkStart seconds to
			// the left
			log.info("Shrink plan " + newPlan.getId() + " by " + (shrinkEnd - shrinkStart)
					+ " seconds at dropping of signal group " + thisDropping.getSignalGroupId()
					+ ". Shift all settings behind " + shrinkStart + " accordingly. The next dropping is signal group "
					+ (nextDropping == null ? "null" : nextDropping.getSignalGroupId())
					+ ". The next onset is signal group " + nextOnset.getSignalGroupId());
			shrinkSignalPlan(newPlan, shrinkStart, shrinkEnd - shrinkStart);
			thisDropping = nextDropping;
		}
		return newPlan;
	}

	/**
	 * shift plan such that longest green time starts at second zero. adapt offset accordingly. check that no dropping happens at second 0 but cycleTime
	 */
	private static SignalPlanData shiftPlan(SignalPlanData signalPlan) {
		// identify longest green time
		int longestGreenTime = 0;
		Id<SignalGroup> longestGreenGroup = null;
		for (SignalGroupSettingsData setting : signalPlan.getSignalGroupSettingsDataByGroupId().values()) {
			int greenTime = setting.getDropping() - setting.getOnset();
			if (greenTime <= 0) greenTime += signalPlan.getCycleTime();

			if (greenTime > longestGreenTime) {
				longestGreenTime = greenTime;
				longestGreenGroup = setting.getSignalGroupId();
			}
		}
		// shift all settings and offset accordingly
		int shiftBy = signalPlan.getSignalGroupSettingsDataByGroupId().get(longestGreenGroup).getOnset();
		log.info("The setting with the longest green time is signal group " + longestGreenGroup + " with a green time of " + longestGreenTime + " seconds. Shift start to second " + shiftBy);
		if (shiftBy == 0) {
			return signalPlan;
		}
		signalPlan.setOffset((signalPlan.getOffset() - shiftBy + signalPlan.getCycleTime()) % signalPlan.getCycleTime());
		for (SignalGroupSettingsData setting : signalPlan.getSignalGroupSettingsDataByGroupId().values()) {
			// set new onset in interval [0,cycleTime-1]
			int shiftedOnset = setting.getOnset() - shiftBy;
			if (shiftedOnset < 0) shiftedOnset += signalPlan.getCycleTime();
			setting.setOnset(shiftedOnset);
			// set new dropping in interval [1,cycleTime]
			int shiftedDropping = setting.getDropping() - shiftBy;
			if (shiftedDropping <= 0) shiftedDropping += signalPlan.getCycleTime();
			setting.setDropping(shiftedDropping);
		}
		return signalPlan;
	}

	/**
	 * move all droppings and onsets behind shrinkStart by secondsToBeRemoved
	 * seconds to the left, i.e. shrink the whole signal plan by secondsToBeRemoved
	 * seconds beginning at second shrinkStart. Move the offset accordingly.
	 */
	private static void shrinkSignalPlan(SignalPlanData signalPlan, int shrinkStart, int secondsToBeRemoved) {
		signalPlan.setCycleTime(signalPlan.getCycleTime() - secondsToBeRemoved);
		for (SignalGroupSettingsData setting : signalPlan.getSignalGroupSettingsDataByGroupId().values()) {
			if (setting.getOnset() > shrinkStart) {
				setting.setOnset(setting.getOnset() - secondsToBeRemoved);
			}
			if (setting.getDropping() > shrinkStart) {
				setting.setDropping(setting.getDropping() - secondsToBeRemoved);
			}
		}
		// move offset accordingly
		if (signalPlan.getOffset() > shrinkStart + secondsToBeRemoved) {
			signalPlan.setOffset(signalPlan.getOffset() - secondsToBeRemoved);
		} else if (signalPlan.getOffset() > shrinkStart) {
			signalPlan.setOffset(shrinkStart);
		}
	}


	public static void main(String[] args) throws JAXBException, SAXException, ParserConfigurationException, IOException {
		String signalControlFile = args[0];
		String signalControlOutFile = args[1];
		PsoPreprocessData.convertFixedTimePlansToPsoBasePlans(signalControlFile, signalControlOutFile);
	}
}
