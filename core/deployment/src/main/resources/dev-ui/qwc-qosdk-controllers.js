import { LitElement, html, css, nothing} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/grid';
import '@vaadin/details';
import '@vaadin/list-box';
import '@vaadin/item';
import {columnBodyRenderer} from '@vaadin/grid/lit.js';
export class ControllersWebComponent extends LitElement {

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
    }

    render() {
        return html`
            <vaadin-grid .dataProvider="${this._controllersProvider}">
                <vaadin-grid-column path="name"></vaadin-grid-column>
                <vaadin-grid-column path="resourceClass"></vaadin-grid-column>
                <vaadin-grid-column path="effectiveNamespaces"></vaadin-grid-column>
                <vaadin-grid-column path="eventSources" auto-width ${columnBodyRenderer(this._eventSourcesRenderer, [])}></vaadin-grid-column>
                <vaadin-grid-column path="dependents" auto-width
                                    ${columnBodyRenderer(
                                        this._dependentsRenderer, [])}></vaadin-grid-column>
            </vaadin-grid>
            `;
    }

    _controllersProvider = (params, callback) => {
        let items = [];
        this.jsonRpc.getControllers().then(jsonRpcResponse => {
            console.log(jsonRpcResponse);
            callback(jsonRpcResponse.result, jsonRpcResponse.result.length);
        })
    }

    _eventSourcesRenderer = (controller) => {
       return html`
           <vaadin-list-box>
               ${controller.eventSources.map((eventSource) => html`
                   <vaadin-item>
                       ${eventSource.name}
                       ${eventSource.resourceClass ?? nothing}
                   </vaadin-item>
               `)}
           </vaadin-list-box>
       `
    };

    _dependentsRenderer = (controller) => {
        return html`
            <vaadin-list-box>
                ${controller.dependents.map((dependent) => html`
                   <vaadin-item>
                       ${dependent.name}
                       ${dependent.type}
                   </vaadin-item>
               `)}
            </vaadin-list-box>
        `
    };
    
}
customElements.define('qwc-qosdk-controllers', ControllersWebComponent);