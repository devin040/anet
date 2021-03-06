import PropTypes from 'prop-types'
import React, { Component } from 'react'
import {Button, Pagination} from 'react-bootstrap'
import autobind from 'autobind-decorator'

import ReportSummary from 'components/ReportSummary'
import ReportTable from 'components/ReportTable'
import ButtonToggleGroup from 'components/ButtonToggleGroup'
import {Location} from 'models'
import Leaflet from 'components/Leaflet'
import _get from 'lodash/get'

const FORMAT_SUMMARY = 'summary'
const FORMAT_TABLE = 'table'
const FORMAT_MAP = 'map'

const GQL_REPORT_FIELDS =  /* GraphQL */`
	id, intent, engagementDate, keyOutcomes, nextSteps, cancelledReason
	atmosphere, atmosphereDetails, state
	author { id, name, rank }
	primaryAdvisor { id, name, rank },
	primaryPrincipal { id, name, rank },
	advisorOrg { id, shortName },
	principalOrg { id, shortName },
	location { id, name, lat, lng },
	tasks { id, shortName },
	tags { id, name, description }
	approvalStatus {
		type, createdAt
		step { id , name
			approvers { id, name, person { id, name, rank } }
		},
		person { id, name, rank }
	}
`


export default class ReportCollection extends Component {
	static propTypes = {
		reports: PropTypes.array,
		paginatedReports: PropTypes.shape({
			totalCount: PropTypes.number,
			pageNum: PropTypes.number,
			pageSize: PropTypes.number,
			list: PropTypes.array.isRequired,
		}),
		goToPage: PropTypes.func,
	}

	constructor(props) {
		super(props)

		this.state = {
			viewFormat: 'summary',
		}
	}

	render() {
		var reports

		if (this.props.paginatedReports) {
			var {pageSize, pageNum, totalCount} = this.props.paginatedReports
			var numPages = (pageSize <= 0) ? 1 : Math.ceil(totalCount / pageSize)
			reports = this.props.paginatedReports.list
			pageNum++
		} else {
			reports = this.props.reports
		}

		let reportsExist = _get(reports, 'length', 0) > 0

		return <div className="report-collection">
			{reportsExist ?
				<div>
					<header>
						<ButtonToggleGroup value={this.state.viewFormat} onChange={this.changeViewFormat} className="hide-for-print">
							<Button value={FORMAT_SUMMARY}>Summary</Button>
							<Button value={FORMAT_TABLE}>Table</Button>
							<Button value={FORMAT_MAP}>Map</Button>
						</ButtonToggleGroup>

						{numPages > 1 &&
							<Pagination
								className="pull-right"
								prev
								next
								items={numPages}
								ellipsis
								maxButtons={6}
								activePage={pageNum}
								onSelect={(value) => {this.props.goToPage(value - 1)}}
							/>
						}

						{this.props.isSuperUser &&
							<div className="reports-filter">
								Filter: {this.renderToggleFilterButton(this.props)}
							</div>
						}
					</header>

					<div>
						{this.state.viewFormat === FORMAT_TABLE && this.renderTable(reports)}
						{this.state.viewFormat === FORMAT_SUMMARY && this.renderSummary(reports)}
						{this.state.viewFormat === FORMAT_MAP && this.renderMap(reports)}
					</div>

					<footer>
						{numPages > 1 &&
							<Pagination
								className="pull-right"
								prev
								next
								items={numPages}
								ellipsis
								maxButtons={6}
								activePage={pageNum}
								onSelect={(value) => {this.props.goToPage(value - 1)}}
							/>
						}
					</footer>
				</div>
				:
				<em>No reports found</em>
			}
		</div>
	}

	renderToggleFilterButton(props) {
		let showAll = 'Show all reports'
		let showPendingApproval = 'Show pending approval'
		let buttonText = (props.filterIsSet) ? showAll: showPendingApproval
		let button = <Button value="toggle-filter" className="btn btn-sm" onClick={props.setReportsFilter}>{buttonText}</Button>
		return button
	}

	renderTable(reports) {
		return <ReportTable showAuthors={true} reports={reports} />
	}

	renderSummary(reports) {
		return <div>
			{reports.map(report =>
				<ReportSummary report={report} key={report.id} />
			)}
		</div>
	}

	renderMap(reports) {
		let markers = []
		reports.forEach(report => {
			if (Location.hasCoordinates(report.location)) {
				markers.push({id: report.id, lat: report.location.lat, lng: report.location.lng, name: report.intent})
			}
		})
		return <Leaflet markers={markers} />
	}

	@autobind
	changeViewFormat(value) {
		this.setState({viewFormat: value})
	}

}

ReportCollection.GQL_REPORT_FIELDS = GQL_REPORT_FIELDS
