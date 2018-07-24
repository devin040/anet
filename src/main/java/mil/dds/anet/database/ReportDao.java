package mil.dds.anet.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;

import com.google.common.base.Joiner;

import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.beans.AuthorizationGroup;
import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Organization.OrganizationType;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Task;
import mil.dds.anet.beans.Position;
import mil.dds.anet.beans.Report;
import mil.dds.anet.beans.Report.ReportState;
import mil.dds.anet.beans.ReportPerson;
import mil.dds.anet.beans.ReportSensitiveInformation;
import mil.dds.anet.beans.RollupGraph;
import mil.dds.anet.beans.Tag;
import mil.dds.anet.beans.lists.AbstractAnetBeanList.ReportList;
import mil.dds.anet.beans.search.OrganizationSearchQuery;
import mil.dds.anet.beans.search.ReportSearchQuery;
import mil.dds.anet.database.AdminDao.AdminSettingKeys;
import mil.dds.anet.database.mappers.AuthorizationGroupMapper;
import mil.dds.anet.database.mappers.TaskMapper;
import mil.dds.anet.database.mappers.ReportMapper;
import mil.dds.anet.database.mappers.ReportPersonMapper;
import mil.dds.anet.database.mappers.TagMapper;
import mil.dds.anet.search.sqlite.SqliteReportSearcher;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.utils.Utils;

public class ReportDao implements IAnetDao<Report> {

	private static final String[] fields = { "uuid", "state", "createdAt", "updatedAt", "engagementDate",
			"locationUuid", "approvalStepUuid", "intent", "exsum", "atmosphere", "cancelledReason",
			"advisorOrganizationUuid", "principalOrganizationUuid", "releasedAt",
			"atmosphereDetails", "text", "keyOutcomes",
			"nextSteps", "authorUuid"};
	private static final String tableName = "reports";
	public static final String REPORT_FIELDS = DaoUtils.buildFieldAliases(tableName, fields, true);

	private final Handle dbHandle;
	private final String weekFormat;

	public ReportDao(Handle db) {
		this.dbHandle = db;
		this.weekFormat = getWeekFormat(DaoUtils.getDbType(db));
	}

	private String getWeekFormat(DaoUtils.DbType dbType) {
		switch (dbType) {
			case MSSQL:
				return "DATEPART(week, %s)";
			case SQLITE:
				return "strftime('%%%%W', substr(%s, 1, 10))";
			case POSTGRESQL:
				return "EXTRACT(WEEK FROM %s)";
			default:
				throw new RuntimeException("No week format found for " + dbType);
		}
	}

	public ReportList getAll(int pageNum, int pageSize) {
		// Return the reports without sensitive information
		return getAll(pageNum, pageSize, null);
	}

	public ReportList getAll(int pageNum, int pageSize, Person user) {
		String sql = DaoUtils.buildPagedGetAllSql(DaoUtils.getDbType(dbHandle),
				"Reports", "reports join people on reports.\"authorUuid\" = people.uuid", REPORT_FIELDS + ", " + PersonDao.PERSON_FIELDS,
				"reports.\"createdAt\"");
		Query<Report> query = dbHandle.createQuery(sql)
			.bind("limit", pageSize)
			.bind("offset", pageSize * pageNum)
			.map(new ReportMapper());
		return ReportList.fromQuery(user, query, pageNum, pageSize);
	}

	public Report insert(Report r) {
		// Create a report without sensitive information
		return insert(r, null);
	}

	public Report insert(Report r, Person user) {
		return dbHandle.inTransaction(new TransactionCallback<Report>() {
			@Override
			public Report inTransaction(Handle conn, TransactionStatus status) throws Exception {
				DaoUtils.setInsertFields(r);

				//MSSQL requires explicit CAST when a datetime2 might be NULL.
				StringBuilder sql = new StringBuilder("/* insertReport */ INSERT INTO reports "
						+ "(uuid, state, \"createdAt\", \"updatedAt\", \"locationUuid\", intent, exsum, "
						+ "text, \"keyOutcomes\", \"nextSteps\", \"authorUuid\", "
						+ "\"engagementDate\", \"releasedAt\", atmosphere, \"cancelledReason\", "
						+ "\"atmosphereDetails\", \"advisorOrganizationUuid\", "
						+ "\"principalOrganizationUuid\") VALUES "
						+ "(:uuid, :state, :createdAt, :updatedAt, :locationUuid, :intent, "
						+ ":exsum, :reportText, :keyOutcomes, "
						+ ":nextSteps, :authorUuid, ");
				if (DaoUtils.isMsSql(dbHandle)) {
					sql.append("CAST(:engagementDate AS datetime2), CAST(:releasedAt AS datetime2), ");
				} else {
					sql.append(":engagementDate, :releasedAt, ");
				}
				sql.append(":atmosphere, :cancelledReason, :atmosphereDetails, :advisorOrgUuid, :principalOrgUuid)");

				dbHandle.createStatement(sql.toString())
					.bindFromProperties(r)
					.bind("state", DaoUtils.getEnumId(r.getState()))
					.bind("atmosphere", DaoUtils.getEnumId(r.getAtmosphere()))
					.bind("cancelledReason", DaoUtils.getEnumId(r.getCancelledReason()))
					.bind("locationUuid", DaoUtils.getUuid(r.getLocation()))
					.bind("authorUuid", DaoUtils.getUuid(r.getAuthor()))
					.bind("advisorOrgUuid", DaoUtils.getUuid(r.getAdvisorOrg()))
					.bind("principalOrgUuid", DaoUtils.getUuid(r.getPrincipalOrg()))
					.execute();

				// Write sensitive information (if allowed)
				final ReportSensitiveInformation rsi = AnetObjectEngine.getInstance().getReportSensitiveInformationDao().insert(r.getReportSensitiveInformation(), user, r);
				r.setReportSensitiveInformation(rsi);

				final ReportBatch rb = dbHandle.attach(ReportBatch.class);
				if (r.getAttendees() != null) {
					//Setify based on attendeeUuid to prevent violations of unique key constraint.
					Map<String,ReportPerson> attendeeMap = new HashMap<>();
					r.getAttendees().stream().forEach(rp -> attendeeMap.put(rp.getUuid(), rp));
					rb.insertReportAttendees(r.getUuid(), new ArrayList<ReportPerson>(attendeeMap.values()));
				}

				if (r.getAuthorizationGroups() != null) {
					rb.insertReportAuthorizationGroups(r.getUuid(), r.getAuthorizationGroups());
				}
				if (r.getTasks() != null) {
					rb.insertReportTasks(r.getUuid(), r.getTasks());
				}
				if (r.getTags() != null) {
					rb.insertReportTags(r.getUuid(), r.getTags());
				}
				return r;
			}
		});
	}

	public interface ReportBatch {
		@SqlBatch("INSERT INTO \"reportPeople\" (\"reportUuid\", \"personUuid\", \"isPrimary\") VALUES (:reportUuid, :uuid, :primary)")
		void insertReportAttendees(@Bind("reportUuid") String reportUuid,
				@BindBean List<ReportPerson> reportPeople);

		@SqlBatch("INSERT INTO \"reportAuthorizationGroups\" (\"reportUuid\", \"authorizationGroupUuid\") VALUES (:reportUuid, :uuid)")
		void insertReportAuthorizationGroups(@Bind("reportUuid") String reportUuid,
				@BindBean List<AuthorizationGroup> authorizationGroups);

		@SqlBatch("INSERT INTO \"reportTasks\" (\"reportUuid\", \"taskUuid\") VALUES (:reportUuid, :uuid)")
		void insertReportTasks(@Bind("reportUuid") String reportUuid,
				@BindBean List<Task> tasks);

		@SqlBatch("INSERT INTO \"reportTags\" (\"reportUuid\", \"tagUuid\") VALUES (:reportUuid, :uuid)")
		void insertReportTags(@Bind("reportUuid") String reportUuid,
				@BindBean List<Tag> tags);
	}

	public Report getByUuid(String uuid) {
		// Return the report without sensitive information
		return getByUuid(uuid, null);
	}

	public Report getByUuid(String uuid, Person user) {
		/* Check whether uuid is purely numerical, and if so, query on legacyId */
		final String queryDescriptor;
		final String keyField;
		final Object key;
		final Integer legacyId = Utils.getInteger(uuid);
		if (legacyId != null) {
			queryDescriptor = "getReportByLegacyId";
			keyField = "legacyId";
			key = legacyId;
		}
		else {
			queryDescriptor = "getReportByUuid";
			keyField = "uuid";
			key = uuid;
		}
		final Report result = dbHandle.createQuery("/* " + queryDescriptor + " */ SELECT " + REPORT_FIELDS + ", " + PersonDao.PERSON_FIELDS
				+ "FROM reports, people "
				+ "WHERE reports.\"" + keyField + "\" = :key "
				+ "AND reports.\"authorUuid\" = people.uuid")
				.bind("key", key)
				.map(new ReportMapper())
				.first();
		if (result == null) { return null; }
		result.setUser(user);
		return result;
	}

	/** This should always be wrapped in a transaction! But actually it's never used at all. */
	public int update(Report r) {
		// Update the report without sensitive information
		return update(r, null);
	}

	/** NOTE: this should always be wrapped in a transaction! (If JDBI were able to handle nested calls to inTransaction, we would have
	 * one inside this method, but it isn't.)
	 * @param r the report to update, in its updated state
	 * @param user the user attempting the update, for authorization purposes
	 * @return the number of rows updated by the final update call (should be 1 in all cases).
	 */
	public int update(Report r, Person user) {
		// Write sensitive information (if allowed)
		AnetObjectEngine.getInstance().getReportSensitiveInformationDao().insertOrUpdate(r.getReportSensitiveInformation(), user, r);

		DaoUtils.setUpdateFields(r);

		StringBuilder sql = new StringBuilder("/* updateReport */ UPDATE reports SET "
				+ "state = :state, \"updatedAt\" = :updatedAt, \"locationUuid\" = :locationUuid, "
				+ "intent = :intent, exsum = :exsum, text = :reportText, "
				+ "\"keyOutcomes\" = :keyOutcomes, \"nextSteps\" = :nextSteps, "
				+ "\"approvalStepUuid\" = :approvalStepUuid, ");
		if (DaoUtils.isMsSql(dbHandle)) {
			sql.append("\"engagementDate\" = CAST(:engagementDate AS datetime2), \"releasedAt\" = CAST(:releasedAt AS datetime2), ");
		} else {
			sql.append("\"engagementDate\" = :engagementDate, \"releasedAt\" = :releasedAt, ");
		}
		sql.append("atmosphere = :atmosphere, \"atmosphereDetails\" = :atmosphereDetails, "
				+ "\"cancelledReason\" = :cancelledReason, "
				+ "\"principalOrganizationUuid\" = :principalOrgUuid, \"advisorOrganizationUuid\" = :advisorOrgUuid "
				+ "WHERE uuid = :uuid");

		return dbHandle.createStatement(sql.toString())
			.bindFromProperties(r)
			.bind("state", DaoUtils.getEnumId(r.getState()))
			.bind("locationUuid", DaoUtils.getUuid(r.getLocation()))
			.bind("authorUuid", DaoUtils.getUuid(r.getAuthor()))
			.bind("approvalStepUuid", DaoUtils.getUuid(r.getApprovalStep()))
			.bind("atmosphere", DaoUtils.getEnumId(r.getAtmosphere()))
			.bind("cancelledReason", DaoUtils.getEnumId(r.getCancelledReason()))
			.bind("advisorOrgUuid", DaoUtils.getUuid(r.getAdvisorOrg()))
			.bind("principalOrgUuid", DaoUtils.getUuid(r.getPrincipalOrg()))
			.execute();
	}

	public void updateToDraftState(Report r) {
		dbHandle.execute("/* UpdateFutureEngagement */ UPDATE reports SET state = ? "
				+ "WHERE uuid = ?", DaoUtils.getEnumId(ReportState.DRAFT), r.getUuid());
	}

	public int addAttendeeToReport(ReportPerson rp, Report r) {
		return dbHandle.createStatement("/* addReportAttendee */ INSERT INTO \"reportPeople\" "
				+ "(\"personUuid\", \"reportUuid\", \"isPrimary\") VALUES (:personUuid, :reportUuid, :isPrimary)")
			.bind("personUuid", rp.getUuid())
			.bind("reportUuid", r.getUuid())
			.bind("isPrimary", rp.isPrimary())
			.execute();
	}

	public int removeAttendeeFromReport(Person p, Report r) {
		return dbHandle.createStatement("/* deleteReportAttendee */ DELETE FROM \"reportPeople\" "
				+ "WHERE \"reportUuid\" = :reportUuid AND \"personUuid\" = :personUuid")
			.bind("reportUuid", r.getUuid())
			.bind("personUuid", p.getUuid())
			.execute();
	}

	public int updateAttendeeOnReport(ReportPerson rp, Report r) {
		return dbHandle.createStatement("/* updateAttendeeOnReport*/ UPDATE \"reportPeople\" "
				+ "SET \"isPrimary\" = :isPrimary WHERE \"reportUuid\" = :reportUuid AND \"personUuid\" = :personUuid")
			.bind("reportUuid", r.getUuid())
			.bind("personUuid", rp.getUuid())
			.bind("isPrimary", rp.isPrimary())
			.execute();
	}


	public int addAuthorizationGroupToReport(AuthorizationGroup a, Report r) {
		return dbHandle.createStatement("/* addAuthorizationGroupToReport */ INSERT INTO \"reportAuthorizationGroups\" (\"authorizationGroupUuid\", \"reportUuid\") "
				+ "VALUES (:authorizationGroupUuid, :reportUuid)")
			.bind("reportUuid", r.getUuid())
			.bind("authorizationGroupUuid", a.getUuid())
			.execute();
	}

	public int removeAuthorizationGroupFromReport(AuthorizationGroup a, Report r) {
		return dbHandle.createStatement("/* removeAuthorizationGroupFromReport*/ DELETE FROM \"reportAuthorizationGroups\" "
				+ "WHERE \"reportUuid\" = :reportUuid AND \"authorizationGroupUuid\" = :authorizationGroupUuid")
				.bind("reportUuid", r.getUuid())
				.bind("authorizationGroupUuid", a.getUuid())
				.execute();
	}

	public int addTaskToReport(Task p, Report r) {
		return dbHandle.createStatement("/* addTaskToReport */ INSERT INTO \"reportTasks\" (\"taskUuid\", \"reportUuid\") "
				+ "VALUES (:taskUuid, :reportUuid)")
			.bind("reportUuid", r.getUuid())
			.bind("taskUuid", p.getUuid())
			.execute();
	}

	public int removeTaskFromReport(Task p, Report r) {
		return dbHandle.createStatement("/* removeTaskFromReport*/ DELETE FROM \"reportTasks\" "
				+ "WHERE \"reportUuid\" = :reportUuid AND \"taskUuid\" = :taskUuid")
				.bind("reportUuid", r.getUuid())
				.bind("taskUuid", p.getUuid())
				.execute();
	}

	public int addTagToReport(Tag t, Report r) {
		return dbHandle.createStatement("/* addTagToReport */ INSERT INTO \"reportTags\" (\"reportUuid\", \"tagUuid\") "
				+ "VALUES (:reportUuid, :tagUuid)")
			.bind("reportUuid", r.getUuid())
			.bind("tagUuid", t.getUuid())
			.execute();
	}

	public int removeTagFromReport(Tag t, Report r) {
		return dbHandle.createStatement("/* removeTagFromReport */ DELETE FROM \"reportTags\" "
				+ "WHERE \"reportUuid\" = :reportUuid AND \"tagUuid\" = :tagUuid")
				.bind("reportUuid", r.getUuid())
				.bind("tagUuid", t.getUuid())
				.execute();
	}

	public List<ReportPerson> getAttendeesForReport(String reportUuid) {
		return dbHandle.createQuery("/* getAttendeesForReport */ SELECT " + PersonDao.PERSON_FIELDS 
				+ ", \"reportPeople\".\"isPrimary\" FROM \"reportPeople\" "
				+ "LEFT JOIN people ON \"reportPeople\".\"personUuid\" = people.uuid "
				+ "WHERE \"reportPeople\".\"reportUuid\" = :reportUuid")
			.bind("reportUuid", reportUuid)
			.map(new ReportPersonMapper())
			.list();
	}


	public List<AuthorizationGroup> getAuthorizationGroupsForReport(String reportUuid) {
		return dbHandle.createQuery("/* getAuthorizationGroupsForReport */ SELECT * FROM \"authorizationGroups\", \"reportAuthorizationGroups\" "
				+ "WHERE \"reportAuthorizationGroups\".\"reportUuid\" = :reportUuid "
				+ "AND \"reportAuthorizationGroups\".\"authorizationGroupUuid\" = \"authorizationGroups\".uuid")
				.bind("reportUuid", reportUuid)
				.map(new AuthorizationGroupMapper())
				.list();
	}

	public List<Task> getTasksForReport(Report report) {
		return dbHandle.createQuery("/* getTasksForReport */ SELECT * FROM tasks, \"reportTasks\" "
				+ "WHERE \"reportTasks\".\"reportUuid\" = :reportUuid "
				+ "AND \"reportTasks\".\"taskUuid\" = tasks.uuid")
				.bind("reportUuid", report.getUuid())
				.map(new TaskMapper())
				.list();
	}

	public List<Tag> getTagsForReport(String reportUuid) {
		return dbHandle.createQuery("/* getTagsForReport */ SELECT * FROM \"reportTags\" "
				+ "INNER JOIN tags ON \"reportTags\".\"tagUuid\" = tags.uuid "
				+ "WHERE \"reportTags\".\"reportUuid\" = :reportUuid "
				+ "ORDER BY tags.name")
			.bind("reportUuid", reportUuid)
			.map(new TagMapper())
			.list();
	}

	//Does an unauthenticated search. This will never return any DRAFT or REJECTED reports
	public ReportList search(ReportSearchQuery query) { 
		return search(query, null);
	}
	
	public ReportList search(ReportSearchQuery query, Person user) {
		return AnetObjectEngine.getInstance().getSearcher().getReportSearcher()
			.runSearch(query, dbHandle, user);
	}

	/*
	 * Deletes a given report from the database. 
	 * Ensures consistency by removing all references to a report before deleting a report. 
	 */
	public void deleteReport(final Report report) {
		dbHandle.inTransaction(new TransactionCallback<Void>() {
			public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
				// Delete tags
				dbHandle.execute("/* deleteReport.tags */ DELETE FROM \"reportTags\" where \"reportUuid\" = ?", report.getUuid());

				//Delete tasks
				dbHandle.execute("/* deleteReport.tasks */ DELETE FROM \"reportTasks\" where \"reportUuid\" = ?", report.getUuid());
				
				//Delete attendees
				dbHandle.execute("/* deleteReport.attendees */ DELETE FROM \"reportPeople\" where \"reportUuid\" = ?", report.getUuid());
				
				//Delete comments
				dbHandle.execute("/* deleteReport.comments */ DELETE FROM comments where \"reportUuid\" = ?", report.getUuid());
				
				//Delete \"approvalActions\"
				dbHandle.execute("/* deleteReport.actions */ DELETE FROM \"approvalActions\" where \"reportUuid\" = ?", report.getUuid());

				//Delete relation to authorization groups
				dbHandle.execute("/* deleteReport.\"authorizationGroups\" */ DELETE FROM \"reportAuthorizationGroups\" where \"reportUuid\" = ?", report.getUuid());

				//Delete report
				dbHandle.execute("/* deleteReport.report */ DELETE FROM reports where uuid = ?", report.getUuid());

				return null;
			}
		});
		
	}

	private DateTime getRollupEngagmentStart(DateTime start) { 
		String maxReportAgeStr = AnetObjectEngine.getInstance().getAdminSetting(AdminSettingKeys.DAILY_ROLLUP_MAX_REPORT_AGE_DAYS);
		if (maxReportAgeStr == null) { 
			throw new WebApplicationException("Missing Admin Setting for " + AdminSettingKeys.DAILY_ROLLUP_MAX_REPORT_AGE_DAYS); 
		} 
		Integer maxReportAge = Integer.parseInt(maxReportAgeStr);
		return start.minusDays(maxReportAge);
	}
	
	/* Generates the Rollup Graph for a particular Organization Type, starting at the root of the org hierarchy */
	public List<RollupGraph> getDailyRollupGraph(DateTime start, DateTime end, OrganizationType orgType, Map<String, Organization> nonReportingOrgs) {
		final List<Map<String, Object>> results = rollupQuery(start, end, orgType, null, false);
		final Map<String,Organization> orgMap = AnetObjectEngine.getInstance().buildTopLevelOrgHash(orgType);
		
		return generateRollupGraphFromResults(results, orgMap, nonReportingOrgs);
	}
	
	/* Generates a Rollup graph for a particular organization.  Starting with a given parent Organization */
	public List<RollupGraph> getDailyRollupGraph(DateTime start, DateTime end, String parentOrgUuid, OrganizationType orgType, Map<String, Organization> nonReportingOrgs) {
		List<Organization> orgList = null;
		final Map<String, Organization> orgMap;
		if (!parentOrgUuid.equals(Organization.DUMMY_ORG_UUID)) {
			//doing this as two separate queries because I do need all the information about the organizations
			OrganizationSearchQuery query = new OrganizationSearchQuery();
			query.setParentOrgUuid(parentOrgUuid);
			query.setParentOrgRecursively(true);
			query.setPageSize(Integer.MAX_VALUE);
			orgList = AnetObjectEngine.getInstance().getOrganizationDao().search(query).getList();
			Optional<Organization> parentOrg = orgList.stream().filter(o -> o.getUuid().equals(parentOrgUuid)).findFirst();
			if (parentOrg.isPresent() == false) { 
				throw new WebApplicationException("No such organization with uuid " + parentOrgUuid, Status.NOT_FOUND);
			}
			orgMap  = Utils.buildParentOrgMapping(orgList, parentOrgUuid);
		} else { 
			orgMap = new HashMap<String, Organization>(); //guaranteed to match no orgs!
		}
		
		final List<Map<String,Object>> results = rollupQuery(start, end, orgType, orgList, parentOrgUuid.equals(Organization.DUMMY_ORG_UUID));
		
		return generateRollupGraphFromResults(results, orgMap, nonReportingOrgs);
	}

	/* Generates Advisor Report Insights for Organizations */
	public List<Map<String,Object>> getAdvisorReportInsights(DateTime start, DateTime end, String orgUuid) {
		final Map<String,Object> sqlArgs = new HashMap<String,Object>();
		StringBuilder sql = new StringBuilder();

		sql.append("/* AdvisorReportInsightsQuery */ ");
		sql.append("SELECT ");
		sql.append("CASE WHEN a.\"organizationUuid\" IS NULL THEN b.\"organizationUuid\" ELSE a.\"organizationUuid\" END AS \"organizationUuid\",");
		sql.append("CASE WHEN a.\"organizationShortName\" IS NULL THEN b.\"organizationShortName\" ELSE a.\"organizationShortName\" END AS \"organizationShortName\",");
		sql.append("%1$s");
		sql.append("%2$s");
		sql.append("CASE WHEN a.week IS NULL THEN b.week ELSE a.week END AS week,");
		sql.append("CASE WHEN a.\"nrReportsSubmitted\" IS NULL THEN 0 ELSE a.\"nrReportsSubmitted\" END AS \"nrReportsSubmitted\",");
		sql.append("CASE WHEN b.\"nrEngagementsAttended\" IS NULL THEN 0 ELSE b.\"nrEngagementsAttended\" END AS \"nrEngagementsAttended\"");

		sql.append(" FROM (");

			sql.append("SELECT ");
			sql.append("organizations.uuid AS \"organizationUuid\",");
			sql.append("organizations.\"shortName\" AS \"organizationShortName\",");
			sql.append("%3$s");
			sql.append("%4$s");
			sql.append(" " + String.format(weekFormat, "reports.\"createdAt\"") + " AS week,");
			sql.append("COUNT(reports.\"authorUuid\") AS \"nrReportsSubmitted\"");

			sql.append(" FROM ");
			sql.append("positions,");
			sql.append("reports,");
			sql.append("%5$s");
			sql.append("organizations");

			sql.append(" WHERE positions.\"currentPersonUuid\" = reports.\"authorUuid\"");
			sql.append(" %6$s");
			sql.append(" AND reports.\"advisorOrganizationUuid\" = organizations.uuid");
			sql.append(" AND positions.type = :positionAdvisor");
			sql.append(" AND reports.state IN ( :reportReleased, :reportPending, :reportDraft )");
			sql.append(" AND reports.\"createdAt\" BETWEEN :startDate and :endDate");
			sql.append(" %11$s");

			sql.append(" GROUP BY ");
			sql.append("organizations.uuid,");
			sql.append("organizations.\"shortName\",");
			sql.append("%7$s");
			sql.append("%8$s");
			sql.append(" " + String.format(weekFormat, "reports.\"createdAt\""));
		sql.append(") a");

		sql.append(" FULL OUTER JOIN (");
			sql.append("SELECT ");
			sql.append("organizations.uuid AS \"organizationUuid\",");
			sql.append("organizations.\"shortName\" AS \"organizationShortName\",");
			sql.append("%3$s");
			sql.append("%4$s");
			sql.append(" " + String.format(weekFormat, "reports.\"engagementDate\"") + " AS week,");
			sql.append("COUNT(\"reportPeople\".\"personUuid\") AS \"nrEngagementsAttended\"");

			sql.append(" FROM ");
			sql.append("positions,");
			sql.append("%5$s");
			sql.append("reports,");
			sql.append("\"reportPeople\",");
			sql.append("organizations");

			sql.append(" WHERE positions.\"currentPersonUuid\" = \"reportPeople\".personUuid");
			sql.append(" %6$s");
			sql.append(" AND \"reportPeople\".\"reportUuid\" = reports.uuid");
			sql.append(" AND reports.\"advisorOrganizationUuid\" = organizations.uuid");
			sql.append(" AND positions.type = :positionAdvisor");
			sql.append(" AND reports.state IN ( :reportReleased, :reportPending, :reportDraft )");
			sql.append(" AND reports.\"engagementDate\" BETWEEN :startDate and :endDate");
			sql.append(" %11$s");

			sql.append(" GROUP BY ");
			sql.append("organizations.uuid,");
			sql.append("organizations.\"shortName\",");
			sql.append("%7$s");
			sql.append("%8$s");
			sql.append(" " + String.format(weekFormat, "reports.\"engagementDate\""));
		sql.append(") b");

		sql.append(" ON ");
		sql.append(" a.\"organizationUuid\" = b.\"organizationUuid\"");
		sql.append(" %9$s");
		sql.append(" AND a.week = b.week");

		sql.append(" ORDER BY ");
		sql.append("\"organizationShortName\",");
		sql.append("%10$s");
		sql.append("week;");

		final Object[] fmtArgs;
		if (!Organization.DUMMY_ORG_UUID.equals(orgUuid)) {
			fmtArgs = new String[] {
					"CASE WHEN a.\"personUuid\" IS NULL THEN b.\"personUuid\" ELSE a.\"personUuid\" END AS \"personUuid\",",
					"CASE WHEN a.name IS NULL THEN b.name ELSE a.name END AS name,",
					"people.uuid AS \"personUuid\",",
					"people.name AS name,",
					"people,",
					"AND positions.\"currentPersonUuid\" = people.uuid",
					"people.uuid,",
					"people.name,",
					"AND a.\"personUuid\" = b.\"personUuid\"",
					"name,",
					"AND organizations.uuid = :organizationUuid"};
			sqlArgs.put("organizationUuid", orgUuid);
		} else {
			fmtArgs = new String[] {
					"",
					"",
					"",
					"",
					"",
					"",
					"",
					"",
					"",
					"",
					""};
		}

		sqlArgs.put("startDate", start);
		sqlArgs.put("endDate", end);
		sqlArgs.put("positionAdvisor", Position.PositionType.ADVISOR.ordinal());
		sqlArgs.put("reportDraft", ReportState.DRAFT.ordinal());
		sqlArgs.put("reportPending", ReportState.PENDING_APPROVAL.ordinal());
		sqlArgs.put("reportReleased", ReportState.RELEASED.ordinal());

		return dbHandle.createQuery(String.format(sql.toString(), fmtArgs))
			.bindFromMap(sqlArgs)
			.map(new DefaultMapper(false))
			.list();
	}

	/** Helper method that builds and executes the daily rollup query
	 * Handles both MsSql and Sqlite
	 * Searching for just all reports and for reports in certain organizations.
	 * @param orgType: the type of organization to be looking for
	 * @param orgs: the list of orgs for whose reports to find, null means all
	 * @param missingOrgReports: true if we want to look for reports specifically with NULL org uuid's.
	 */
	private List<Map<String,Object>> rollupQuery(DateTime start, 
			DateTime end, 
			OrganizationType orgType, 
			List<Organization> orgs, 
			boolean missingOrgReports) { 
		String orgColumn = String.format("\"%s\"", orgType == OrganizationType.ADVISOR_ORG ? "advisorOrganizationUuid" : "principalOrganizationUuid");
		Map<String,Object> sqlArgs = new HashMap<String,Object>();
		
		StringBuilder sql = new StringBuilder();
		sql.append("/* RollupQuery */ SELECT " + orgColumn + " as \"orgUuid\", state, count(*) AS count ");
		sql.append("FROM reports WHERE ");

		// NOTE: more date-comparison work here that might be worth abstracting, but might not
		if (DaoUtils.getDbType(dbHandle) != DaoUtils.DbType.SQLITE) {
			sql.append("\"releasedAt\" >= :startDate and \"releasedAt\" < :endDate "
					+ "AND \"engagementDate\" > :engagementDateStart ");
			sqlArgs.put("startDate", start);
			sqlArgs.put("endDate", end.plusMillis(1));
			sqlArgs.put("engagementDateStart", getRollupEngagmentStart(start));
		} else { 
			sql.append("\"releasedAt\"  >= DateTime(:startDate) AND \"releasedAt\" <= DateTime(:endDate) "
					+ "AND \"engagementDate\" > DateTime(:engagementDateStart) ");
			sqlArgs.put("startDate", SqliteReportSearcher.sqlitePattern.print(start));
			sqlArgs.put("endDate", SqliteReportSearcher.sqlitePattern.print(end));
			sqlArgs.put("engagementDateStart", SqliteReportSearcher.sqlitePattern.print(getRollupEngagmentStart(start)));
		}
		
		if (orgs != null) { 
			List<String> sqlBind = new LinkedList<String>();
			int orgNum = 0; 
			for (Organization o : orgs) { 
				sqlArgs.put("orgUuid" + orgNum, o.getUuid());
				sqlBind.add(":orgUuid" + orgNum);
				orgNum++;
			}
			String orgInSql = Joiner.on(',').join(sqlBind);
			sql.append("AND " + orgColumn + " IN (" + orgInSql + ") ");
		} else if (missingOrgReports) { 
			sql.append(" AND " + orgColumn + " IS NULL ");
		}
		
		sql.append("GROUP BY " + orgColumn + ", state");

		return dbHandle.createQuery(sql.toString())
			.bindFromMap(sqlArgs)
			.map(new DefaultMapper(false))
			.list();
	}
	
	/* Given the results from the database on the number of reports grouped by organization
	 * And the map of each organization to the organization that their reports roll up to
	 * this method returns the final rollup graph information. 
	 */
	private List<RollupGraph> generateRollupGraphFromResults(List<Map<String, Object>> dbResults, Map<String, Organization> orgMap, Map<String, Organization> nonReportingOrgs) {
		final Map<String, Map<ReportState,Integer>> rollup = new HashMap<>();
		
		for (Map<String,Object> result : dbResults) { 
			final String orgUuid = (String) result.get("orgUuid");
			if (nonReportingOrgs.containsKey(orgUuid)) {
				// Skip non-reporting organizations
				continue;
			}
			final Integer count = ((Number) result.get("count")).intValue();
			final ReportState state = ReportState.values()[(Integer) result.get("state")];
		
			final String parentOrgUuid = DaoUtils.getUuid(orgMap.get(orgUuid));
			Map<ReportState,Integer> orgBar = rollup.get(parentOrgUuid);
			if (orgBar == null) { 
				orgBar = new HashMap<ReportState,Integer>();
				rollup.put(parentOrgUuid, orgBar);
			}
			orgBar.put(state,  Utils.orIfNull(orgBar.get(state), 0) + count);
		}

		// Add all (top-level) organizations without any reports
		for (final Map.Entry<String, Organization> entry : orgMap.entrySet()) {
			final String orgUuid = entry.getKey();
			if (nonReportingOrgs.containsKey(orgUuid)) {
				// Skip non-reporting organizations
				continue;
			}
			final String parentOrgUuid = DaoUtils.getUuid(orgMap.get(orgUuid));
			if (!rollup.keySet().contains(parentOrgUuid)) {
				final Map<ReportState, Integer> orgBar = new HashMap<ReportState, Integer>();
				orgBar.put(ReportState.RELEASED, 0);
				orgBar.put(ReportState.CANCELLED, 0);
				rollup.put(parentOrgUuid, orgBar);
			}
		}

		final List<RollupGraph> result = new LinkedList<RollupGraph>();
		for (Map.Entry<String, Map<ReportState,Integer>> entry : rollup.entrySet()) {
			Map<ReportState,Integer> values = entry.getValue();
			RollupGraph bar = new RollupGraph();
			bar.setOrg(orgMap.get(entry.getKey()));
			bar.setReleased(Utils.orIfNull(values.get(ReportState.RELEASED), 0));
			bar.setCancelled(Utils.orIfNull(values.get(ReportState.CANCELLED), 0));
			result.add(bar);
		}
		
		return result;
	}
}
