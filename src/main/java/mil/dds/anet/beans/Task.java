package mil.dds.anet.beans;

import java.util.Objects;

import org.joda.time.DateTime;

import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.graphql.GraphQLFetcher;
import mil.dds.anet.graphql.GraphQLIgnore;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.utils.Utils;
import mil.dds.anet.views.AbstractAnetBean;

public class Task extends AbstractAnetBean {

	public enum TaskStatus { ACTIVE, INACTIVE }
	
	DateTime plannedCompletion;
	DateTime projectedCompletion;

	String shortName;
	String longName;
	String category;
	String customField;
	String customFieldEnum1;
	String customFieldEnum2;

	Task customFieldRef1;

	TaskStatus status;

	Organization responsibleOrg;

	public void setPlannedCompletion(DateTime plannedCompletion) {
		this.plannedCompletion = plannedCompletion;
	}

	public DateTime getPlannedCompletion() {
		return plannedCompletion;
	}

	public void setProjectedCompletion(DateTime projectedCompletion) {
		this.projectedCompletion = projectedCompletion;
	}

	public DateTime getProjectedCompletion() {
		return projectedCompletion;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = Utils.trimStringReturnNull(shortName);
	}

	public String getLongName() {
		return longName;
	}

	public void setLongName(String longName) {
		this.longName = Utils.trimStringReturnNull(longName);
	}

	public String getCustomField() {
		return customField;
	}

	public void setCustomField(String customField) {
		this.customField = Utils.trimStringReturnNull(customField);
	}

	public String getCustomFieldEnum1() {
		return customFieldEnum1;
	}

	public void setCustomFieldEnum1(String customFieldEnum1) {
		this.customFieldEnum1 = Utils.trimStringReturnNull(customFieldEnum1);
	}

	public String getCustomFieldEnum2() {
		return customFieldEnum2;
	}

	public void setCustomFieldEnum2(String customFieldEnum2) {
		this.customFieldEnum2 = Utils.trimStringReturnNull(customFieldEnum2);
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = Utils.trimStringReturnNull(category);
	}

	@GraphQLFetcher("customFieldRef1")
	public Task loadCustomFieldRef1() {
		if (customFieldRef1 == null || customFieldRef1.getLoadLevel() == null) { return customFieldRef1; }
		if (customFieldRef1.getLoadLevel().contains(LoadLevel.PROPERTIES) == false) {
			this.customFieldRef1 = AnetObjectEngine.getInstance()
					.getTaskDao().getByUuid(customFieldRef1.getUuid());
		}
		return customFieldRef1;
	}

	public void setCustomFieldRef1(Task customFieldRef1) {
		this.customFieldRef1 = customFieldRef1;
	}

	@GraphQLIgnore
	public Task getCustomFieldRef1() {
		return this.customFieldRef1;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public void setResponsibleOrg(Organization org) { 
		this.responsibleOrg = org;
	}

	@GraphQLFetcher("responsibleOrg")
	public Organization loadResponsibleOrg() {
		if (responsibleOrg == null || responsibleOrg.getLoadLevel() == null) { return responsibleOrg; } 
		if (responsibleOrg.getLoadLevel().contains(LoadLevel.PROPERTIES) == false) { 
			this.responsibleOrg = AnetObjectEngine.getInstance()
					.getOrganizationDao().getByUuid(responsibleOrg.getUuid());
		}
		return responsibleOrg;
	}

	@GraphQLIgnore
	public Organization getResponsibleOrg() { 
		return responsibleOrg;
	}

	public static Task createWithUuid(String uuid) {
		final Task p = new Task();
		p.setUuid(uuid);
		p.setLoadLevel(LoadLevel.ID_ONLY);
		return p;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || o.getClass() != this.getClass()) {
			return false;
		}
		Task other = (Task) o;
		return Objects.equals(other.getUuid(), uuid)
				&& Objects.equals(other.getShortName(), shortName)
				&& Objects.equals(other.getLongName(), longName)
				&& Objects.equals(other.getCategory(), category)
				&& uuidEqual(other.getCustomFieldRef1(), customFieldRef1);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uuid, shortName, longName, category, customFieldRef1);
	}

	@Override
	public String toString() {
		return String.format("[uuid:%s shortName:%s category:%s customFieldRef1:%s]", uuid, shortName, category, DaoUtils.getUuid(customFieldRef1));
	}

}
