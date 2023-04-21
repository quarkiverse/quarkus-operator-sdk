import {LitElement, html, nothing} from 'lit';
import {JsonRpc} from 'jsonrpc';
import '@vaadin/details';
import '@vaadin/list-box';
import '@vaadin/item';
import '@vaadin/horizontal-layout';
import '@vaadin/vertical-layout';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/form-layout';
import '@vaadin/text-field';
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
          ${this._controllers.map(this.controller, this)}
        </vaadin-list-box>
      `
    }
  }

  controller(controller) {
    return html`
      <vaadin-item>
        <vaadin-details theme="filled">
          <vaadin-details-summary slot="summary">
            ${name(controller.name)}
            <vaadin-icon icon="vaadin:arrow-circle-right"></vaadin-icon>
            ${controller.className}
            ${resourceClass(controller.resourceClass)}
          </vaadin-details-summary>
          <vaadin-horizontal-layout theme="spacing padding">
            ${this.children(controller.dependents, "Dependents",
                this.dependent)}
            ${this.children(controller.eventSources, "Event Sources",
                this.eventSource)}
          </vaadin-horizontal-layout>
        </vaadin-details>
      </vaadin-item>`
  }

  eventSource(eventSource) {
    return html`
      ${name(eventSource.name)}
      ${resourceClass(eventSource.resourceClass)}
    `
  }

  dependent(dependent) {
    let defaultName = dependent.name === dependent.type;
    return html`
      <vaadin-details theme="filled">
        <vaadin-details-summary slot="summary">
          ${name(dependent.name)}
          ${defaultName ? '' : resourceClass(dependent.type)}
          ${eventSourceLink(dependent)}
        </vaadin-details-summary>
        <vaadin-vertical-layout>
          ${field(dependent.resourceClass, "Target resource")}
          ${dependsOn(dependent)}
          ${conditions(dependent)}
        </vaadin-vertical-layout>
      </vaadin-details>`
  }

  children(children, childrenName, childRenderer) {
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

function name(name) {
  return html`
    <qui-badge>${name}</qui-badge>`
}

function resourceClass(resourceClass) {
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

function conditions(dependent) {
  let hasConditions = dependent.hasConditions;
  if (hasConditions) {
    return html`
      <vaadin-details summary="Conditions">
        <vaadin-vertical-layout>
          ${field(dependent.readyCondition, "Ready")}
          ${field(dependent.reconcileCondition, "Reconcile")}
          ${field(dependent.deleteCondition, "Delete")}
        </vaadin-vertical-layout>
      </vaadin-details>
    `
  }
}

function dependsOn(dependent) {
   if (dependent.dependsOn && dependent.dependsOn.length > 0) {
     return html`
       <vaadin-details summary="Depends on">
         <vaading-list-box>
           ${dependent.dependsOn.map(
               dep => html`<vaadin-item>${name(dep)}</vaadin-item>`)}
         </vaading-list-box>
       </vaadin-details>
     `
   }
}

function eventSourceLink(dependent) {
  if (dependent.useEventSourceWithName) {
    return html`
      <vaadin-icon icon="vaadin:arrow-circle-right"></vaadin-icon>
    ${name(dependent.useEventSourceWithName)}`
  }
}

function field(value, label) {
  if (value) {
    return html`
      <vaadin-horizontal-layout>
        <span>${label}</span>: ${resourceClass(value)}
      </vaadin-horizontal-layout>`
  }
}

customElements.define('qwc-qosdk-controllers', QWCQOSDKControllers);