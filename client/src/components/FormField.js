import PropTypes from 'prop-types'
import React, { Component } from 'react'
import utils from 'utils'
import deepEqual from 'deep-equal'
import autobind from 'autobind-decorator'
import {FormGroup, Col, ControlLabel, FormControl, InputGroup, HelpBlock} from 'react-bootstrap'

class FormFieldExtraCol extends Component {
	render() {
		return <Col sm={3} {...this.props} />
	}
}

export default class FormField extends Component {
	constructor(props, context) {
		super(props, context)
		this.state = {
			value: '',
			userHasTouchedField: props.validateBeforeUserTouches,
			defaultValidation: null,
			isValid: null,
			errorMessage: ''
		}
	}
	static contextTypes = {
		formFor: PropTypes.object,
		form: PropTypes.object,
	}

	static propTypes = {
		// Specifying an id prop on a FormField contained inside a Form with
		// a formFor prop will cause the FormField to be autobound to the formFor
		// value. That is to say, its value will be set to formForObject[idProp],
		// and when the FormField changes, formForObject[idProp] will automatically
		// be updated. The form will then have its own onChange fired to allow
		// you to update state or rerender.
		id: PropTypes.string.isRequired,
		label: PropTypes.string,

		// if you need to do additional formatting on the value returned by
		// formForObject[idProp], you can specify a getter function which
		// will be called with the value as its prop
		getter: PropTypes.func,

		// This will cause the FormField to be rendered as an InputGroup,
		// with the node specified by addon appended on the right of the group.
		addon: PropTypes.node,

		// If you pass children, we will try to autobind them to the id key
		// if any of the children have propTypes that include onChange
		children: PropTypes.node,

		humanName: PropTypes.string,
		required: PropTypes.bool,
		onError: PropTypes.func,
		onValid: PropTypes.func,
		validate: PropTypes.func,

		// If you don't pass children, we will automatically create a FormControl.
		// You can use componentClass to override its type (for example, for a select).
		componentClass: PropTypes.oneOfType([
			PropTypes.string,
			PropTypes.object,
			PropTypes.func,
		]),

		// If you don't want autobinding behavior, you can override them here
		value: PropTypes.oneOfType([
			PropTypes.string,
			PropTypes.object,
			PropTypes.array,
			PropTypes.bool,
		]),

		onChange: PropTypes.func,

		maxCharacters: PropTypes.number,
	}

	render() {
		let {
			id,
			className,
			display,
			label,
			icon,
			addon,
			children,
			postInputGroupChildren,
			canSubmitWithError,
			...childProps
		} = this.props

		childProps = Object.without(
			childProps,
			'getter', 'horizontal', 'onError', 'onValid', 'humanName', 'maxCharacters', 'validateBeforeUserTouches', 'validate'
		)
		if (canSubmitWithError) {
			childProps = Object.without(childProps, 'required')
		}

		let defaultValue = this.getDefaultValue(this.props, this.context)

		let state = this.state
		if (Array.isArray(defaultValue)) {
			state.value = Array.from(defaultValue)
		} else {
			state.value = defaultValue
		}

		const validationState = this.props.validationState ||
			(this.state.isValid === false || this.isMissingRequiredField(this.props)) ?
				(canSubmitWithError ? 'warning' : 'error')
				: null

		let horizontal = this.context.form && this.context.form.props.horizontal
		if (typeof this.props.horizontal !== 'undefined') {
			horizontal = this.props.horizontal
		}

		if (typeof label === 'undefined') {
			label = utils.sentenceCase(id)
		}

		// Remove an ExtraCol from children first so we can manually append it
		// as a column
		children = React.Children.toArray(children)
		let extra = children.find(child => child.type === FormFieldExtraCol)
		if (extra)
			children.splice(children.indexOf(extra), 1)

		// if type is static, render out a static value
		if (this.props.type === 'static' || (!this.props.type && !this.props.componentClass && this.context.form.props.static)) {
			children = <FormControl.Static componentClass={'div'} id={id} {...childProps}>{(children.length && children) || defaultValue}</FormControl.Static>

		// if children are provided, render those, but special case them to
		// automatically set value and children props
		} else if (!this.props.componentClass && children.length) {
			children = children.map(child => {
				let propTypes = child.type.propTypes

				// check to see if this is some kind of element where we
				// can register an onChange handler, otherwise skip it
				if (propTypes && !propTypes.onChange)
					return child

				let onChange = child.props.onChange || this.onChange
				return React.cloneElement(child, {value: defaultValue, onChange: onChange, onInput: onChange})
			})

		// otherwise render out a default FormControl input element
		} else {
			if (children.length)
				childProps.children = children

			const formControl = <FormControl
				{...childProps}
				value={defaultValue}
				onChange={this.onChange}
				onInput={this.onChange}
				onBlur={this.onUserTouchedField} />

			children = <div>
				{formControl}
				<HelpBlock className={validationState === 'error' || validationState === 'warning' ? '' : 'hidden'} >
					{this.state.errorMessage}
				</HelpBlock>
			</div>
		}

		if (icon) {
			icon = <img src={icon} height={24} alt="" />
		}

		// if there's an addon we need to use an InputGroup
		if (addon) {
			// allows passing a url for an image
			if (addon.indexOf('.') !== -1) {
				addon = <img src={addon} height={20} alt="" />
			}

			children = <div>
				<InputGroup>
					{children}
					<InputGroup.Addon onClick={this.focus}>{addon}</InputGroup.Addon>
				</InputGroup>
				{postInputGroupChildren}
			</div>
		}

		const inline = display && display === 'inline'
		const hidden = inline ? 'sr-only' : ''

		return (
			<FormGroup controlId={id} className={className} validationState={validationState}>
				{horizontal && !hidden
					? <Col sm={2} className={hidden} componentClass={ControlLabel}>{label} {icon}</Col>
					: <ControlLabel className={hidden}>{label} {icon}</ControlLabel> }
				{horizontal && !hidden
					? <Col sm={7}>{children}</Col>
					: children }
				{extra}
			</FormGroup>
		)
	}

	@autobind
	isMissingRequiredField(props) {
		return props.required && !this.state.value && this.state.userHasTouchedField
	}

	shouldComponentUpdate(newProps, newState, newContext) {
		if (this.state.userHasTouchedField !== newState.userHasTouchedField) {
			return true
		}

		let newValue = this.getDefaultValue(newProps, newContext)
		let oldValue = this.state.value

		if (newValue !== oldValue) {
			return true
		}

		if (Array.isArray(newValue)) {
			return !deepEqual(newValue, oldValue)
		}

		if (!deepEqual(this.props, newProps)) {
			return true
		}

		return false
	}

	getValue(props, context) {
		let formContext = context.formFor
		let id = props.id
		let getter = props.getter
		if (formContext) {
			let value = formContext[id]
			return getter ? getter(value) : value
		}
	}

	getDefaultValue(props, context) {
		return props.value || this.getValue(props, context) || ''
	}

	componentWillReceiveProps(nextProps) {
		if (nextProps.validateBeforeUserTouches) {
			this.setState({userHasTouchedField: true})
		}

		if (nextProps.required !== this.props.required) {
			this.updateValidationState(nextProps)
		}
	}

	setStateDefaultInvalidField(props) {
		this.setState({
			isValid: false,
			errorMessage: `${props.humanName} is required`
		})
	}

	setStateCustomValidationField(props) {
		if (this.state.value.length === 0) return

		let customValidation = props.validate(this.state.value)
		if (customValidation.isValid !== null) {
			this.setState({
				isValid: customValidation.isValid,
				errorMessage: customValidation.message
			})
		} else if (this.state.defaultValidation) {
			this.setState({ isValid: null, errorMessage: ''})
		} else {
			this.setStateDefaultInvalidField(props)
		}
	}

	setValidationState(props) {
		if (this.isMissingRequiredField(props) || this.state.defaultValidation === false){
			this.setStateDefaultInvalidField(props)
		}
		if (props.validate) {
			this.setStateCustomValidationField(props)
		}
	}

	@autobind
	updateValidationState(props) {
		if (!this.state.userHasTouchedField) {
			if ((!this.isMissingRequiredField(props) && this.state.value.length === 0)) {
				this.setState({ isValid: null, errorMessage: ''})
				return
			}
			if (!this.isMissingRequiredField(props) && this.state.defaultValidation === true) {
				this.setValidationState(props)
				return
			}
		}

		this.setValidationState(props)
		if (this.isMissingRequiredField(props) || this.state.isValid === false) {
			props.onError()
		} else if (props.onValid && !this.isMissingRequiredField(props) && this.state.isValid !== false) {
			props.onValid()
		}
		this.setState( { userHasTouchedField: false })
	}

	@autobind
	onUserTouchedField(event) {
		if ( !(event && event.target)) return null
		let defaultValidation = event.target.checkValidity()
		let id = this.props.id
		let value = this.sanitizeInput(this.getEventValue(event))
		this.setFormContextWith(id, value)
		this.setState({
			defaultValidation: defaultValidation,
			isValid: defaultValidation,
			errorMessage: `${this.props.humanName} is required`,
			userHasTouchedField: true,
		}, () => this.updateValidationState(this.props))
	}

	@autobind
	onChange(event) {
		const id = this.props.id
		const value = this.getEventValue(event)
		this.setFormContextWith(id, value)
		if (this.props.maxCharacters && value.length > this.props.maxCharacters) {
			return
		}

		if (this.props.onChange) {
			this.props.onChange(event)
			return
		}

		let form = this.context.form
		if (form && form.props.onChange) {
			form.props.onChange(event)
			event && event.stopPropagation && event.stopPropagation()
		} else {
			this.forceUpdate()
		}
	}

	@autobind
	focus() {
		let element = document.getElementById(this.props.id)
		if (element && element.focus) {
			element.focus()
		}
	}

	getEventValue(event) {
		return event && event.target ? event.target.value : event
	}

	setFormContextWith(id, value) {
		let formContext = this.context.formFor
		if (formContext)
			formContext[id] = value
		return formContext
	}

	sanitizeInput(value) {
		if (typeof value === 'string' || value instanceof String) {
			return value.trim()
		}
		return value
	}
}

FormField.ExtraCol = FormFieldExtraCol
