package mil.dds.anet.database;

import java.util.Collections;
import java.util.List;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.unstable.BindIn;

import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Organization.OrganizationStatus;
import mil.dds.anet.beans.Organization.OrganizationType;
import mil.dds.anet.beans.lists.AbstractAnetBeanList.OrganizationList;
import mil.dds.anet.beans.search.OrganizationSearchQuery;
import mil.dds.anet.database.mappers.OrganizationMapper;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.utils.Utils;

public class OrganizationDao extends AnetBaseDao<Organization> {

	private static String[] fields = {"uuid", "shortName", "longName", "status", "identificationCode", "type", "createdAt", "updatedAt", "parentOrgUuid"};
	private static String tableName = "organizations";
	public static String ORGANIZATION_FIELDS = DaoUtils.buildFieldAliases(tableName, fields, true);
	
	public OrganizationDao(Handle dbHandle) { 
		super(dbHandle, "Orgs", tableName, ORGANIZATION_FIELDS, null);
	}
	
	public OrganizationList getAll(int pageNum, int pageSize) {
		Query<Organization> query = getPagedQuery(pageNum, pageSize, new OrganizationMapper());
		Long manualRowCount = getSqliteRowCount();
		return OrganizationList.fromQuery(query, pageNum, pageSize, manualRowCount);
	}

	public Organization getByUuid(String uuid) {
		return dbHandle.createQuery(
				"/* getOrgByUuid */ SELECT " + ORGANIZATION_FIELDS + " from organizations where uuid = :uuid")
				.bind("uuid", uuid)
				.map(new OrganizationMapper())
				.first();
	}

	public List<Organization> getTopLevelOrgs(OrganizationType type) { 
		return dbHandle.createQuery("/* getTopLevelOrgs */ SELECT " + ORGANIZATION_FIELDS
				+ " FROM organizations "
				+ "WHERE \"parentOrgUuid\" IS NULL "
				+ "AND status = :status "
				+ "AND type = :type")
			.bind("status", DaoUtils.getEnumId(OrganizationStatus.ACTIVE))
			.bind("type", DaoUtils.getEnumId(type))
			.map(new OrganizationMapper())
			.list();
	}

	@UseStringTemplate3StatementLocator
	public interface OrgListQueries {
		@Mapper(OrganizationMapper.class)
		@SqlQuery("SELECT uuid AS organizations_uuid"
				+ ", uuid AS uuid"
				+ ", \"shortName\" AS organizations_shortName"
				+ ", \"longName\" AS organizations_longName"
				+ ", status AS organizations_status"
				+ ", \"identificationCode\" AS organizations_identificationCode"
				+ ", type AS organizations_type"
				+ ", \"parentOrgUuid\" AS organizations_parentOrgUuid"
				+ ", \"createdAt\" AS organizations_createdAt"
				+ ", \"updatedAt\" AS organizations_updatedAt"
				+ " FROM organizations WHERE \"shortName\" IN ( <shortNames> )")
		public List<Organization> getOrgsByShortNames(@BindIn("shortNames") List<String> shortNames);
	}

	public List<Organization> getOrgsByShortNames(List<String> shortNames) {
		if (Utils.isEmptyOrNull(shortNames)) {
			return Collections.emptyList();
		}
		return dbHandle.attach(OrgListQueries.class).getOrgsByShortNames(shortNames);
	}

	public Organization insert(Organization org) {
		DaoUtils.setInsertFields(org);
		dbHandle.createStatement(
				"/* insertOrg */ INSERT INTO organizations (uuid, \"shortName\", \"longName\", status, \"identificationCode\", type, \"createdAt\", \"updatedAt\", \"parentOrgUuid\") "
				+ "VALUES (:uuid, :shortName, :longName, :status, :identificationCode, :type, :createdAt, :updatedAt, :parentOrgUuid)")
			.bindFromProperties(org)
			.bind("status", DaoUtils.getEnumId(org.getStatus()))
			.bind("type", DaoUtils.getEnumId(org.getType()))
			.bind("parentOrgUuid", DaoUtils.getUuid(org.getParentOrg()))
			.execute();
		return org;
	}
	
	public int update(Organization org) {
		DaoUtils.setUpdateFields(org);
		int numRows = dbHandle.createStatement("/* updateOrg */ UPDATE organizations "
				+ "SET \"shortName\" = :shortName, \"longName\" = :longName, status = :status, \"identificationCode\" = :identificationCode, type = :type, "
				+ "\"updatedAt\" = :updatedAt, \"parentOrgUuid\" = :parentOrgUuid where uuid = :uuid")
				.bindFromProperties(org)
				.bind("status", DaoUtils.getEnumId(org.getStatus()))
				.bind("type", DaoUtils.getEnumId(org.getType()))
				.bind("parentOrgUuid", DaoUtils.getUuid(org.getParentOrg()))
				.execute();
			
		return numRows;
	}

	public OrganizationList search(OrganizationSearchQuery query) {
		return AnetObjectEngine.getInstance().getSearcher().getOrganizationSearcher()
				.runSearch(query, dbHandle);
	} 
}
