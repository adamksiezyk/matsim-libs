package playground.wrashid.PSF.energy.charging;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;

import org.matsim.api.basic.v01.Id;

import playground.wrashid.PSF.ParametersPSF;
import playground.wrashid.PSF.energy.consumption.EnergyConsumption;
import playground.wrashid.PSF.energy.consumption.LinkEnergyConsumptionLog;
import playground.wrashid.PSF.parking.ParkLog;

public class ChargingTimes {

	private PriorityQueue<ChargeLog> chargingTimes = new PriorityQueue<ChargeLog>();

	public void addChargeLog(ChargeLog chargeLog) {
		chargingTimes.add(chargeLog);
	}

	/**
	 * This method does not give back the original list, but a copy.
	 * 
	 * @return
	 */
	public LinkedList<ChargeLog> getChargingTimes() {
		LinkedList<ChargeLog> list = new LinkedList<ChargeLog>();
		Iterator<ChargeLog> iterator = chargingTimes.iterator();

		while (iterator.hasNext()) {
			ChargeLog curItem = iterator.next();
			list.add(curItem);
		}

		return list;
	}

	public void print() {
		Iterator<ChargeLog> iterator = chargingTimes.iterator();

		while (iterator.hasNext()) {
			ChargeLog curItem = iterator.next();
			curItem.print();
		}
	}

	/**
	 * TODO: replace default max battery capacity with a class, which maps max
	 * battery of vehicles on an individual vehicle basis. TODO - refactor: this
	 * is not neat design, because inconsitencies are possible, if this method
	 * is not invoked
	 * 
	 * Appraoche: think through, if this could works????
	 * 
	 * - Attention, this cannot work, because the ChargeLogs are not ordered! -
	 * The energy consumption needs to be taken into account => this should be
	 * directly done via the constructed instead!!!!
	 * @param energyConsumption 
	 * 
	 */

	public void updateSOCs(EnergyConsumption energyConsumption) {
		double startSOC = ParametersPSF.getDefaultMaxBatteryCapacity();
		
		LinkedList<LinkEnergyConsumptionLog> consumptionLog= (LinkedList<LinkEnergyConsumptionLog>) energyConsumption.getLinkEnergyConsumption().clone();
		LinkEnergyConsumptionLog curConsumptionLog= consumptionLog.poll();
		
		// take both energy consumption and charging into consideration for calculating the SOC
		
		Iterator<ChargeLog> iterator = chargingTimes.iterator();

		while (iterator.hasNext()) {
			ChargeLog curChargeLog = iterator.next();
			
			// we must check if curConsumptionLog is not null, because if the agent only charges at home
			// in the evening, then all energy consumption happens before that...
			while (curConsumptionLog !=null && curConsumptionLog.getEnterTime()<curChargeLog.getEndChargingTime()){
				startSOC-=curConsumptionLog.getEnergyConsumption();
				curConsumptionLog= consumptionLog.poll();
			}
			
			curChargeLog.updateSOC(startSOC);
			startSOC = curChargeLog.getEndSOC();
		}
		
		
	}

	/**
	 * This writes out charging events. TODO: add columns at the end for
	 * SOC_start and SOC_end Dependencies: Of course the link ids etc. must be
	 * available for using this...
	 * 
	 * @param chargingTimes
	 * @param outputFilePath
	 */
	public static void writeChargingTimes(HashMap<Id, ChargingTimes> chargingTimes, String outputFilePath) {
		System.out.println("linkId\tagentId\tstartChargingTime\tendChargingTime");

		try {
			FileOutputStream fos = new FileOutputStream(outputFilePath);
			OutputStreamWriter chargingOutput = new OutputStreamWriter(fos, "UTF8");
			chargingOutput.write("linkId\tagentId\tstartChargingTime\tendChargingTime\n");

			for (Id personId : chargingTimes.keySet()) {
				ChargingTimes curChargingTime = chargingTimes.get(personId);

				for (ChargeLog chargeLog : curChargingTime.getChargingTimes()) {
					chargingOutput.write(chargeLog.getLinkId().toString() + "\t");
					chargingOutput.write(personId.toString() + "\t");
					chargingOutput.write(chargeLog.getStartChargingTime() + "\t");
					chargingOutput.write(chargeLog.getEndChargingTime() + "\t");
					chargingOutput.write("\n");
				}
			}

			chargingOutput.flush();
			chargingOutput.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
