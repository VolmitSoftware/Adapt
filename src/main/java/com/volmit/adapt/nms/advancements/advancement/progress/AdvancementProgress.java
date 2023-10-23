package com.volmit.adapt.nms.advancements.advancement.progress;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.google.common.collect.Iterables;

import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;

/**
 * Represents the progress a Player has made for a specific Advancement
 * 
 * @author Axel
 *
 */
public class AdvancementProgress {
	
	private net.minecraft.advancements.AdvancementProgress nmsProgress = new net.minecraft.advancements.AdvancementProgress();
	private long lastUpdate = -1;
	
	/**
	 * Constructor for Creating a Progress Instance
	 * 
	 * @param criteria The Criteria
	 * @param requirements The Requirements
	 * @deprecated Use AdvancementProgress(String[][] requirements) instead
	 */
	@Deprecated(forRemoval = true, since = "2.1.15")
	public AdvancementProgress(Map<String, Criterion<?>> criteria, String[][] requirements) {
		nmsProgress.a(new AdvancementRequirements(requirements));
	}
	
	/**
	 * Constructor for Creating a Progress Instance
	 * 
	 * @param criteria The Criteria
	 * @param requirements The Requirements
	 */
	public AdvancementProgress(String[][] requirements) {
		nmsProgress.a(new AdvancementRequirements(requirements));
	}
	
	/**
	 * Grants the Advancement, does not update for the player
	 * 
	 * @return The result of this oepration
	 */
	public GenericResult grant() {
		GenericResult result = GenericResult.UNCHANGED;
		
		Iterable<String> missing = getRemainingCriteria();
		Iterator<String> missingIterator = missing.iterator();
		
		while(missingIterator.hasNext()) {
			String next = missingIterator.next();
			CriterionProgress criterionProgress = getCriterionProgress(next);
			setGranted(criterionProgress);
			result = GenericResult.CHANGED;
			setLastUpdate();
		}
		
		return result;
	}
	
	/**
	 * Revokes the Advancemnt, does not update for the player
	 * 
	 * @return The result of this operation
	 */
	public GenericResult revoke() {
		GenericResult result = GenericResult.UNCHANGED;
		
		Iterable<String> awarded = getAwardedCriteria();
		Iterator<String> awardedIterator = awarded.iterator();
		long current = StreamSupport.stream(awarded.spliterator(), false).count();
		
		while(current > 0 && awardedIterator.hasNext()) {
			String next = awardedIterator.next();
			CriterionProgress criterionProgress = getCriterionProgress(next);
			setUngranted(criterionProgress);
			current--;
			result = GenericResult.CHANGED;
			setLastUpdate();
		}
		
		return result;
	}
	
	/**
	 * Grants Criteria, does not update for the player
	 * 
	 * @param criteria The Criteria to grant
	 * @return The result of this operation
	 */
	public GrantCriteriaResult grantCriteria(String... criteria) {
		GrantCriteriaResult result = GrantCriteriaResult.UNCHANGED;
		boolean doneBefore = isDone();
		
		if(!doneBefore) {//Only grant criteria if the advancement is not already granted
			for(String criterion : criteria) {
				CriterionProgress criterionProgress = getCriterionProgress(criterion);
				if(criterionProgress != null && !isGranted(criterionProgress)) {
					setGranted(criterionProgress);
					result = GrantCriteriaResult.CHANGED;
					setLastUpdate();
				}
			}
			
			if(isDone()) {
				return GrantCriteriaResult.COMPLETED;
			}
		}
		return result;
	}
	
	/**
	 * Revokes Criteria, does not update for the player
	 * 
	 * @param criteria The Criteria to revoke
	 * @return The result of this operation
	 */
	public GenericResult revokeCriteria(String... criteria) {
		GenericResult result = GenericResult.UNCHANGED;
		
		for(String criterion : criteria) {
			CriterionProgress criterionProgress = getCriterionProgress(criterion);
			if(criterionProgress != null && isGranted(criterionProgress)) {
				setUngranted(criterionProgress);
				result = GenericResult.CHANGED;
				setLastUpdate();
			}
		}
		
		return result;
	}
	
	/**
	 * Sets Criteria, does not update for the player
	 * 
	 * @param number The Criteria to set
	 * @return The result of this operation
	 */
	public SetCriteriaResult setCriteriaProgress(int number) {
		SetCriteriaResult result = SetCriteriaResult.UNCHANGED;
		boolean doneBefore = isDone();
		
		Iterable<String> awarded = getAwardedCriteria();
		Iterator<String> awardedIterator = awarded.iterator();
		long current = StreamSupport.stream(awarded.spliterator(), false).count();
		
		Iterable<String> missing = getRemainingCriteria();
		Iterator<String> missingIterator = missing.iterator();
		
		while(current < number && missingIterator.hasNext()) {
			String next = missingIterator.next();
			CriterionProgress criterionProgress = getCriterionProgress(next);
			setGranted(criterionProgress);
			current++;
			result = SetCriteriaResult.CHANGED;
			setLastUpdate();
		}
		
		while(current > number && awardedIterator.hasNext()) {
			String next = awardedIterator.next();
			CriterionProgress criterionProgress = getCriterionProgress(next);
			setUngranted(criterionProgress);
			current--;
			result = SetCriteriaResult.CHANGED;
			setLastUpdate();
		}
		
		if(!doneBefore && isDone()) {
			result = SetCriteriaResult.COMPLETED;
		}
		
		return result;
	}
	
	private static void setGranted(CriterionProgress criterionProgress) {
		criterionProgress.b();
	}
	
	private static void setUngranted(CriterionProgress criterionProgress) {
		criterionProgress.c();
	}
	
	private static boolean isGranted(CriterionProgress criterionProgress) {
		return criterionProgress.a();
	}
	
	/**
	 * Gets the remaining Criteria
	 * 
	 * @return The remaining Criteria
	 */
	public Iterable<String> getRemainingCriteria() {
		return getNmsProgress().e();
	}
	
	/**
	 * Gets the awarded Criteria
	 * 
	 * @return The awarded Criteria
	 */
	public Iterable<String> getAwardedCriteria() {
		return getNmsProgress().f();
	}
	
	/**
	 * Gets the Criteria Progress
	 * 
	 * @return The Criteria Progress
	 */
	public int getCriteriaProgress() {
		return Iterables.size(getAwardedCriteria());
	}
	
	/**
	 * Gets the Criterion Progress Instance by it's name
	 * 
	 * @param name The Criterion Name
	 * @return The CriterionProgress
	 */
	public CriterionProgress getCriterionProgress(String name) {
		return getNmsProgress().c(name);
	}
	
	/**
	 * Checks whether the Progress is Done
	 * 
	 * @return Whether the Progress is Done
	 */
	public boolean isDone() {
		return getNmsProgress().a();
	}
	
	/**
	 * Gets the nms progress instance
	 * 
	 * @return The nms progress instance
	 */
	public net.minecraft.advancements.AdvancementProgress getNmsProgress() {
		return nmsProgress;
	}
	
	/**
	 * Gets the timestamp for the last update or -1 if it has not been updated yet
	 * 
	 * @return The timestamp in milliseconds or -1 if it has not been updated yet
	 */
	public long getLastUpdate() {
		return lastUpdate;
	}
	
	/**
	 * Sets the timestamp for the last update to the current system time
	 * 
	 */
	public void setLastUpdate() {
		lastUpdate = System.currentTimeMillis();
	}
	
}