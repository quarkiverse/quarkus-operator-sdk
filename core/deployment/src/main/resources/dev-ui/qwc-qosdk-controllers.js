import {LitElement, html, nothing} from 'lit';
import {JsonRpc} from 'jsonrpc';
import '@vaadin/details';
import '@vaadin/list-box';
import '@vaadin/item';
import '@vaadin/horizontal-layout';
import 'qui-badge';

export class QWCQOSDKControllers extends LitElement {

  jsonRpc = new JsonRpc(this);

  constructor() {
    super();
  }

  connectedCallback() {
    super.connectedCallback();
    this.jsonRpc.getControllers().then(
        jsonRpcResponse => this._controllers = jsonRpcResponse.result);
  }

  static properties = {
    _controllers: {state: true},
  }

  render() {
    if (this._controllers) {
      return html`
        <vaadin-list-box>
          ${this._controllers.map(this._renderController, this)}
        </vaadin-list-box>
      `
    }
  }

  _renderController(controller) {
    return html`
      <vaadin-item>
        ${resourceClassRenderer(controller.resourceClass)}
        <vaadin-details theme="filled" summary="${controller.name}">
          <vaadin-horizontal-layout theme="spacing padding">
            ${this.childrenRenderer(controller.dependents, "Dependents",
                this.dependentRenderer)}
            ${this.childrenRenderer(controller.eventSources, "Event Sources",
                this.eventSourceRenderer)}
          </vaadin-horizontal-layout>
        </vaadin-details>
      </vaadin-item>`
  }

  eventSourceRenderer(eventSource) {
    return html`
      <qui-badge>${eventSource.name}</qui-badge>
      ${resourceClassRenderer(eventSource.resourceClass)}
    `
  }

  dependentRenderer(dependent) {
    let defaultName = dependent.name === dependent.type;
    return html`
      <qui-badge>${dependent.name}</qui-badge>
      ${defaultName ? '' : resourceClassRenderer(dependent.type)}`
  }

  childrenRenderer = (children, childrenName, childRenderer) => {
    if (children) {
      let count = children.length;
      return html`
        <vaadin-details theme="filled" summary="${count} ${childrenName}">
          <vaadin-list-box>
            ${children.map((child) => html`
              <vaadin-item>
                ${childRenderer(child)}
              </vaadin-item>
            `)}
          </vaadin-list-box>
        </vaadin-details>
      `
    }
  }

}

function resourceClassRenderer(resourceClass) {
  if (resourceClass) {
    const fabric8Prefix = 'io.fabric8.kubernetes.api.model.';
    let level = 'success';
    if (resourceClass.startsWith(fabric8Prefix)) {
      level = 'warning';
      resourceClass = resourceClass.substring(fabric8Prefix.length);
    }
    return html`
      <qui-badge level="${level}" small>${resourceClass}</qui-badge>`
  }
}

customElements.define('qwc-qosdk-controllers', QWCQOSDKControllers);