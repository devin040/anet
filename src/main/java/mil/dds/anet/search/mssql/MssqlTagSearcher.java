package mil.dds.anet.search.mssql;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jersey.repackaged.com.google.common.base.Joiner;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;

import mil.dds.anet.beans.Tag;
import mil.dds.anet.beans.lists.AbstractAnetBeanList.TagList;
import mil.dds.anet.beans.search.ISearchQuery.SortOrder;
import mil.dds.anet.beans.search.TagSearchQuery;
import mil.dds.anet.beans.search.TagSearchQuery.TagSearchSortBy;
import mil.dds.anet.database.mappers.TagMapper;
import mil.dds.anet.search.ITagSearcher;
import mil.dds.anet.utils.Utils;

public class MssqlTagSearcher implements ITagSearcher {

	@Override
	public TagList runSearch(TagSearchQuery query, Handle dbHandle) {
		final List<String> whereClauses = new LinkedList<String>();
		final Map<String,Object> sqlArgs = new HashMap<String,Object>();
		final StringBuilder sql = new StringBuilder("/* MssqlTagSearch */ SELECT tags.*");

		final String text = query.getText();
		final boolean doFullTextSearch = (text != null && !text.trim().isEmpty());
		if (doFullTextSearch) {
			// If we're doing a full-text search, add a pseudo-rank (the sum of all search ranks)
			// so we can sort on it (show the most relevant hits at the top).
			// Note that summing up independent ranks is not ideal, but it's the best we can do now.
			// See https://docs.microsoft.com/en-us/sql/relational-databases/search/limit-search-results-with-rank
			sql.append(", ISNULL(c_tags.rank, 0) + ISNULL(f_tags.rank, 0)");
			sql.append(" AS search_rank");
		}
		sql.append(", count(*) over() as totalCount FROM tags");

		if (doFullTextSearch) {
			sql.append(" LEFT JOIN CONTAINSTABLE (tags, (name, description), :containsQuery) c_tags"
					+ " ON tags.uuid = c_tags.[Key]"
					+ " LEFT JOIN FREETEXTTABLE(tags, (name, description), :freetextQuery) f_tags"
					+ " ON tags.uuid = f_tags.[Key]");
			whereClauses.add("c_tags.rank IS NOT NULL");
			sqlArgs.put("containsQuery", Utils.getSqlServerFullTextQuery(text));
			sqlArgs.put("freetextQuery", text);
		}

		final TagList result = new TagList();
		result.setPageNum(query.getPageNum());
		result.setPageSize(query.getPageSize());

		if (whereClauses.isEmpty()) {
			return result;
		}

		sql.append(" WHERE ");
		sql.append(Joiner.on(" AND ").join(whereClauses));

		//Sort Ordering
		final List<String> orderByClauses = new LinkedList<>();
		if (doFullTextSearch && query.getSortBy() == null) {
			// We're doing a full-text search without an explicit sort order,
			// so sort first on the search pseudo-rank.
			orderByClauses.addAll(Utils.addOrderBy(SortOrder.DESC, null, "search_rank"));
		}

		if (query.getSortBy() == null) { query.setSortBy(TagSearchSortBy.NAME); }
		if (query.getSortOrder() == null) { query.setSortOrder(SortOrder.ASC); }
		switch (query.getSortBy()) {
			case CREATED_AT:
				orderByClauses.addAll(Utils.addOrderBy(query.getSortOrder(), "tags", "createdAt"));
				break;
			case NAME:
			default:
				orderByClauses.addAll(Utils.addOrderBy(query.getSortOrder(), "tags", "name"));
				break;
		}
		orderByClauses.addAll(Utils.addOrderBy(SortOrder.ASC, "tags", "uuid"));
		sql.append(" ORDER BY ");
		sql.append(Joiner.on(", ").join(orderByClauses));

		final Query<Tag> sqlQuery = MssqlSearcher.addPagination(query, dbHandle, sql, sqlArgs)
			.map(new TagMapper());
		return TagList.fromQuery(sqlQuery, query.getPageNum(), query.getPageSize());
	}

}
