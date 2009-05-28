/* *********************************************************************** *
 * project: org.matsim.*
 * Person.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2008 by the members listed in the COPYING,  *
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

package org.matsim.core.population;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.basic.v01.BasicPersonImpl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.households.Household;
import org.matsim.population.Desires;
import org.matsim.population.Knowledge;
import org.matsim.utils.customize.Customizable;
import org.matsim.utils.customize.CustomizableImpl;
/**
 * Default implementation of {@link Person} interface.
 * 
 * @see org.matsim.core.api.population.Person
 */
public class PersonImpl implements Person {

	private final static Logger log = Logger.getLogger(Person.class);

	private final BasicPersonImpl<Plan> delegate;

	private Customizable customizableDelegate;

	private Household household;

	protected Plan selectedPlan = null;

	public PersonImpl(final Id id) {
		this.delegate = new BasicPersonImpl<Plan>(id);
	}

	public void addPlan(final Plan plan) {
		this.delegate.addPlan(plan);
		// Make sure there is a selected plan if there is at least one plan
		if (this.selectedPlan == null) this.selectedPlan = plan;
	}

	public Plan getSelectedPlan() {
		return this.selectedPlan;
	}

	public void setSelectedPlan(final Plan selectedPlan) {
		if (this.delegate.getPlans().contains(selectedPlan)) {
			this.selectedPlan = selectedPlan;
		} else if (selectedPlan != null) {
			throw new IllegalStateException("The plan to be set as selected is not stored in the person's plans");
		}
	}

	public Plan createPlan(final boolean selected) {
		Plan p = new PlanImpl(this);
		this.delegate.getPlans().add(p);
		if (selected) {
			setSelectedPlan(p);
		}
		// Make sure there is a selected plan if there is at least one plan
		if (this.getSelectedPlan() == null)
			this.setSelectedPlan(p);
		return p;
	}

	public void removeUnselectedPlans() {
		for (Iterator<Plan> iter = this.delegate.getPlans().iterator(); iter.hasNext(); ) {
			Plan plan = iter.next();
			if (!plan.isSelected()) {
				iter.remove();
			}
		}
	}

	public Plan getRandomPlan() {
		if (this.delegate.getPlans().size() == 0) {
			return null;
		}
		int index = (int)(MatsimRandom.getRandom().nextDouble()*this.delegate.getPlans().size());
		return this.getPlans().get(index);
	}

	public Plan getRandomUnscoredPlan() {
		int cntUnscored = 0;
		for (Plan plan : this.getPlans()) {
			if (plan.getScore() == null) {
				cntUnscored++;
			}
		}
		if (cntUnscored > 0) {
			// select one of the unscored plans
			int idxUnscored = MatsimRandom.getRandom().nextInt(cntUnscored);
			cntUnscored = 0;
			for (Plan plan : this.getPlans()) {
				if (plan.getScore() == null) {
					if (cntUnscored == idxUnscored) {
						return plan;
					}
					cntUnscored++;
				}
			}
		}
		return null;
	}

	public void exchangeSelectedPlan(final Plan newPlan, final boolean appendPlan) {
		newPlan.setPerson(this);
		Plan oldSelectedPlan = getSelectedPlan();
		if (appendPlan || (oldSelectedPlan == null)) {
			this.delegate.getPlans().add(newPlan);
		} else {
			int i = this.delegate.getPlans().indexOf(oldSelectedPlan);
			this.delegate.getPlans().set(i, newPlan);
		}
		setSelectedPlan(newPlan);
	}

	public Plan copySelectedPlan() {
		int i=0;
		Plan oldPlan = this.getSelectedPlan();
		if (oldPlan == null) {
			return null;
		}
		Plan newPlan = new PlanImpl(oldPlan.getPerson());
		try {
			newPlan.copyPlan(oldPlan);
			this.delegate.getPlans().add(newPlan);
			this.setSelectedPlan(newPlan);
		} catch (Exception e) {
			log.warn("plan# " + i +" went wrong:", e);
			newPlan = oldPlan; // give old plan back??
		}
		return newPlan;
	}


	@Override
	public final String toString() {
		StringBuilder b = new StringBuilder();
		b.append("[id=").append(this.getId()).append("]");
		b.append("[sex=").append(this.getSex()).append("]");
		b.append("[age=").append(this.getAge()).append("]");
		b.append("[license=").append(this.getLicense()).append("]");
		b.append("[car_avail=").append(this.getCarAvail()).append("]");
		b.append("[employed=").append(this.getEmployed()).append("]");
		b.append("[travelcards=").append(this.getTravelcards() == null ? "null" : this.getTravelcards().size()).append("]");
		b.append("[knowledge=").append(this.getKnowledge() == null ? "null" : this.getKnowledge()).append("]");
		b.append("[nof_plans=").append(this.getPlans() == null ? "null" : this.getPlans().size()).append("]");
	  return b.toString();
	}

	public boolean removePlan(final Plan plan) {
		boolean result = this.delegate.getPlans().remove(plan);
		if ((this.getSelectedPlan() == plan) && result) {
			this.setSelectedPlan(this.getRandomPlan());
		}
		return result;
	}

	/** Removes the plans with the worst score until only <code>maxSize</code> plans are left.
	 * Plans with undefined scores are treated as plans with very bad scores and thus removed
	 * first. If there are several plans with the same bad score, it can not be predicted which
	 * one of them will be removed.<br>
	 * This method insures that if there are different types of plans (see
	 * {@link org.matsim.core.api.population.Plan#getType()}),
	 * at least one plan of each type remains. This could lead to the worst plan being kept if
	 * it is the only one of it's type. Plans with type <code>null</code> are handled like any
	 * other type, and are differentiated from plans with the type set to an empty String.<br>
	 *
	 * If there are more plan-types than <code>maxSize</code>, it is not possible to reduce the
	 * number of plans to the requested size.<br>
	 *
	 * If the selected plan is on of the deleted ones, a randomly chosen plan will be selected.
	 *
	 * @param maxSize The number of plans that should be left.
	 * 
	 * @deprecated
	 **/
	public void removeWorstPlans(final int maxSize) {
		if (this.delegate.getPlans().size() <= maxSize) {
			return;
		}
		HashMap<Plan.Type, Integer> typeCounts = new HashMap<Plan.Type, Integer>();
		// initialize list of types
		for (Plan plan : this.getPlans()) {
			Integer cnt = typeCounts.get(plan.getType());
			if (cnt == null) {
				typeCounts.put(plan.getType(), Integer.valueOf(1));
			} else {
				typeCounts.put(plan.getType(), Integer.valueOf(cnt.intValue() + 1));
			}
		}
		while (this.delegate.getPlans().size() > maxSize) {
			Plan worst = null;
			double worstScore = Double.POSITIVE_INFINITY;
			for (Plan plan : this.getPlans()) {
				if (typeCounts.get(plan.getType()).intValue() > 1) {
					if (plan.getScore() == null) {
						worst = plan;
						// make sure no other score could be less than this
						worstScore = Double.NEGATIVE_INFINITY;
					} else if (plan.getScore().doubleValue() < worstScore) {
						worst = plan;
						worstScore = plan.getScore().doubleValue();
					}
				}
			}
			if (worst != null) {
				this.delegate.getPlans().remove(worst);
				if (worst.isSelected()) {
					this.setSelectedPlan(this.getRandomPlan());
				}
				// reduce the number of plans of this type
				Integer cnt = typeCounts.get(worst.getType());
				typeCounts.put(worst.getType(), Integer.valueOf(cnt.intValue() - 1));
			} else {
				return; // should only happen if we have more different plan-types than maxSize
			}
		}
	}

	public Id getHouseholdId() {
		if (this.household != null) {
			return this.household.getId();
		}
		return null;
	}

	public Household getHousehold() {
		return this.household;
	}

	public void setHousehold(final Household hh) {
		if (!hh.getMembers().containsKey(this.getId())) {
			hh.getMembers().put(this.getId(), this);
			this.household = hh;
		}
		else if (this.household == null) {
			this.household = hh;
		}
		else if (!this.equals(hh.getMembers().get(this.getId()))) {
			throw new IllegalStateException("The household with id: " + hh.getId() + " already has a member"
					+ " with id: " + this.getId() + " the referenced objects however are not equal!");
		}
	}

	public Knowledge createKnowledge(final String desc) {
		if (this.delegate.getKnowledge() == null) {
			Knowledge k = new Knowledge();
			k.setDescription(desc);
			this.delegate.setKnowledge(k);
		}
		return (Knowledge) this.delegate.getKnowledge();
	}

	public void addTravelcard(final String type) {
		this.delegate.addTravelcard(type);
	}

	public Desires createDesires(final String desc) {
		return this.delegate.createDesires(desc);
	}

	public int getAge() {
		return this.delegate.getAge();
	}

	public String getCarAvail() {
		return this.delegate.getCarAvail();
	}

	public Desires getDesires() {
		return this.delegate.getDesires();
	}

	/**
	 * @return "yes" if the person has a job
	 * @deprecated use {@link #isEmployed()}
	 */
	@Deprecated
	public String getEmployed() {
		if (isEmployed() == null) {
			return null;
		}
		return (isEmployed() ? "yes" : "no");
	}

	public Id getId() {
		return this.delegate.getId();
	}

	public Knowledge getKnowledge() {
		return (Knowledge) this.delegate.getKnowledge();
	}

	public String getLicense() {
		return this.delegate.getLicense();
	}

	public List<Plan> getPlans() {
		return this.delegate.getPlans();
	}

	public String getSex() {
		return this.delegate.getSex();
	}

	public TreeSet<String> getTravelcards() {
		return this.delegate.getTravelcards();
	}

	public boolean hasLicense() {
		return this.delegate.hasLicense();
	}

	public Boolean isEmployed() {
		return this.delegate.isEmployed();
	}

	public void setAge(final int age) {
		this.delegate.setAge(age);
	}

	public void setCarAvail(final String carAvail) {
		this.delegate.setCarAvail(carAvail);
	}

	public void setEmployed(final String employed) {
		this.delegate.setEmployed(employed);
	}

	public void setId(final Id id) {
		this.delegate.setId(id);
	}

	public void setLicence(final String licence) {
		this.delegate.setLicence(licence);
	}

	public void setSex(final String sex) {
		this.delegate.setSex(sex);
	}

	public Map<String, Object> getCustomAttributes() {
		if (this.customizableDelegate == null) {
			this.customizableDelegate = new CustomizableImpl();
		}
		return this.customizableDelegate.getCustomAttributes();
	}

	public void setKnowledge(final Knowledge knowledge) {
		this.delegate.setKnowledge(knowledge);
	}

}
