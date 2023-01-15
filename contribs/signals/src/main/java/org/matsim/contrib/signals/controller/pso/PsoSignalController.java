/* *********************************************************************** *
 * project: org.matsim.*
 * DgPsoController
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

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.controller.AbstractSignalController;
import org.matsim.contrib.signals.controller.SignalController;
import org.matsim.contrib.signals.controller.SignalControllerFactory;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.model.*;
import org.matsim.contrib.signals.sensor.DownstreamSensor;
import org.matsim.contrib.signals.sensor.LinkSensorManager;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;

import java.util.*;


/**
 * @author dgrether, tthunig
 */
public final class PsoSignalController extends AbstractSignalController implements SignalController {

	public final static String IDENTIFIER = "PsoSignalControl";
	private static final Logger log = LogManager.getLogger(PsoSignalController.class);
	private static int psoPlanDumpCount = 0;
	private final PsoConfigGroup psoConfig;
	private final LinkSensorManager sensorManager;
	private final DownstreamSensor downstreamSensor;
	private PsoSignalPlan activePsoPlan = null;
	private boolean extensionActive = false;
	private boolean forcedExtensionActive = false;
	private int secondInPsoCycle = -1; //as this is incremented before use
	private Map<Integer, PsoExtensionPoint> extensionPointMap = null;
	private Map<Integer, PsoExtensionPoint> forcedExtensionPointMap = null;
	private Map<Id<SignalGroup>, Double> greenGroupId2OnsetMap = null;
	private int extensionTime = 0;
	private int secondInCycle = -1; //used for debug output
	private PsoExtensionPoint currentExtensionPoint;

	private PsoSignalController(Scenario scenario, LinkSensorManager sensorManager, DownstreamSensor downstreamSensor) {
		this.psoConfig = ConfigUtils.addOrGetModule(scenario.getConfig(), PsoConfigGroup.class);
		this.sensorManager = sensorManager;
		this.downstreamSensor = downstreamSensor;
		this.init();
	}

	private void init() {
		this.currentExtensionPoint = null;
		this.activePsoPlan = null;
		this.extensionActive = false;
		this.forcedExtensionActive = false;
		this.greenGroupId2OnsetMap = new HashMap<>();
		this.extensionPointMap = new HashMap<>();
		this.forcedExtensionPointMap = new HashMap<>();
		this.initCycle();
	}

	private void initCycle() {
		this.secondInPsoCycle = -1; //as this is incremented before use
		this.secondInCycle = -1;
		this.extensionTime = 0;
	}

	/**
	 * Decide how to control the signals in this second. Possibilities:
	 * (1) continue with the PSO base plan.
	 * (2) extend a signal phase when more vehicles are arriving.
	 * (3) extend the last signal phase when fixed time cycle time is not reached yet (forced extension).
	 */
	@Override
	public void updateState(double currentTime) {
		this.secondInCycle++;

		if (this.forcedExtensionActive) {
			// forced means last phase of the plan, i.e. has to be extended until the end of the cycle

			this.extensionTime++;
			// check whether the extension should hold on
			if (this.checkForcedExtensionCondition()) {
				// extension holds on. nothing else has to be done in this time step
				return;
			} else { // stop extension
				this.forcedExtensionActive = false;
				// no return because droppings/onsets of this second has to be processed, see (*) below.
			}
		} else if (this.extensionActive) {
			this.extensionTime++;
			// check whether the extension should hold on
			if (this.checkExtensionCondition(currentTime, this.currentExtensionPoint)) {
				// extension holds on. nothing else has to be done in this time step
				return;
			} else { // stop extension
				this.extensionActive = false;
				this.currentExtensionPoint = null;
				// no return because droppings/onsets of this second has to be processed, see (*) below.
			}
		} else { // no extension is active
			// increment the number of seconds that the basic plan is processed
			this.secondInPsoCycle++;

			List<Id<SignalGroup>> onsets = this.activePsoPlan.getOnsets(this.secondInPsoCycle);
			if (onsets != null) {
				for (Id<SignalGroup> groupId : onsets) {
					this.system.scheduleOnset(currentTime, groupId);
					this.greenGroupId2OnsetMap.put(groupId, currentTime);
				}
			}

			// TODO what happens when first sim sim event is not at second 0? it does not extend the phase then?! theresa apr'17

			//check for forced extension trigger (end of the last phase of the plan)
			if (this.forcedExtensionPointMap.containsKey(this.secondInPsoCycle)) {
				if (this.checkForcedExtensionCondition()) {
					this.forcedExtensionActive = true;
					// extension starts. nothing else has to be done in this time step
					return;
				}
				/* else: no extension starts.
				 * droppings/onsets of this second has to be processed, see (*) below. */
			}
			//check for extension trigger (end of an arbitrary phase of the plan)
			else if (this.extensionPointMap.containsKey(this.secondInPsoCycle)) {
				this.currentExtensionPoint = this.extensionPointMap.get(this.secondInPsoCycle);
				if (this.checkExtensionCondition(currentTime, this.currentExtensionPoint)) {
					this.extensionActive = true;
					// extension starts. nothing else has to be done in this time step
					return;
				} else { // no extension starts.
					this.currentExtensionPoint = null;
					// no return because droppings/onsets of this second has to be processed, see (*) below.
				}
			}
		}

		/* stopped extension or no extension at all.
		 * process droppings and onsets of this second in cycle. (*) */
		List<Id<SignalGroup>> droppings = this.activePsoPlan.getDroppings(this.secondInPsoCycle);
		if (droppings != null) {
			for (Id<SignalGroup> groupId : droppings) {
				this.system.scheduleDropping(currentTime, groupId);
				this.greenGroupId2OnsetMap.remove(groupId);
			}
		}

		// stop criterion, i.e. start with the next cycle:
		if (this.secondInPsoCycle == this.activePsoPlan.getCycleTime() - 1) {
			// the base plan cycle including all extensions is processed. init data structure for the next cycle.
			this.initCycle();
		}
	}

	/**
	 * Checks whether there is time left to extend phases.
	 * If the fixed time cycle time is used as maximal extension time, this method checks whether it is already reached.
	 * If not, extension is always allowed.
	 */
	private boolean isExtensionTimeLeft() {
		if (this.psoConfig.isUseFixedTimeCycleAsMaximalExtension()) {
			return this.extensionTime < this.activePsoPlan.getMaxExtensionTime();
		}
		return true;
	}

	/**
	 * Checks whether the maximal green time of the signal is already reached.
	 *
	 * @return false if maximal green time is reached or if signal has not been switched on (first second of the simulation);
	 * true if signal can be extended based on the number of current green time seconds.
	 */
	private boolean isGreenTimeLeft(double currentTime, Id<SignalGroup> groupId, int maxGreenTime) {
		if (!this.greenGroupId2OnsetMap.containsKey(groupId)) {
			/* signals that have not been switched on should not be extended.
			 * this may happen when the signal has its dropping in the first second of the simulation. */
			return false;
		}
		int greenTime = (int) (currentTime - this.greenGroupId2OnsetMap.get(groupId));
		return greenTime < maxGreenTime;
	}

	/**
	 * Checks whether the phase should be forced to extend.
	 * This is the case if there is cycle time left.
	 *
	 * @return true if it should be forced to extend; false if not
	 */
	private boolean checkForcedExtensionCondition() {
		return isExtensionTimeLeft();
	}

	/**
	 * Checks
	 * 1. whether there is green time left to extend the signal groups green phase and, if yes,
	 * 2. whether there are cars arriving, i.e. there is need to extend the signal groups green time.
	 *
	 * @param currentTime
	 * @param extensionPoint
	 * @return true, if the signal should be extended. false, if not, i.e. if there is no time left to extend the signal or if there is no need to extend the signal.
	 */
	private boolean checkExtensionCondition(double currentTime, PsoExtensionPoint extensionPoint) {
		if (this.isExtensionTimeLeft()) {
			//check if there is some green time left or one of the groups is over its maximal green time
			for (Id<SignalGroup> signalGroupId : extensionPoint.getSignalGroupIds()) {
				if (!this.isGreenTimeLeft(currentTime, signalGroupId, extensionPoint.getMaxGreenTime(signalGroupId))) {
					return false;
				}
			}
			//if return was not reached yet, green time is left. check sensor data:
			return this.checkTrafficConditions(currentTime, extensionPoint);
		}
		return false;
	}

	/**
	 * Checks whether there are cars arriving, i.e. the signal groups green time should be extended.
	 *
	 * @param currentTime
	 * @param extensionPoint
	 * @return true, if there is a car at an incoming lane
	 * (or, if no lanes are used, when there is a car on an incoming link within some distance (specified in psoConfig - default is 10))
	 * and, when downstream check is enabled, if all downstream links are empty
	 */
	private boolean checkTrafficConditions(double currentTime, PsoExtensionPoint extensionPoint) {
		if (psoConfig.isCheckDownstream()) {
			// no extension if downstream links are occupied
			for (Id<SignalGroup> greenGroup : extensionPoint.getSignalGroupIds()) {
				if (!downstreamSensor.allDownstreamLinksEmpty(system.getId(), greenGroup))
					return false;
			}
		}
		for (Signal signal : extensionPoint.getSignals()) {
			if (signal.getLaneIds() == null || signal.getLaneIds().isEmpty()) {
				// link has no lanes
				int noCars = this.sensorManager.getNumberOfCarsInDistance(signal.getLinkId(), this.psoConfig.getSensorDistanceMeter(), currentTime);
				if (noCars > 0)
					return true;
			} else {
				// link has lanes
				for (Id<Lane> laneId : signal.getLaneIds()) {
					int noCars = this.sensorManager.getNumberOfCarsInDistanceOnLane(signal.getLinkId(), laneId, this.psoConfig.getSensorDistanceMeter(), currentTime);
					if (noCars > 0)
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * Is called when mobsim is initialized.
	 * The parameter simStartTimeSeconds is not used.
	 * <p>
	 * This method initializes all elements needed for the PSO controller:
	 * it collects relevant (PSO and fixed time) signal plans,
	 * determines possible extension points (in time)
	 * and prepares sensors (event handlers) to count vehicles in front of signals.
	 */
	@Override
	public void simulationInitialized(double simStartTimeSeconds) {
		this.init();
		Tuple<SignalPlan, PsoSignalPlan> plans = this.searchActivePlans();
		this.activePsoPlan = plans.getSecond();
		this.activePsoPlan.setFixedTimeCycle(plans.getFirst().getCycleTime());
		this.setMaxExtensionTimeInPsoPlan(plans);
		this.calculateExtensionPoints(plans);
		if (psoPlanDumpCount < 1) {
			this.dumpPsoPlan();
			psoPlanDumpCount++;
		}
		this.initializeSensoring();
	}

	private void setMaxExtensionTimeInPsoPlan(Tuple<SignalPlan, PsoSignalPlan> plans) {
		int ext = plans.getFirst().getCycleTime() - plans.getSecond().getCycleTime();
		plans.getSecond().setMaxExtensionTime(ext);
	}

	/**
	 * Collects PSO and fixed time signal plans of this signal system from the signal plan container.
	 *
	 * @return tuple of both (PSO and fixed time) signal plans
	 */
	private Tuple<SignalPlan, PsoSignalPlan> searchActivePlans() {
		PsoSignalPlan psoPlan = null;
		SignalPlan fixedTimePlan = null;
		if (this.signalPlans.size() > 2) {
			throw new IllegalStateException("The current implementation of PSO does not work with multiple signal plans for different times of the day.");
		}
		for (Id<SignalPlan> planId : this.signalPlans.keySet()) {
			if (planId.toString().startsWith(PsoPreprocessData.PSO_PREFIX)) {
				psoPlan = (PsoSignalPlan) this.signalPlans.get(planId);
			}
			if (planId.toString().startsWith(PsoPreprocessData.FIXED_TIME_PREFIX)) {
				fixedTimePlan = this.signalPlans.get(planId);
			}
		}
		if (psoPlan == null && fixedTimePlan == null) {
			throw new IllegalStateException("No suitable plans found for controller of signal system: " + this.system.getId());
		}
		return new Tuple<SignalPlan, PsoSignalPlan>(fixedTimePlan, psoPlan);
	}

	/**
	 * This method determines points in time when extension is possible (extension points).
	 * It calculates maximal green times based on maximal cycle time and maximal green timescale.
	 * The last extension point in a cycle is defined as a forced extension point.
	 *
	 * @param plans
	 */
	private void calculateExtensionPoints(Tuple<SignalPlan, PsoSignalPlan> plans) {
		SignalPlan fixedTime = plans.getFirst();
		PsoSignalPlan psoSignalPlan = plans.getSecond();
		int offset = psoSignalPlan.getOffset();
		int lastExtensionMoment = 0;
		for (SignalGroupSettingsData settings : psoSignalPlan.getPlanData().getSignalGroupSettingsDataByGroupId().values()) {
			// set the extension moment to the second of the dropping (but respecting offset and cycle time)
			int extensionMoment = (settings.getDropping() + offset) % psoSignalPlan.getCycleTime();
			// remember last extension moment
			if (extensionMoment > lastExtensionMoment) {
				lastExtensionMoment = extensionMoment;
			}

			// put all extension points in a map ordered by time
			if (!this.extensionPointMap.containsKey(extensionMoment)) {
				this.extensionPointMap.put(extensionMoment, new PsoExtensionPoint(extensionMoment));
//				// comment this out because it is not needed and not used. tt, oct'16
//				psoSignalPlan.addExtensionPoint(extPoint);
			}
			PsoExtensionPoint extPoint = this.extensionPointMap.get(extensionMoment);
			extPoint.addSignalGroupId(settings.getSignalGroupId());

			//calculate max green time
			SignalGroupSettingsData fixedTimeSettings = ((DatabasedSignalPlan) fixedTime).getPlanData().getSignalGroupSettingsDataByGroupId().get(settings.getSignalGroupId());
			int fixedTimeGreen = SignalUtils.calculateGreenTimeSeconds(fixedTimeSettings, fixedTime.getCycleTime());
			int maxGreen = Integer.MAX_VALUE;
			if (this.psoConfig.getSignalGroupMaxGreenScale() != Double.MAX_VALUE) {
				// signal group max scale is not unbounded
				maxGreen = (int) (fixedTimeGreen * this.psoConfig.getSignalGroupMaxGreenScale());
			}
//			// comment this out because isExtensionTimeLeft() already checks whether cycle time is exceeded. tt, oct'16
//			if (maxGreen >= fixedTime.getCycleTime()){
//				maxGreen = fixedTimeGreen;
//			}
			extPoint.setMaxGreenTime(settings.getSignalGroupId(), maxGreen);
		}

		if (this.psoConfig.isUseFixedTimeCycleAsMaximalExtension()) {
			// convert last extension point in cycle into a forced extension point (has to be extended until fixed time cycle time is reached).
			forcedExtensionPointMap.put(lastExtensionMoment, extensionPointMap.remove(lastExtensionMoment));
		}

	}

	private void dumpPsoPlan() {
		log.debug("Signal System: " + this.system.getId() + " Plan: " + this.activePsoPlan.getPlanData().getId());
		log.debug("  Maximal time for extension: " + this.activePsoPlan.getMaxExtensionTime());
		for (PsoExtensionPoint p : this.extensionPointMap.values()) {
			log.debug("  ExtensionPoint at: " + p.getSecondInPlan() + " groups: ");
			for (Id<SignalGroup> sgId : p.getSignalGroupIds()) {
				log.debug("    SignalGroup: " + sgId + " maxGreen: " + p.getMaxGreenTime(sgId));
			}
		}
	}

	/**
	 * Collect all signals of the signal system and prepare sensors (event handlers) upstream of them
	 * to count vehicles in a specific distance (when no lanes are used) or on the last lane (if lanes are used).
	 * <p>
	 * Prepare also downstream sensors if checkDownstream is enabled in psoConfig
	 */
	private void initializeSensoring() {
		for (PsoExtensionPoint extPoint : this.extensionPointMap.values()) {
			Set<Signal> extPointSignals = new HashSet<>();
			for (Id<SignalGroup> signalGroupId : extPoint.getSignalGroupIds()) {
				extPointSignals.addAll(system.getSignalGroups().get(signalGroupId).getSignals().values());
			}
			extPoint.addSignals(extPointSignals);
			for (Signal signal : extPointSignals) {
				if (signal.getLaneIds() == null || signal.getLaneIds().isEmpty()) {
					this.sensorManager.registerNumberOfCarsInDistanceMonitoring(signal.getLinkId(), this.psoConfig.getSensorDistanceMeter());
				} else {
					for (Id<Lane> laneId : signal.getLaneIds()) {
						this.sensorManager.registerNumberOfCarsOnLaneInDistanceMonitoring(signal.getLinkId(), laneId, this.psoConfig.getSensorDistanceMeter());
					}
				}
			}
		}

		if (psoConfig.isCheckDownstream()) {
			downstreamSensor.registerDownstreamSensors(system);
		}
	}

	public final static class PsoFactory implements SignalControllerFactory {
		@Inject
		private LinkSensorManager sensorManager;
		@Inject
		private DownstreamSensor downstreamSensor;
		@Inject
		private Scenario scenario;

		@Override
		public SignalController createSignalSystemController(SignalSystem signalSystem) {
			SignalController controller = new PsoSignalController(scenario, sensorManager, downstreamSensor);
			controller.setSignalSystem(signalSystem);
			return controller;
		}
	}
}
