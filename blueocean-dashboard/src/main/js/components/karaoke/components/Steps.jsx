import React, { Component, PropTypes } from 'react';
import { QueuedState } from './QueuedState';
import { Step } from './Step';

export default class Steps extends Component {
    render() {
        const { t, nodeInformation } = this.props;
        const { model } = nodeInformation;
        return (<div className="Steps">
            { model.map((item) => <Step
                { ...
                    { ...this.props,
                        key: item.key,
                        step: item,
                    }
                }
            />) }
        </div>);
    }
}

Steps.propTypes = {
    nodeInformation: PropTypes.object.isRequired,
    t: PropTypes.func,
};

