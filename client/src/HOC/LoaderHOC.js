import PropTypes from 'prop-types'
import React, { Component } from 'react'
import { showLoading, hideLoading } from 'react-redux-loading-bar'
import './LoaderHOC.css'

export const mapDispatchToProps = (dispatch, ownProps) => ({
    showLoading: () => dispatch(showLoading()),
    hideLoading: () => dispatch(hideLoading()),
})

const LoaderHOC = (isLoading) => (dataPropName) => (WrappedComponent) => {
    return class LoaderHOC extends Component {

        static propTypes = {
            showLoading: PropTypes.func.isRequired,
            hideLoading: PropTypes.func.isRequired,
        }

        isEmpty(prop) {
            return (
                prop === null ||
                prop === undefined ||
                (prop.hasOwnProperty('length') && prop.length === 0) ||
                (prop.constructor === Object && Object.keys(prop).length === 0)
            )
        }

        isLoadingData(prop) {
            return (
                prop ||
                prop === undefined
            )
        }

        render() {
            const dataIsEmpty = this.isEmpty(this.props[dataPropName])
            const showLoader =  dataIsEmpty && this.isLoadingData(this.props[isLoading])

            if (showLoader) {
                if (typeof this.props.showLoading === 'function') {
                    this.props.showLoading()
                }
                return <div className='loader'></div>
            } else {
                if (typeof this.props.hideLoading === 'function') {
                    this.props.hideLoading()
                }
                if (dataIsEmpty) {
                    return <div></div>
                }
                else {
                    return <WrappedComponent {...this.props} />
                }
            }
        }
    }
}

export default LoaderHOC
